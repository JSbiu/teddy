package com.dbay.teddy.service;

import com.dbay.teddy.entity.App;
import com.sun.net.httpserver.HttpServer;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public class YarnServiceTest {

    private HttpServer server;
    private CloseableHttpClient httpClient;
    private String endpoint;

    @Before
    public void setUp() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/ws/v1/cluster/apps/application_1_2", exchange -> {
            byte[] body = ("{\"app\":{\"id\":\"application_1_2\","
                    + "\"state\":\"RUNNING\",\"finalStatus\":\"UNDEFINED\","
                    + "\"startedTime\":1000,\"finishedTime\":0}}")
                    .getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream output = exchange.getResponseBody()) {
                output.write(body);
            }
        });
        server.start();
        endpoint = "http://127.0.0.1:" + server.getAddress().getPort();

        RequestConfig requestConfig = RequestConfig.custom()
                .setConnectTimeout(200)
                .setSocketTimeout(1000)
                .setConnectionRequestTimeout(200)
                .build();
        httpClient = HttpClients.custom()
                .setDefaultRequestConfig(requestConfig)
                .disableAutomaticRetries()
                .build();
    }

    @After
    public void tearDown() throws IOException {
        if (server != null) {
            server.stop(0);
        }
        if (httpClient != null) {
            httpClient.close();
        }
    }

    @Test
    public void fallsBackToTheNextResourceManagerAndReturnsOneSnapshot() {
        YarnService service = new YarnService(
                httpClient, Arrays.asList("127.0.0.1:1", endpoint));

        App app = service.app("application_1_2");

        assertEquals("application_1_2", app.getId());
        assertEquals("RUNNING", app.getState());
    }

    @Test
    public void rejectsInvalidApplicationIdsBeforeMakingARequest() {
        YarnService service = new YarnService(httpClient, Collections.singletonList(endpoint));

        assertRejected(() -> service.app("../cluster"));
    }

    @Test(expected = YarnService.YarnQueryException.class)
    public void failsClosedWhenNoResourceManagerHasTheApplication() {
        YarnService service = new YarnService(
                httpClient, Collections.singletonList("127.0.0.1:1"));

        service.app("application_9_9");
    }

    private void assertRejected(Runnable action) {
        try {
            action.run();
            fail("Expected the application id to be rejected");
        } catch (IllegalArgumentException expected) {
        }
    }
}
