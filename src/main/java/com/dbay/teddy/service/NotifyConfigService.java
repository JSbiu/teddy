package com.dbay.teddy.service;

import com.dbay.teddy.entity.NotifyConfig;
import com.dbay.teddy.mapper.NotifyConfigMapper;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 通知配置的读写。保证"全局最多一条默认项"这条不变式。
 *
 * @author AlexanderGuo
 */
@Service
public class NotifyConfigService {

    private static final int MAX_WEBHOOK_LENGTH = 500;
    private static final int MAX_NAME_LENGTH = 100;

    private final NotifyConfigMapper notifyConfigMapper;

    @Autowired
    public NotifyConfigService(NotifyConfigMapper notifyConfigMapper) {
        this.notifyConfigMapper = notifyConfigMapper;
    }

    public void create() {
        notifyConfigMapper.create();
    }

    /**
     * 表不存在时返回 -1，调用方据此决定是否建表。
     */
    public Integer count() {
        try {
            return notifyConfigMapper.count();
        } catch (Exception e) {
            return -1;
        }
    }

    public List<NotifyConfig> list() {
        return notifyConfigMapper.list();
    }

    public NotifyConfig findDefault() {
        return notifyConfigMapper.findDefault();
    }

    public void save(NotifyConfig config) {
        validate(config);
        if (Integer.valueOf(1).equals(config.getIsDefault())) {
            notifyConfigMapper.clearDefault();
        }
        if (config.getId() == null) {
            notifyConfigMapper.save(config);
            return;
        }
        if (notifyConfigMapper.findOne(config.getId()) == null) {
            throw new IllegalArgumentException("要更新的通知配置不存在");
        }
        notifyConfigMapper.update(config);
    }

    public void delete(Integer id) {
        if (id == null) {
            throw new IllegalArgumentException("通知配置 id 不能为空");
        }
        notifyConfigMapper.delete(id);
    }

    private void validate(NotifyConfig config) {
        if (config == null) {
            throw new IllegalArgumentException("通知配置不能为空");
        }
        if (StringUtils.isBlank(config.getName())) {
            throw new IllegalArgumentException("配置名称不能为空");
        }
        String webhook = StringUtils.trimToEmpty(config.getWebhook());
        if (webhook.isEmpty()) {
            throw new IllegalArgumentException("机器人地址不能为空");
        }
        if (!webhook.startsWith("https://")) {
            throw new IllegalArgumentException("机器人地址必须以 https:// 开头");
        }
        if (webhook.length() > MAX_WEBHOOK_LENGTH) {
            throw new IllegalArgumentException("机器人地址过长");
        }
        if (config.getName().trim().length() > MAX_NAME_LENGTH) {
            throw new IllegalArgumentException("配置名称过长");
        }
        if (config.getIsDefault() == null) {
            config.setIsDefault(0);
        } else if (!Integer.valueOf(0).equals(config.getIsDefault())
                && !Integer.valueOf(1).equals(config.getIsDefault())) {
            throw new IllegalArgumentException("默认标记只能是 0 或 1");
        }
        config.setName(config.getName().trim());
        config.setWebhook(webhook);
    }
}
