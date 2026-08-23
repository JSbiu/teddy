package com.dbay.teddy.manager;

import com.dbay.teddy.entity.App;
import com.dbay.teddy.entity.Job;
import com.dbay.teddy.service.JobService;
import com.dbay.teddy.service.YarnService;
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
 * Refreshes persisted job state from one consistent YARN snapshot per cycle.
 *
 * @author AlexanderGuo
 */
@Component
public class StateRefresher implements ApplicationRunner {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final YarnService yarnService;
    private final JobService jobService;

    private final ScheduledExecutorService scheduledThreadPool = new ScheduledThreadPoolExecutor(1,
            new BasicThreadFactory.Builder().namingPattern("state-refresher-pool-%d").daemon(true).build());

    @Autowired
    public StateRefresher(YarnService yarnService, JobService jobService) {
        this.yarnService = yarnService;
        this.jobService = jobService;
    }

    @Override
    public void run(ApplicationArguments applicationArguments) {
        logger.info("启动状态刷新线程");
        long refreshInterval = Long.parseLong(TeddyConf.get("state.refresh.interval"));
        scheduledThreadPool.scheduleAtFixedRate(this::refreshOnce,
                0, refreshInterval, TimeUnit.SECONDS);
    }

    void refreshOnce() {
        try {
            List<Job> jobs = jobService.findAllWithAppId();
            logger.info("监测到{}条 appId 不为空的任务", jobs.size());
            for (Job job : jobs) {
                refresh(job);
            }
        } catch (RuntimeException e) {
            logger.error("读取待刷新任务失败", e);
        }
    }

    void refresh(Job job) {
        try {
            App snapshot = yarnService.app(job.getAppId());
            String state = JobStatePolicy.persistedState(snapshot);
            job.setState(state);
            job.setTotalRunningTime(snapshot.totalRunningTime());
            jobService.update(job);
            logger.info("任务{}状态更新为{}", job.getId(), state);
        } catch (RuntimeException e) {
            logger.warn("暂不更新任务{}（{}）的状态，保留上次结果：{}",
                    job.getId(), job.getAppId(), e.getMessage());
        }
    }
}
