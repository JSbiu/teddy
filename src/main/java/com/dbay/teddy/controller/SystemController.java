package com.dbay.teddy.controller;

import com.dbay.teddy.entity.NotifyConfig;
import com.dbay.teddy.service.JobService;
import com.dbay.teddy.service.NotifyConfigService;
import com.dbay.teddy.utils.Response;
import com.dbay.teddy.utils.TeddyConf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestBody;
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
    private final NotifyConfigService notifyConfigService;

    @Autowired
    public SystemController(JobService jobService, NotifyConfigService notifyConfigService) {
        this.jobService = jobService;
        this.notifyConfigService = notifyConfigService;
    }

    @RequestMapping(value = "client-config", method = RequestMethod.GET)
    public Response clientConfig() {
        String yarnProxyBaseUrl = TeddyConf.get("yarn.proxy.base-url", "").trim();
        while (yarnProxyBaseUrl.endsWith("/")) {
            yarnProxyBaseUrl = yarnProxyBaseUrl.substring(0, yarnProxyBaseUrl.length() - 1);
        }
        return Response.SUCCESS(Collections.singletonMap("yarnProxyBaseUrl", yarnProxyBaseUrl));
    }

    @RequestMapping(value = "notify-config/list", method = RequestMethod.GET)
    public Response notifyConfigList() {
        return Response.SUCCESS(notifyConfigService.list());
    }

    @RequestMapping(value = "notify-config/save", method = RequestMethod.POST)
    public Response notifyConfigSave(@RequestBody NotifyConfig config) {
        try {
            notifyConfigService.save(config);
            return Response.SUCCESS("保存成功");
        } catch (IllegalArgumentException e) {
            return Response.ERROR(message(e, "保存失败"));
        }
    }

    @RequestMapping(value = "notify-config/delete", method = RequestMethod.POST)
    public Response notifyConfigDelete(Integer id) {
        try {
            notifyConfigService.delete(id);
            return Response.SUCCESS("已删除");
        } catch (IllegalArgumentException e) {
            return Response.ERROR(message(e, "删除失败"));
        }
    }

    /**
     * 供任务配置页的"填入默认通知"按钮使用。
     */
    @RequestMapping(value = "notify-config/default", method = RequestMethod.GET)
    public Response notifyConfigDefault() {
        NotifyConfig config = notifyConfigService.findDefault();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("configured", config != null);
        data.put("webhook", config == null ? "" : config.getWebhook());
        return Response.SUCCESS(data);
    }

    private static String message(IllegalArgumentException e, String fallback) {
        return e.getMessage() == null ? fallback : e.getMessage();
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
