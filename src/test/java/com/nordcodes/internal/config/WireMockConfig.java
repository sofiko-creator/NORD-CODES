package com.nordcodes.internal.config;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import org.junit.jupiter.api.extension.*;

import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;

public class WireMockConfig implements BeforeAllCallback, AfterAllCallback, BeforeEachCallback {

    private WireMockServer wireMockServer;

    @Override
    public void beforeAll(ExtensionContext context) {
        String host = Props.get("host", "localhost");
        int port = Props.getInt("port", 8888);

        wireMockServer = new WireMockServer(options().port(port));
        wireMockServer.start();
        WireMock.configureFor(host, port);
    }

    @Override
    public void beforeEach(ExtensionContext context) {
        WireMock.resetAllRequests();
    }

    @Override
    public void afterAll(ExtensionContext context) {
        if (wireMockServer != null) wireMockServer.stop();
    }
}