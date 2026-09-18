package com.dbay.teddy.manager;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

public class WebHookSenderTest {

    /**
     * 明显的占位值，不是任何真实机器人地址。
     *
     * 这里要证明的是脱敏行为本身，把待脱敏的原值写进代码等于没有脱敏——
     * 仓库是公开的，测试文件与源码一样会被人看到。
     */
    private static final String KEY = "11111111-2222-3333-4444-55555555abcd";

    @Test
    public void keepsHostAndPathButMasksTheKey() {
        String masked = WebHookSender.sanitize(
                "https://qyapi.weixin.qq.com/cgi-bin/webhook/send?key=" + KEY);

        assertEquals("https://qyapi.weixin.qq.com/cgi-bin/webhook/send (key ****abcd)", masked);
        assertFalse(masked.contains(KEY));
    }

    @Test
    public void dropsOtherQueryParameters() {
        String masked = WebHookSender.sanitize(
                "https://qyapi.weixin.qq.com/cgi-bin/webhook/send?key=" + KEY + "&debug=1");

        assertEquals("https://qyapi.weixin.qq.com/cgi-bin/webhook/send (key ****abcd)", masked);
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
