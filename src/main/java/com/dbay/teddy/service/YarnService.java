package com.dbay.teddy.service;

import com.alibaba.fastjson.JSON;
import com.dbay.teddy.entity.App;
import com.dbay.teddy.utils.TeddyConf;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

/**
 * @author AlexanderGuo
 */
@Service
public class YarnService {

    private static final Pattern APPLICATION_ID =
            Pattern.compile("application_[0-9]+_[0-9]+");

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final CloseableHttpClient httpClient;
    private final List<String> resourceManagers;

    public YarnService() {
        this(createHttpClient(
                        configuredTimeout("yarn.http.connect-timeout-ms", 3000, 100, 60000),
                        configuredTimeout("yarn.http.read-timeout-ms", 5000, 100, 120000)),
                parseResourceManagers(TeddyConf.get("yarn.cluster")));
    }

    YarnService(CloseableHttpClient httpClient, List<String> resourceManagers) {
        this.httpClient = httpClient;
        this.resourceManagers = Collections.unmodifiableList(new ArrayList<>(resourceManagers));
        if (this.resourceManagers.isEmpty()) {
            throw new IllegalArgumentException("yarn.cluster must contain at least one endpoint");
        }
    }

    public String state(String appId) {
        return app(appId).getState();
    }

    /**
     * Gets one consistent snapshot of an application. When every ResourceManager
     * is unavailable, the caller receives an exception and must keep the last
     * persisted state instead of turning a network problem into a job failure.
     */
    public App app(String appId) {
        validateApplicationId(appId);
        List<String> failures = new ArrayList<>();

        for (String resourceManager : resourceManagers) {
            String url = applicationUrl(resourceManager, appId);
            HttpGet request = new HttpGet(url);
            request.setHeader("Accept", "application/json");

            try (CloseableHttpResponse response = httpClient.execute(request)) {
                int statusCode = response.getStatusLine().getStatusCode();
                if (statusCode != 200) {
                    failures.add(resourceManager + " returned HTTP " + statusCode);
                    continue;
                }

                String content = EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8);
                App snapshot = JSON.parseObject(content).getObject("app", App.class);
                if (snapshot == null || snapshot.getState() == null) {
                    failures.add(resourceManager + " returned an invalid application payload");
                    continue;
                }
                return snapshot;
            } catch (IOException | RuntimeException e) {
                failures.add(resourceManager + " failed: " + safeMessage(e));
                logger.warn("YARN application query failed for {} via {}: {}",
                        appId, resourceManager, safeMessage(e));
            }
        }

        throw new YarnQueryException("No ResourceManager returned " + appId + ": "
                + String.join("; ", failures));
    }

    private static CloseableHttpClient createHttpClient(int connectTimeout, int readTimeout) {
        RequestConfig requestConfig = RequestConfig.custom()
                .setConnectTimeout(connectTimeout)
                .setConnectionRequestTimeout(connectTimeout)
                .setSocketTimeout(readTimeout)
                .build();
        return HttpClients.custom()
                .setDefaultRequestConfig(requestConfig)
                .disableAutomaticRetries()
                .build();
    }

    private static int configuredTimeout(String key, int defaultValue, int minimum, int maximum) {
        String configured = TeddyConf.get(key, String.valueOf(defaultValue));
        try {
            int value = Integer.parseInt(configured);
            if (value < minimum || value > maximum) {
                throw new IllegalStateException(key + " must be between "
                        + minimum + " and " + maximum);
            }
            return value;
        } catch (NumberFormatException e) {
            throw new IllegalStateException(key + " must be an integer", e);
        }
    }

    private static List<String> parseResourceManagers(String configured) {
        if (configured == null) {
            return Collections.emptyList();
        }
        List<String> endpoints = new ArrayList<>();
        for (String value : configured.split(",")) {
            String endpoint = value.trim();
            if (!endpoint.isEmpty()) {
                endpoints.add(endpoint);
            }
        }
        return endpoints;
    }

    private static String applicationUrl(String resourceManager, String appId) {
        String baseUrl = resourceManager.matches("(?i)^https?://.*")
                ? resourceManager
                : "http://" + resourceManager;
        while (baseUrl.endsWith("/")) {
            baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
        }
        return baseUrl + "/ws/v1/cluster/apps/" + appId;
    }

    private static void validateApplicationId(String appId) {
        if (appId == null || !APPLICATION_ID.matcher(appId).matches()) {
            throw new IllegalArgumentException("Invalid YARN application id");
        }
    }

    private static String safeMessage(Exception e) {
        return e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
    }

    public static class YarnQueryException extends RuntimeException {
        public YarnQueryException(String message) {
            super(message);
        }
    }
}
