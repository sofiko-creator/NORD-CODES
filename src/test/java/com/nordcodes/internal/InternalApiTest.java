package com.nordcodes.internal;

import com.github.tomakehurst.wiremock.client.WireMock;
import com.nordcodes.internal.config.Props;
import com.nordcodes.internal.config.WireMockConfig;
import io.qameta.allure.*;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
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

    @Test
    @Order(1)
    @Story("LOGIN: успешная аутентификация")
    @Severity(SeverityLevel.CRITICAL)
    @Description("LOGIN триггерит POST /auth на внешний сервис; при 200 токен сохраняется")
    void loginSuccess() {
        String token = randomToken();

        stubFor(post(urlEqualTo("/auth"))
                .willReturn(aResponse().withStatus(200)));

        RestAssured.given()
                .contentType(ContentType.URLENC)
                .accept(ContentType.JSON)
                .header("X-Api-Key", apiKey)
                .formParam("token", token)
                .formParam("action", "LOGIN")
                .when()
                .post("/endpoint")
                .then()
                .statusCode(200)
                .body("result", equalTo("OK"));

        verify(postRequestedFor(urlEqualTo("/auth"))
                .withHeader("Content-Type", containing("application/x-www-form-urlencoded"))
                .withHeader("Accept", containing("application/json"))
                .withRequestBody(containing("token=" + token)));
    }

    @Test
    @Order(2)
    @Story("LOGIN: повторная аутентификация тем же токеном")
    @Severity(SeverityLevel.NORMAL)
    @Description("Повторный LOGIN с уже сохранённым токеном возвращает 409")
    void loginTwiceSameToken() {
        String token = randomToken();

        stubFor(post(urlEqualTo("/auth"))
                .willReturn(aResponse().withStatus(200)));

        RestAssured.given()
                .contentType(ContentType.URLENC)
                .header("X-Api-Key", apiKey)
                .formParam("token", token)
                .formParam("action", "LOGIN")
                .when()
                .post("/endpoint")
                .then()
                .statusCode(200)
                .body("result", equalTo("OK"));

        RestAssured.given()
                .contentType(ContentType.URLENC)
                .header("X-Api-Key", apiKey)
                .formParam("token", token)
                .formParam("action", "LOGIN")
                .when()
                .post("/endpoint")
                .then()
                .statusCode(409)
                .body("result", equalTo("ERROR"));
    }

    @Test
    @Order(3)
    @Story("ACTION: действие после успешного LOGIN")
    @Severity(SeverityLevel.CRITICAL)
    @Description("ACTION триггерит POST /doAction; доступно только для токена, прошедшего LOGIN")
    void actionAfterLogin() {
        String token = randomToken();

        stubFor(post(urlEqualTo("/auth"))
                .willReturn(aResponse().withStatus(200)));
        stubFor(post(urlEqualTo("/doAction"))
                .willReturn(aResponse().withStatus(200)));

        RestAssured.given()
                .contentType(ContentType.URLENC)
                .header("X-Api-Key", apiKey)
                .formParam("token", token)
                .formParam("action", "LOGIN")
                .when()
                .post("/endpoint")
                .then()
                .statusCode(200)
                .body("result", equalTo("OK"));

        RestAssured.given()
                .contentType(ContentType.URLENC)
                .header("X-Api-Key", apiKey)
                .formParam("token", token)
                .formParam("action", "ACTION")
                .when()
                .post("/endpoint")
                .then()
                .statusCode(200)
                .body("result", equalTo("OK"));

        verify(postRequestedFor(urlEqualTo("/doAction"))
                .withHeader("Content-Type", containing("application/x-www-form-urlencoded"))
                .withHeader("Accept", containing("application/json"))
                .withRequestBody(containing("token=" + token)));
    }

    @Test
    @Order(4)
    @Story("ACTION без LOGIN должен вернуть ERROR")
    @Severity(SeverityLevel.NORMAL)
    void actionWithoutLogin() {
        String token = randomToken();

        RestAssured.given()
                .contentType(ContentType.URLENC)
                .header("X-Api-Key", apiKey)
                .formParam("token", token)
                .formParam("action", "ACTION")
                .when()
                .post("/endpoint")
                .then()
                .statusCode(403)
                .body("result", equalTo("ERROR"))
                .body("message", containsString("not found"));

        verify(0, postRequestedFor(urlEqualTo("/doAction")));
    }

    @Test
    @Order(5)
    @Story("LOGOUT удаляет токен из хранилища")
    @Severity(SeverityLevel.NORMAL)
    void logoutRemovesToken() {
        String token = randomToken();

        stubFor(post(urlEqualTo("/auth"))
                .willReturn(aResponse().withStatus(200)));

        RestAssured.given()
                .contentType(ContentType.URLENC)
                .header("X-Api-Key", apiKey)
                .formParam("token", token)
                .formParam("action", "LOGIN")
                .when()
                .post("/endpoint")
                .then()
                .statusCode(200)
                .body("result", equalTo("OK"));

        RestAssured.given()
                .contentType(ContentType.URLENC)
                .header("X-Api-Key", apiKey)
                .formParam("token", token)
                .formParam("action", "LOGOUT")
                .when()
                .post("/endpoint")
                .then()
                .statusCode(200)
                .body("result", equalTo("OK"));

        RestAssured.given()
                .contentType(ContentType.URLENC)
                .header("X-Api-Key", apiKey)
                .formParam("token", token)
                .formParam("action", "ACTION")
                .when()
                .post("/endpoint")
                .then()
                .statusCode(403)
                .body("result", equalTo("ERROR"));
    }

    @Test
    @Order(6)
    @Story("Без X-Api-Key доступ запрещён")
    @Severity(SeverityLevel.BLOCKER)
    void missingApiKey() {
        String token = randomToken();

        RestAssured.given()
                .contentType(ContentType.URLENC)
                .formParam("token", token)
                .formParam("action", "LOGIN")
                .when()
                .post("/endpoint")
                .then()
                .statusCode(401);
    }

    @Test
    @Order(7)
    @Story("С неверным X-Api-Key доступ запрещён")
    @Severity(SeverityLevel.BLOCKER)
    void wrongApiKey() {
        String token = randomToken();

        RestAssured.given()
                .contentType(ContentType.URLENC)
                .header("X-Api-Key", "WRONG")
                .formParam("token", token)
                .formParam("action", "LOGIN")
                .when()
                .post("/endpoint")
                .then()
                .statusCode(401);
    }
}