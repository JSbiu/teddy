package com.dbay.teddy.manager;

import com.dbay.teddy.entity.Job;

/**
 * 告警的判定规则，与调度和发送分离，便于单独测试。
 *
 * <p>同一个故障会按扫描间隔的 {@link #REPEAT_INTERVAL_MULTIPLIERS 倍数} 递增提醒，
 * 倍数封顶后固定循环：既不刷屏，也不会因为第一次提醒没人看见而彻底静默。
 */
public final class AlertPolicy {

    /**
     * 同一次故障重复提醒的间隔倍数，实际秒数 = 倍数 × 扫描间隔。最后一项之后一直沿用。
     *
     * <p>用倍数而不是固定秒数，是为了让阶梯随 {@code alert.interval} 一起伸缩。固定秒数
     * 的话，把扫描间隔调大之后前面几档会比扫描周期还短，实际退避不起来。
     */
    private static final long[] REPEAT_INTERVAL_MULTIPLIERS = {1L, 2L, 5L, 10L};

    private AlertPolicy() {
    }

    /**
     * 第 {@code notifyCount} 次通知之后，下一次提醒的等待秒数。扫描间隔为 60 秒时依次是
     * 60、120、300、600 秒。
     *
     * @param notifyCount         已经发出的通知次数，最小为 1
     * @param scanIntervalSeconds 告警扫描间隔（{@code alert.interval}），须为正数
     */
    public static long repeatIntervalSeconds(int notifyCount, long scanIntervalSeconds) {
        if (notifyCount < 1) {
            throw new IllegalArgumentException("notifyCount must be positive");
        }
        if (scanIntervalSeconds < 1) {
            throw new IllegalArgumentException("scanIntervalSeconds must be positive");
        }
        int index = Math.min(notifyCount - 1, REPEAT_INTERVAL_MULTIPLIERS.length - 1);
        return REPEAT_INTERVAL_MULTIPLIERS[index] * scanIntervalSeconds;
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
