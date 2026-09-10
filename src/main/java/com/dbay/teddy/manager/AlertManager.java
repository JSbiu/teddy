package com.dbay.teddy.manager;

import com.dbay.teddy.entity.Job;
import com.dbay.teddy.service.JobService;
import com.dbay.teddy.utils.TeddyConf;
import org.apache.commons.lang3.concurrent.BasicThreadFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * 任务失败告警。
 *
 * <p>同一次故障只在首次发现时立即通知，之后按 {@link AlertPolicy} 的间隔递增提醒；
 * 任务恢复时补一条恢复通知。状态按任务 ID 跟踪，因为自动重启会更换 ApplicationId，
 * 用 ApplicationId 会把同一次故障拆成多次。
 *
 * @author AlexanderGuo
 */
@Component
public class AlertManager implements ApplicationRunner {

    private static final DateTimeFormatter TIMESTAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    /** 每个任务的告警跟踪状态，键为任务 ID。 */
    private final Map<Integer, AlertTrack> tracks = new ConcurrentHashMap<>();

    @Autowired
    private WebHookSender webHookSender;

    @Autowired
    private JobService jobService;

    private final ScheduledExecutorService scheduledThreadPool = new ScheduledThreadPoolExecutor(1,
            new BasicThreadFactory.Builder().namingPattern("alert-pool-%d").daemon(true).build());

    @Override
    public void run(ApplicationArguments applicationArguments) {
        long scanIntervalSeconds = Long.parseLong(TeddyConf.get("alert.interval"));
        logger.info("启动告警线程，扫描间隔{}秒", scanIntervalSeconds);

        scheduledThreadPool.scheduleAtFixedRate(() -> {
            try {
                List<Job> jobs = jobService.findAllWithAppId();
                for (Job job : jobs) {
                    try {
                        inspect(job, scanIntervalSeconds);
                    } catch (RuntimeException e) {
                        logger.error("任务" + job.getId() + "告警检查失败", e);
                    }
                }
            } catch (RuntimeException e) {
                logger.error("告警扫描失败", e);
            }
        }, 0, scanIntervalSeconds, TimeUnit.SECONDS);
    }

    private void inspect(Job job, long scanIntervalSeconds) {
        Integer jobId = job.getId();
        if (jobId == null) {
            return;
        }

        if (!Integer.valueOf(1).equals(job.getSend())) {
            tracks.remove(jobId);
            return;
        }

        if (isBlank(job.getWebhook())) {
            // 没有地址就无从发送，不建立跟踪，也不推进退避：填上地址后会按首次故障通知。
            logger.debug("任务{}未填写机器人地址，跳过告警", jobId);
            return;
        }

        String state = job.getState();
        long now = System.currentTimeMillis();
        AlertTrack track = tracks.get(jobId);

        if (track == null) {
            track = new AlertTrack(state, scanIntervalSeconds);
            tracks.put(jobId, track);
            if (AlertPolicy.isFailure(state)) {
                sendFailure(job, track, now);
            }
            return;
        }

        AlertTrack.Action action = track.observe(state, now);
        if (action == AlertTrack.Action.FAILURE) {
            sendFailure(job, track, now);
        } else if (action == AlertTrack.Action.RECOVERY) {
            sendRecovered(job, track);
            track.reset();
        }
    }

    private void sendFailure(Job job, AlertTrack track, long now) {
        long intervalSeconds = track.markNotified(now);
        boolean willRestart = AlertPolicy.willRestartAutomatically(job);

        webHookSender.sendMarkdown(job.getWebhook(),
                failureMessage(job, track.notifyCount(), intervalSeconds, willRestart));
        logger.error("任务{}告警已发送（第{}次），剩余重启次数{}", job.getId(), track.notifyCount(), job.getRetries());
    }

    private void sendRecovered(Job job, AlertTrack track) {
        webHookSender.sendMarkdown(job.getWebhook(), recoveryMessage(job, track.notifyCount()));
        logger.info("任务{}已恢复，本次故障通知{}次", job.getId(), track.notifyCount());
    }

    private static String failureMessage(Job job, int notifyCount, long intervalSeconds, boolean willRestart) {
        StringBuilder builder = new StringBuilder();
        if (willRestart) {
            builder.append("<font color=\"comment\">任务异常 · ").append(job.getName())
                    .append("（将自动重启，剩余 ").append(job.getRetries()).append(" 次）</font>");
        } else {
            builder.append("<font color=\"warning\">任务异常 · ").append(job.getName()).append("</font>");
        }
        appendQuote(builder, "ApplicationId：" + text(job.getAppId()));
        appendQuote(builder, "YARN 终态：" + text(job.getState()));
        appendQuote(builder, "队列：" + text(job.getYarnQueue()));
        appendQuote(builder, "检测时间：" + LocalDateTime.now().format(TIMESTAMP));
        appendQuote(builder, "第 " + notifyCount + " 次通知，下次约 " + describeInterval(intervalSeconds) + "后");
        return builder.toString();
    }

    /** 整分钟时说分钟，比 600 秒直观。 */
    private static String describeInterval(long seconds) {
        if (seconds >= 60 && seconds % 60 == 0) {
            return (seconds / 60) + " 分钟";
        }
        return seconds + " 秒";
    }

    private static String recoveryMessage(Job job, int notifyCount) {
        StringBuilder builder = new StringBuilder();
        builder.append("<font color=\"info\">任务已恢复 · ").append(job.getName()).append("</font>");
        appendQuote(builder, "ApplicationId：" + text(job.getAppId()));
        appendQuote(builder, "恢复时间：" + LocalDateTime.now().format(TIMESTAMP));
        if (notifyCount > 0) {
            appendQuote(builder, "本次故障共通知 " + notifyCount + " 次");
        }
        return builder.toString();
    }

    private static void appendQuote(StringBuilder builder, String line) {
        builder.append("\n> ").append(line);
    }

    private static String text(String value) {
        return isBlank(value) ? "-" : value.trim();
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
