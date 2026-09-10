package com.dbay.teddy.manager;

import java.util.concurrent.TimeUnit;

/**
 * 单个任务的告警跟踪状态。
 *
 * <p>把连续观察到的任务状态翻译成"该不该发通知"。退避与恢复的规则集中在这里，
 * 便于脱离调度和 HTTP 单独验证——告警逻辑无法在部署时冒烟，测试是唯一手段。
 */
final class AlertTrack {

    enum Action {
        /** 不需要发送。 */
        NONE,
        /** 发送失败告警。 */
        FAILURE,
        /** 发送恢复通知；调用方发送后需要调用 {@link #reset()}。 */
        RECOVERY
    }

    private String lastState;
    private int notifyCount;
    private long nextNotifyAt;

    AlertTrack(String initialState) {
        this.lastState = initialState;
    }

    /**
     * 记录本次观察到的状态，并给出该做的动作。
     *
     * @param currentState 本次扫描看到的状态
     * @param now          当前时间戳（毫秒）
     */
    Action observe(String currentState, long now) {
        String previousState = lastState;
        lastState = currentState;

        if (AlertPolicy.isRecovered(previousState, currentState)) {
            return Action.RECOVERY;
        }
        if (!AlertPolicy.isFailure(currentState)) {
            reset();
            return Action.NONE;
        }
        if (!AlertPolicy.isFailure(previousState) || now >= nextNotifyAt) {
            return Action.FAILURE;
        }
        return Action.NONE;
    }

    /**
     * 记录一次已发出的失败告警，并安排下一次提醒。
     *
     * @return 本次采用的提醒间隔（秒），供消息正文说明
     */
    long markNotified(long now) {
        notifyCount += 1;
        long intervalSeconds = AlertPolicy.repeatIntervalSeconds(notifyCount);
        nextNotifyAt = now + TimeUnit.SECONDS.toMillis(intervalSeconds);
        return intervalSeconds;
    }

    void reset() {
        notifyCount = 0;
        nextNotifyAt = 0L;
    }

    int notifyCount() {
        return notifyCount;
    }

    long nextNotifyAt() {
        return nextNotifyAt;
    }
}
