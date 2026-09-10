package com.dbay.teddy.service;

import com.dbay.teddy.entity.NotifyConfig;
import com.dbay.teddy.mapper.NotifyConfigMapper;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class NotifyConfigServiceTest {

    private NotifyConfigMapper mapper;
    private NotifyConfigService service;

    @Before
    public void setUp() {
        mapper = mock(NotifyConfigMapper.class);
        service = new NotifyConfigService(mapper);
    }

    @Test
    public void savingAsDefaultClearsThePreviousDefaultFirst() {
        NotifyConfig config = config(null, "实时采集群", "https://example.com/hook", 1);

        service.save(config);

        verify(mapper).clearDefault();
        verify(mapper).save(config);
    }

    @Test
    public void savingWithoutDefaultFlagKeepsTheExistingDefault() {
        NotifyConfig config = config(null, "备用群", "https://example.com/hook", 0);

        service.save(config);

        verify(mapper, never()).clearDefault();
        verify(mapper).save(config);
    }

    @Test
    public void missingDefaultFlagIsNormalisedToZero() {
        NotifyConfig config = config(null, "备用群", "https://example.com/hook", null);

        service.save(config);

        assertEquals(Integer.valueOf(0), config.getIsDefault());
        verify(mapper, never()).clearDefault();
    }

    @Test
    public void updatingAnUnknownRecordIsRejected() {
        NotifyConfig config = config(7, "实时采集群", "https://example.com/hook", 0);
        when(mapper.findOne(7)).thenReturn(null);

        assertRejected(config);
        verify(mapper, never()).update(any(NotifyConfig.class));
    }

    @Test
    public void updatingAKnownRecordGoesThrough() {
        NotifyConfig config = config(7, "实时采集群", "https://example.com/hook", 0);
        when(mapper.findOne(7)).thenReturn(config);

        service.save(config);

        verify(mapper).update(config);
        verify(mapper, never()).save(any(NotifyConfig.class));
    }

    @Test
    public void blankNameIsRejected() {
        assertRejected(config(null, "  ", "https://example.com/hook", 0));
    }

    @Test
    public void blankWebhookIsRejected() {
        assertRejected(config(null, "实时采集群", "   ", 0));
    }

    @Test
    public void nonHttpsWebhookIsRejected() {
        assertRejected(config(null, "实时采集群", "http://example.com/hook", 0));
    }

    @Test
    public void oversizedWebhookIsRejected() {
        StringBuilder longWebhook = new StringBuilder("https://example.com/");
        while (longWebhook.length() <= 500) {
            longWebhook.append('a');
        }

        assertRejected(config(null, "实时采集群", longWebhook.toString(), 0));
    }

    @Test
    public void invalidDefaultFlagIsRejected() {
        assertRejected(config(null, "实时采集群", "https://example.com/hook", 2));
    }

    @Test
    public void deletingWithoutIdIsRejected() {
        try {
            service.delete(null);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            // 期望的失败
        }
        verify(mapper, never()).delete(any(Integer.class));
    }

    @Test
    public void countFallsBackToMinusOneWhenTheTableIsMissing() throws Exception {
        when(mapper.count()).thenThrow(new RuntimeException("table missing"));

        assertEquals(Integer.valueOf(-1), service.count());
    }

    private void assertRejected(NotifyConfig config) {
        try {
            service.save(config);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            // 期望的失败
        }
        verify(mapper, never()).save(any(NotifyConfig.class));
        verify(mapper, never()).update(any(NotifyConfig.class));
    }

    private static NotifyConfig config(Integer id, String name, String webhook, Integer isDefault) {
        NotifyConfig config = new NotifyConfig();
        config.setId(id);
        config.setName(name);
        config.setWebhook(webhook);
        config.setIsDefault(isDefault);
        return config;
    }
}
