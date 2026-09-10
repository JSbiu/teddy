package com.dbay.teddy.manager;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

public class WebHookSenderTest {

    private static final String KEY = "c1d6b3ea-7c27-4c40-bfaa-3c4ae3991174";

    @Test
    public void keepsHostAndPathButMasksTheKey() {
        String masked = WebHookSender.sanitize(
                "https://qyapi.weixin.qq.com/cgi-bin/webhook/send?key=" + KEY);

        assertEquals("https://qyapi.weixin.qq.com/cgi-bin/webhook/send (key ****1174)", masked);
        assertFalse(masked.contains(KEY));
    }

    @Test
    public void dropsOtherQueryParameters() {
        String masked = WebHookSender.sanitize(
                "https://qyapi.weixin.qq.com/cgi-bin/webhook/send?key=" + KEY + "&debug=1");

        assertEquals("https://qyapi.weixin.qq.com/cgi-bin/webhook/send (key ****1174)", masked);
    }

    @Test
    public void keepsAddressesWithoutAKey() {
        assertEquals("https://example.com/hook",
                WebHookSender.sanitize("https://example.com/hook"));
        assertEquals("https://example.com/hook",
                WebHookSender.sanitize("https://example.com/hook?token=1"));
    }

    @Test
    public void revealsNothingForShortOrMissingKeys() {
        assertEquals("https://example.com/hook (key ****)",
                WebHookSender.sanitize("https://example.com/hook?key=abcd"));
        assertEquals("-", WebHookSender.sanitize(null));
        assertEquals("-", WebHookSender.sanitize("   "));
        assertEquals("<无法解析的机器人地址>", WebHookSender.sanitize("not a url"));
    }
}
