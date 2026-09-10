package com.dbay.teddy.manager;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;

/**
 * 企业微信机器人的 markdown 消息发送。
 *
 * <p>发送结果必须检查：机器人在密钥失效或被限频时仍可能返回 HTTP 200，只有响应体里的
 * {@code errcode} 才能说明是否真的送达。日志里只出现地址的主机和密钥末四位。
 *
 * @author AlexanderGuo
 */
@Component
public class WebHookSender {

    private static final int CONNECT_TIMEOUT_MS = 3000;
    private static final int READ_TIMEOUT_MS = 5000;

    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    private final CloseableHttpClient httpclient = HttpClients.custom()
            .setDefaultRequestConfig(RequestConfig.custom()
                    .setConnectTimeout(CONNECT_TIMEOUT_MS)
                    .setConnectionRequestTimeout(CONNECT_TIMEOUT_MS)
                    .setSocketTimeout(READ_TIMEOUT_MS)
                    .build())
            .build();

    /** 地址为空时直接返回；多个地址用英文分号分隔，逐个发送。 */
    public void sendMarkdown(String webHook, String markdown) {
        if (webHook == null || webHook.trim().isEmpty()) {
            return;
        }
        for (String url : webHook.split(";")) {
            String target = url.trim();
            if (!target.isEmpty()) {
                send(target, markdown);
            }
        }
    }

    private void send(String url, String markdown) {
        String safeUrl = sanitize(url);

        JSONObject content = new JSONObject();
        content.put("content", markdown);
        JSONObject payload = new JSONObject();
        payload.put("msgtype", "markdown");
        payload.put("markdown", content);

        HttpPost httpPost = new HttpPost(url);
        httpPost.setHeader("Content-Type", "application/json;charset=utf-8");
        httpPost.setEntity(new StringEntity(payload.toJSONString(), "utf-8"));

        try (CloseableHttpResponse response = httpclient.execute(httpPost)) {
            int status = response.getStatusLine().getStatusCode();
            String body = response.getEntity() == null
                    ? ""
                    : EntityUtils.toString(response.getEntity(), "utf-8");

            if (status < 200 || status >= 300) {
                logger.error("机器人消息发送失败，{} 返回 HTTP {}，响应{}", safeUrl, status, body);
                return;
            }

            Integer errcode = errcode(body);
            if (errcode == null || errcode != 0) {
                logger.error("机器人消息被拒绝，{} errcode={}，响应{}", safeUrl, errcode, body);
                return;
            }

            logger.info("机器人消息已送达 {}", safeUrl);
        } catch (IOException e) {
            logger.error("机器人消息发送异常，{}：{}", safeUrl, e.getMessage());
        }
    }

    private Integer errcode(String body) {
        try {
            JSONObject result = JSON.parseObject(body);
            return result == null ? null : result.getInteger("errcode");
        } catch (RuntimeException e) {
            return null;
        }
    }

    /**
     * 去掉查询串里的密钥，只保留协议、主机、路径和密钥末四位，避免机器人凭据进入日志。
     */
    static String sanitize(String url) {
        if (url == null || url.trim().isEmpty()) {
            return "-";
        }
        String trimmed = url.trim();
        try {
            URI uri = URI.create(trimmed);
            String base = uri.getScheme() + "://" + uri.getHost() + uri.getPath();
            String query = uri.getQuery();
            if (query == null) {
                return base;
            }
            int keyAt = query.indexOf("key=");
            if (keyAt < 0) {
                return base;
            }
            String key = query.substring(keyAt + "key=".length());
            int separator = key.indexOf('&');
            if (separator >= 0) {
                key = key.substring(0, separator);
            }
            return base + " (key ****" + tail(key) + ")";
        } catch (RuntimeException e) {
            return "<无法解析的机器人地址>";
        }
    }

    private static String tail(String value) {
        if (value == null || value.length() < 8) {
            // 过短的值不展示任何片段，避免把整个凭据暴露成"末四位"。
            return "";
        }
        return value.substring(value.length() - 4);
    }
}
