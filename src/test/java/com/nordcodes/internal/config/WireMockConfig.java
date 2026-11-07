package com.nordcodes.internal.config;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;

public class WireMockConfig implements BeforeAllCallback, AfterAllCallback {

    private WireMockServer wireMockServer;
    private String host;
    private int port;

    @Override
    public void beforeAll(ExtensionContext context) {
        host = Props.get("host", "localhost");
        port = Props.getInt("port", 8888);

        wireMockServer = new WireMockServer(options().port(port));
        wireMockServer.start();
        WireMock.configureFor(host, port);
    }

    @Override
    public void afterAll(ExtensionContext context) {
        if (wireMockServer != null) wireMockServer.stop();
    }
}