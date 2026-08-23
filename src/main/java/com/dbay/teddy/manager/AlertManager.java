package com.dbay.teddy.manager;

import com.alibaba.fastjson.JSON;
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
public class AlertManager implements ApplicationRunner {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    @Autowired
    private WebHookSender webHookSender;

    @Autowired
    private JobService jobService;

    private final ScheduledExecutorService scheduledThreadPool = new ScheduledThreadPoolExecutor(1,
            new BasicThreadFactory.Builder().namingPattern("alert-pool-%d").daemon(true).build());

    @Override
    public void run(ApplicationArguments applicationArguments) {
        logger.info("启动告警线程");

        scheduledThreadPool.scheduleAtFixedRate(() -> {
            try {
                List<Job> jobs = jobService.findAllWithAppId();
                for (Job job : jobs) {
                    if (JobStatePolicy.shouldAlert(job)) {
                        logger.error("检测到失败任务{}", job.getId());
                        webHookSender.wxRobotSend(job.getWebhook(),
                                job.getName() + "状态异常",
                                JSON.toJSONString("state:" + job.getState()));
                    }
                }
            } catch (RuntimeException e) {
                logger.error("告警扫描失败", e);
            }
        }, 0, Long.parseLong(TeddyConf.get("alert.interval")), TimeUnit.SECONDS);
    }
}
