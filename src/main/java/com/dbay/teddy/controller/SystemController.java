package com.dbay.teddy.controller;

import com.dbay.teddy.utils.Response;
import com.dbay.teddy.utils.TeddyConf;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collections;

/**
 * Exposes non-sensitive settings needed by the static web client.
 */
@RestController
@RequestMapping("system")
public class SystemController {

    @RequestMapping(value = "client-config", method = RequestMethod.GET)
    public Response clientConfig() {
        String yarnProxyBaseUrl = TeddyConf.get("yarn.proxy.base-url", "").trim();
        while (yarnProxyBaseUrl.endsWith("/")) {
            yarnProxyBaseUrl = yarnProxyBaseUrl.substring(0, yarnProxyBaseUrl.length() - 1);
        }
        return Response.SUCCESS(Collections.singletonMap("yarnProxyBaseUrl", yarnProxyBaseUrl));
    }
}
