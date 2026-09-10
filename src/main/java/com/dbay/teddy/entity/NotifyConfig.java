package com.dbay.teddy.entity;

/**
 * 可复用的通知配置。新建任务时可以据此填入默认的机器人地址。
 *
 * @author AlexanderGuo
 */
public class NotifyConfig {
    private Integer id;
    private String name;
    private String webhook;
    private Integer isDefault;

    public NotifyConfig() {
    }

    /**
     * 刻意不包含 webhook，避免机器人地址进入日志。
     */
    @Override
    public String toString() {
        return "NotifyConfig{" +
                "id=" + id +
                ", name='" + name + '\'' +
                ", isDefault=" + isDefault +
                '}';
    }

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getWebhook() {
        return webhook;
    }

    public void setWebhook(String webhook) {
        this.webhook = webhook;
    }

    public Integer getIsDefault() {
        return isDefault;
    }

    public void setIsDefault(Integer isDefault) {
        this.isDefault = isDefault;
    }
}
