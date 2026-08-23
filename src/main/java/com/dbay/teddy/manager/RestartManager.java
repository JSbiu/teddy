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

import java.util.List;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * @author AlexanderGuo
 */
@Component
public class RestartManager implements ApplicationRunner {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    @Autowired
    private JobService jobService;

    private final ScheduledExecutorService scheduledThreadPool = new ScheduledThreadPoolExecutor(1,
            new BasicThreadFactory.Builder().namingPattern("restart-pool-%d").daemon(true).build());

    @Override
    public void run(ApplicationArguments args) {
        logger.info("启动自动重启线程");

        long restartInterval = Long.parseLong(TeddyConf.get("auto.restart.interval"));
        long initialDelay = Long.parseLong(TeddyConf.get(
                "auto.restart.initial-delay",
                String.valueOf(restartInterval)));

        scheduledThreadPool.scheduleAtFixedRate(() -> {
            try {
                List<Job> jobs = jobService.findAllWithAppId();
                logger.info("扫描到{}个任务需要检测是否重启", jobs.size());
                for (Job job : jobs) {
                    if (JobStatePolicy.shouldAutoRestart(job)) {
                        try {
                            logger.info("尝试重启任务{}，剩余次数{}", job.getId(), job.getRetries());
                            jobService.autoRestart(job);
                        } catch (RuntimeException e) {
                            logger.error("自动重启任务" + job.getId() + "失败", e);
                        }
                    }
                }
            } catch (RuntimeException e) {
                logger.error("自动重启扫描失败", e);
            }
        }, initialDelay, restartInterval, TimeUnit.SECONDS);
    }
}
