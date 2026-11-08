package com.nordcodes.internal;

import com.github.tomakehurst.wiremock.client.WireMock;
import com.nordcodes.internal.config.Props;
import com.nordcodes.internal.config.WireMockConfig;
import io.qameta.allure.*;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;

import java.security.SecureRandom;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;

@Epic("Internal Web Service")
@Feature("API Testing")
@ExtendWith(WireMockConfig.class)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class InternalApiTest {

    private static String apiKey;

    private static final String CHARS = "0123456789ABCDEF";
    private static final SecureRandom RND = new SecureRandom();

    private static String randomToken() {
        StringBuilder sb = new StringBuilder(32);
        for (int i = 0; i < 32; i++) sb.append(CHARS.charAt(RND.nextInt(CHARS.length())));
        return sb.toString();
    }

    @BeforeAll
    static void setUp() {
        apiKey = Props.get("api-key", "default_secret");

        RestAssured.baseURI = "http://localhost";
        RestAssured.port = Props.getInt("app-port", 8080);
        RestAssured.basePath = "";
        RestAssured.enableLoggingOfRequestAndResponseIfValidationFails();

        WireMock.configureFor(
                Props.get("host", "localhost"),
                Props.getInt("port", 8888));
    }

    @BeforeEach
    void resetRequests() {
        resetAllRequests();
    }

    private Response sendAction(String token, String action) {
        Allure.step("Отправка запроса с действием: " + action);
        return RestAssured.given()
                .contentType(ContentType.URLENC)
                .accept(ContentType.JSON)
                .header("X-Api-Key", apiKey)
                .formParam("token", token)
                .formParam("action", action)
                .when()
                .post("/endpoint");

    }

    private void verifyExternalCall(String path, String token) {
        Allure.step("Проверка, что внешний сервис вызван: " + path, () ->
                verify(postRequestedFor(urlEqualTo(path))
                .withHeader("Content-Type", containing("application/x-www-form-urlencoded"))
                .withHeader("Accept", containing("application/json"))
                .withRequestBody(containing("token=" + token))));
    }

    private void verifyExternalNotCalled() {
        Allure.step("Проверка, что внешний сервис НЕ вызывался: /doAction", () ->
                verify(0, postRequestedFor(urlEqualTo("/doAction"))));
    }

    @Test
    @Order(1)
    @Story("LOGIN: успешная аутентификация")
    @Severity(SeverityLevel.CRITICAL)
    @Description("LOGIN триггерит POST /auth на внешний сервис; при 200 токен сохраняется")
    void loginSuccess() {
        String token = randomToken();

        Allure.step("Настройка мока: /auth возвращает 200", () -> {
            stubFor(post(urlEqualTo("/auth")).willReturn(aResponse().withStatus(200)));
        });

        Response response = sendAction(token, "LOGIN");

        Allure.step("Проверка ответа приложения: 200 и result=OK", () -> {
            response.then()
                    .statusCode(200)
                    .body("result", equalTo("OK"));
        });

        verifyExternalCall("/auth", token);
    }

    @Test
    @Order(2)
    @Story("LOGIN: повторная аутентификация тем же токеном")
    @Severity(SeverityLevel.NORMAL)
    @Description("Повторный LOGIN с уже сохранённым токеном возвращает 409")
    void loginTwiceSameToken() {
        String token = randomToken();

        Allure.step("Настройка мока: /auth возвращает 200", () -> {
            stubFor(post(urlEqualTo("/auth")).willReturn(aResponse().withStatus(200)));
        });

        Response firstLogin = sendAction(token, "LOGIN");
        Allure.step("Первая аутентификация: ожидаем 200 OK", () -> {
            firstLogin.then().statusCode(200).body("result", equalTo("OK"));
        });

        Response secondLogin = sendAction(token, "LOGIN");
        Allure.step("Повторная аутентификация: ожидаем 409 Conflict", () -> {
            secondLogin.then().statusCode(409).body("result", equalTo("ERROR"));
        });
    }

    @Test
    @Order(3)
    @Story("ACTION: действие после успешного LOGIN")
    @Severity(SeverityLevel.CRITICAL)
    @Description("ACTION триггерит POST /doAction; доступно только для токена, прошедшего LOGIN")
    void actionAfterLogin() {
        String token = randomToken();

        Allure.step("Настройка моков: /auth и /doAction возвращают 200", () -> {
            stubFor(post(urlEqualTo("/auth")).willReturn(aResponse().withStatus(200)));
            stubFor(post(urlEqualTo("/doAction")).willReturn(aResponse().withStatus(200)));
        });

        Response login = sendAction(token, "LOGIN");
        Allure.step("Аутентификация токена: ожидаем 200 OK", () -> {
            login.then().statusCode(200).body("result", equalTo("OK"));
        });

        Response action = sendAction(token, "ACTION");
        Allure.step("Выполнение действия: ожидаем 200 OK", () -> {
            action.then().statusCode(200).body("result", equalTo("OK"));
        });

        verifyExternalCall("/doAction", token);
    }

    @Test
    @Order(4)
    @Story("ACTION без LOGIN должен вернуть ERROR")
    @Severity(SeverityLevel.NORMAL)
    void actionWithoutLogin() {
        String token = randomToken();

        Response response = sendAction(token, "ACTION");

        Allure.step("Проверка ответа: ожидаем 403 ERROR с сообщением 'not found'", () -> {
            response.then()
                    .statusCode(403)
                    .body("result", equalTo("ERROR"))
                    .body("message", containsString("not found"));
        });

        verifyExternalNotCalled();
    }

    @Test
    @Order(5)
    @Story("LOGOUT удаляет токен из хранилища")
    @Severity(SeverityLevel.NORMAL)
    void logoutRemovesToken() {
        String token = randomToken();

        Allure.step("Настройка мока: /auth возвращает 200", () -> {
            stubFor(post(urlEqualTo("/auth")).willReturn(aResponse().withStatus(200)));
        });

        Response login = sendAction(token, "LOGIN");
        Allure.step("Аутентификация токена: ожидаем 200 OK", () -> {
            login.then().statusCode(200).body("result", equalTo("OK"));
        });

        Response logout = sendAction(token, "LOGOUT");
        Allure.step("Выход из сессии: ожидаем 200 OK", () -> {
            logout.then().statusCode(200).body("result", equalTo("OK"));
        });

        Response actionAfterLogout = sendAction(token, "ACTION");
        Allure.step("Попытка действия после LOGOUT: ожидаем 403 ERROR", () -> {
            actionAfterLogout.then().statusCode(403).body("result", equalTo("ERROR"));
        });
    }

    @Test
    @Order(6)
    @Story("Без X-Api-Key доступ запрещён")
    @Severity(SeverityLevel.BLOCKER)
    void missingApiKey() {
        String token = randomToken();

        Response response = Allure.step("Отправка запроса без заголовка X-Api-Key", () ->
                RestAssured.given()
                        .contentType(ContentType.URLENC)
                        .formParam("token", token)
                        .formParam("action", "LOGIN")
                        .when()
                        .post("/endpoint")
        );

        Allure.step("Проверка ответа: ожидаем 401 Unauthorized", () -> {
            response.then().statusCode(401);
        });
    }

    @Test
    @Order(7)
    @Story("С неверным X-Api-Key доступ запрещён")
    @Severity(SeverityLevel.BLOCKER)
    void wrongApiKey() {
        String token = randomToken();

        Response response = Allure.step("Отправка запроса с неверным X-Api-Key", () ->
                RestAssured.given()
                        .contentType(ContentType.URLENC)
                        .header("X-Api-Key", "WRONG")
                        .formParam("token", token)
                        .formParam("action", "LOGIN")
                        .when()
                        .post("/endpoint")
        );

        Allure.step("Проверка ответа: ожидаем 401 Unauthorized", () -> {
            response.then().statusCode(401);
        });
    }
}