package com.dbay.teddy.controller;

import com.dbay.teddy.service.JobService;
import com.dbay.teddy.utils.Response;
import com.dbay.teddy.utils.TeddyConf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Exposes non-sensitive settings needed by the static web client.
 */
@RestController
@RequestMapping("system")
public class SystemController {

    private final JobService jobService;

    @Autowired
    public SystemController(JobService jobService) {
        this.jobService = jobService;
    }

    @RequestMapping(value = "client-config", method = RequestMethod.GET)
    public Response clientConfig() {
        String yarnProxyBaseUrl = TeddyConf.get("yarn.proxy.base-url", "").trim();
        while (yarnProxyBaseUrl.endsWith("/")) {
            yarnProxyBaseUrl = yarnProxyBaseUrl.substring(0, yarnProxyBaseUrl.length() - 1);
        }
        return Response.SUCCESS(Collections.singletonMap("yarnProxyBaseUrl", yarnProxyBaseUrl));
    }

    @RequestMapping(value = "health", method = RequestMethod.GET)
    public ResponseEntity<Map<String, Object>> health() {
        Integer jobCount = jobService.count();
        Map<String, Object> health = new LinkedHashMap<>();
        if (jobCount == null || jobCount < 0) {
            health.put("status", "DOWN");
            health.put("database", "DOWN");
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(health);
        }

        String version = SystemController.class.getPackage().getImplementationVersion();
        health.put("status", "UP");
        health.put("database", "UP");
        health.put("version", version == null ? "development" : version);
        return ResponseEntity.ok(health);
    }
}
