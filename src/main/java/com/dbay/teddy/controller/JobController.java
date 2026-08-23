package com.dbay.teddy.controller;

import com.dbay.teddy.entity.Job;
import com.dbay.teddy.service.JobService;
import com.dbay.teddy.utils.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

/**
 * @author AlexanderGuo
 */
@RestController
@RequestMapping("job")
public class JobController {

    private static final String RUNNING = "RUNNING";

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final JobService jobService;

    public JobController(JobService jobService) {
        this.jobService = jobService;
    }

    @RequestMapping(value = "submit", method = RequestMethod.POST)
    public Response submit(@RequestBody Job job) {
        logger.info("收到启动请求，任务名={}", job == null ? null : job.getName());
        if (jobService.start(job)) {
            return Response.SUCCESS("启动成功，前往监控页面查看状态！");
        }
        return Response.ERROR("启动失败o(╥﹏╥)o");
    }

    @RequestMapping(value = "update", method = RequestMethod.POST)
    public Response update(@RequestBody Job job) {
        logger.info("收到更新请求，任务名={}", job == null ? null : job.getName());
        if (jobService.reconfiguring(job)) {
            return Response.SUCCESS("配置已更新，前往监控页面重启！");
        }
        return Response.ERROR("配置更新失败o(╥﹏╥)o");
    }

    @RequestMapping("list")
    public Response list(Integer page, Integer size) {
        return Response.SUCCESS(jobService.list(1, 500));
    }

    @RequestMapping("find")
    public Response find(Integer id) {
        return Response.SUCCESS(jobService.findOne(id));
    }

    @RequestMapping(value = "delete", method = RequestMethod.POST)
    public Response delete(Integer id) {
        Job job = jobService.findOne(id);
        if (job == null) {
            return Response.ERROR("任务不存在");
        }
        if (RUNNING.equals(job.getState())) {
            return Response.ERROR("无法删除正在运行的任务");
        }
        jobService.delete(id);
        return Response.SUCCESS("已删除");
    }

    @RequestMapping(value = "stop", method = RequestMethod.POST)
    public Response stop(Integer id) {
        Boolean isSuccess = jobService.stop(id);
        return isSuccess ? Response.SUCCESS("停止成功") : Response.ERROR("停止失败");
    }

    @RequestMapping(value = "restart", method = RequestMethod.POST)
    public Response restart(Integer id) {
        Job job = jobService.findOne(id);
        if (job == null) {
            return Response.ERROR("任务不存在");
        }
        if (RUNNING.equals(job.getState())) {
            return Response.ERROR("正在执行，无法重启");
        }
        return jobService.restart(job)
                ? Response.SUCCESS("成功重启")
                : Response.ERROR("重启出错");
    }
}
