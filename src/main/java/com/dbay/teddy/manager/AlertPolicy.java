package com.dbay.teddy.manager;

import com.dbay.teddy.entity.Job;

/**
 * 告警的判定规则，与调度和发送分离，便于单独测试。
 *
 * <p>同一个故障会按 {@link #REPEAT_INTERVALS_SECONDS} 递增的间隔反复提醒，间隔封顶后
 * 固定循环：既不刷屏，也不会因为第一次提醒没人看见而彻底静默。
 */
public final class AlertPolicy {

    /**
     * 同一次故障的重复提醒间隔（秒）。最后一项之后一直沿用，不再回到初始间隔。
     */
    private static final long[] REPEAT_INTERVALS_SECONDS = {60L, 120L, 300L, 600L};

    private AlertPolicy() {
    }

    /**
     * 第 {@code notifyCount} 次通知之后，下一次提醒的等待秒数。
     *
     * @param notifyCount 已经发出的通知次数，最小为 1
     */
    public static long repeatIntervalSeconds(int notifyCount) {
        if (notifyCount < 1) {
            throw new IllegalArgumentException("notifyCount must be positive");
        }
        int index = Math.min(notifyCount - 1, REPEAT_INTERVALS_SECONDS.length - 1);
        return REPEAT_INTERVALS_SECONDS[index];
    }

    /** 重复提醒的间隔档数，用于说明封顶位置。 */
    public static int repeatIntervalCount() {
        return REPEAT_INTERVALS_SECONDS.length;
    }

    /** 需要人介入的终态失败。过渡态不会走到这里，调用方只看落库状态。 */
    public static boolean isFailure(String state) {
        return "FAILED".equals(JobStatePolicy.normalize(state));
    }

    /**
     * 恢复指同一个任务重新跑起来（自动重启成功也会走到这里）。被 KILL 不是恢复。
     */
    public static boolean isRecovered(String previousState, String currentState) {
        return isFailure(previousState) && "RUNNING".equals(JobStatePolicy.normalize(currentState));
    }

    /**
     * 该任务的这次失败是否会由自动重启处理。只在还有剩余次数时成立：次数用尽后
     * 的下一次失败必须按正式故障上报。
     */
    public static boolean willRestartAutomatically(Job job) {
        return job != null
                && Integer.valueOf(1).equals(job.getRestart())
                && job.getRetries() != null
                && job.getRetries() > 0;
    }
}
