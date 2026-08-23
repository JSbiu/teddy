package com.dbay.teddy.service;

import com.dbay.teddy.entity.Job;
import com.dbay.teddy.manager.JarResourceManager;
import com.dbay.teddy.mapper.JobMapper;
import com.dbay.teddy.utils.TeddyConf;
import org.apache.commons.lang3.StringUtils;
import org.apache.spark.launcher.SparkAppHandle;
import org.apache.spark.launcher.SparkLauncher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

/**
 * @author AlexanderGuo
 */
@Service
public class JobService {

    private static final Pattern APPLICATION_ID =
            Pattern.compile("application_[0-9]+_[0-9]+");

    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    @Autowired
    private JobMapper jobMapper;

    @Autowired
    private JarResourceManager jarResourceManager;

    public Boolean start(Job job) {
        Job launchedJob;
        try {
            launchedJob = launch(job);
        } catch (Exception e) {
            logger.error("Spark application launch failed", e);
            return false;
        }

        try {
            save(launchedJob);
            return true;
        } catch (RuntimeException e) {
            logger.error("Spark application {} started but could not be persisted; killing it",
                    launchedJob.getAppId(), e);
            killApplication(launchedJob.getAppId());
            return false;
        }
    }

    public Boolean reconfiguring(Job job) {
        try {
            if (StringUtils.isBlank(job.getAppId())) {
                return false;
            }
            Job oldJob = findOneWithAppId(job.getAppId());
            if (Objects.nonNull(oldJob)) {
                job.setId(oldJob.getId());
                delete(oldJob.getId());
            }
            extractYarnQueue(job);
            updateConfigs(job);
            return true;
        } catch (RuntimeException e) {
            logger.error("Task reconfiguration failed", e);
            return false;
        }
    }

    private Job launch(Job job) throws Exception {
        logger.info("Launching Spark application {}", job.getName());
        SparkLauncher launcher = new SparkLauncher()
                .setAppName(job.getName())
                .setSparkHome(TeddyConf.get("spark.home"))
                .setMaster(job.getMaster())
                .setAppResource(jarResourceManager.resolveForLaunch(job.getAppResource()).toString())
                .setMainClass(job.getMainClass())
                .setDeployMode(job.getDeployMode());

        if (StringUtils.isNotEmpty(job.getArgs())) {
            launcher.addAppArgs(job.getArgs());
        }
        applySettings(job, launcher);

        File launcherLog = new File(TeddyConf.get("log.file"));
        launcher.redirectOutput(launcherLog);
        launcher.redirectError(launcherLog);

        SparkAppHandle handler = launcher.startApplication();
        long timeoutSeconds = configuredLong(
                "spark.submit.app-id-timeout-seconds", 120L, 10L, 1800L);
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(timeoutSeconds);

        while (handler.getAppId() == null) {
            SparkAppHandle.State state = handler.getState();
            logger.info("Waiting for Spark application id; launcher state={}", state);

            if (state.isFinal()) {
                throw new IllegalStateException("Spark launcher reached final state " + state
                        + " before returning an application id");
            }
            if (System.nanoTime() >= deadline) {
                killHandler(handler);
                throw new IllegalStateException("Timed out after " + timeoutSeconds
                        + " seconds waiting for a Spark application id");
            }

            try {
                Thread.sleep(1000L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                killHandler(handler);
                throw new IllegalStateException("Interrupted while waiting for Spark application id", e);
            }
        }

        String appId = handler.getAppId();
        if (!APPLICATION_ID.matcher(appId).matches()) {
            killHandler(handler);
            throw new IllegalStateException("Spark launcher returned an invalid application id");
        }

        job.setAppId(appId);
        job.setState("SUBMITTED");
        job.setTotalRunningTime("NONE");
        logger.info("Spark application id acquired: {}", appId);
        return job;
    }

    private void applySettings(Job job, SparkLauncher launcher) {
        String settings = job.getConfig();
        if (StringUtils.isBlank(settings)) {
            return;
        }

        for (String setting : StringUtils.splitByWholeSeparator(settings, ";")) {
            int separator = setting.indexOf('=');
            if (separator <= 0) {
                throw new IllegalArgumentException("Invalid Spark configuration entry");
            }
            String key = setting.substring(0, separator).trim();
            String value = setting.substring(separator + 1).trim();
            if (key.isEmpty()) {
                throw new IllegalArgumentException("Spark configuration key must not be blank");
            }
            launcher.setConf(key, value);
            if ("spark.yarn.queue".equals(key)) {
                job.setYarnQueue(value);
            }
        }
    }

    private void extractYarnQueue(Job job) {
        String settings = job.getConfig();
        if (StringUtils.isBlank(settings)) {
            return;
        }
        for (String setting : StringUtils.splitByWholeSeparator(settings, ";")) {
            int separator = setting.indexOf('=');
            if (separator <= 0) {
                throw new IllegalArgumentException("Invalid Spark configuration entry");
            }
            String key = setting.substring(0, separator).trim();
            if ("spark.yarn.queue".equals(key)) {
                job.setYarnQueue(setting.substring(separator + 1).trim());
            }
        }
    }

    private void killHandler(SparkAppHandle handler) {
        try {
            handler.kill();
        } catch (RuntimeException e) {
            logger.warn("Unable to kill Spark launcher handle after launch failure", e);
        }
    }

    public void autoRestart(Job job) {
        String previousAppId = job.getAppId();
        String previousState = job.getState();
        int retriesBefore = job.getRetries() == null ? 0 : job.getRetries();

        try {
            Job relaunched = launch(job);
            relaunched.setRetries((int) configuredLong(
                    "auto.restart.retries", 3L, 0L, 100L));
            update(relaunched);
        } catch (Exception e) {
            cleanupFailedRelaunch(job, previousAppId, previousState);
            job.setRetries(Math.max(0, retriesBefore - 1));
            try {
                update(job);
            } catch (RuntimeException persistenceError) {
                logger.error("Could not persist the remaining retry count for task "
                        + job.getId(), persistenceError);
            }
            logger.error("Automatic restart failed for task " + job.getId(), e);
        }
    }

    public Boolean restart(Job job) {
        String previousAppId = job.getAppId();
        String previousState = job.getState();
        try {
            update(launch(job));
            return true;
        } catch (Exception e) {
            cleanupFailedRelaunch(job, previousAppId, previousState);
            logger.error("Manual restart failed for task " + job.getId(), e);
            return false;
        }
    }

    private void cleanupFailedRelaunch(Job job, String previousAppId, String previousState) {
        String candidateAppId = job.getAppId();
        if (!Objects.equals(previousAppId, candidateAppId)
                && candidateAppId != null
                && APPLICATION_ID.matcher(candidateAppId).matches()) {
            killApplication(candidateAppId);
        }
        job.setAppId(previousAppId);
        job.setState(previousState);
    }

    public Boolean stop(Job job) {
        if (job == null || job.getAppId() == null
                || !APPLICATION_ID.matcher(job.getAppId()).matches()) {
            return false;
        }
        if (!killApplication(job.getAppId())) {
            return false;
        }

        job.setState("KILLED");
        try {
            update(job);
        } catch (RuntimeException e) {
            logger.warn("Application {} was killed but its state could not be persisted",
                    job.getAppId(), e);
        }
        return true;
    }

    private boolean killApplication(String appId) {
        if (appId == null || !APPLICATION_ID.matcher(appId).matches()) {
            return false;
        }

        String yarnCommand = TeddyConf.get("yarn.command", "yarn").trim();
        if (yarnCommand.isEmpty()) {
            logger.error("yarn.command must not be blank");
            return false;
        }

        ProcessBuilder processBuilder = new ProcessBuilder(
                yarnCommand, "application", "-kill", appId);
        processBuilder.redirectErrorStream(true);
        processBuilder.redirectOutput(
                ProcessBuilder.Redirect.appendTo(new File(TeddyConf.get("log.file"))));

        Process process = null;
        try {
            process = processBuilder.start();
            long timeoutSeconds = configuredLong(
                    "yarn.kill.timeout-seconds", 30L, 1L, 300L);
            if (!process.waitFor(timeoutSeconds, TimeUnit.SECONDS)) {
                process.destroy();
                if (!process.waitFor(5L, TimeUnit.SECONDS)) {
                    process.destroyForcibly();
                }
                logger.error("Timed out killing YARN application {}", appId);
                return false;
            }
            if (process.exitValue() != 0) {
                logger.error("YARN kill command failed for {} with exit code {}",
                        appId, process.exitValue());
                return false;
            }
            logger.info("Killed YARN application {}", appId);
            return true;
        } catch (IOException e) {
            logger.error("Could not start YARN kill command for " + appId, e);
            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            if (process != null) {
                process.destroyForcibly();
            }
            logger.error("Interrupted while killing YARN application " + appId, e);
            return false;
        }
    }

    private static long configuredLong(
            String key, long defaultValue, long minimum, long maximum) {
        String configured = TeddyConf.get(key, String.valueOf(defaultValue));
        try {
            long value = Long.parseLong(configured);
            if (value < minimum || value > maximum) {
                throw new IllegalStateException(key + " must be between "
                        + minimum + " and " + maximum);
            }
            return value;
        } catch (NumberFormatException e) {
            throw new IllegalStateException(key + " must be an integer", e);
        }
    }

    public void create() {
        jobMapper.create();
    }

    public Integer count() {
        try {
            return jobMapper.count();
        } catch (Exception e) {
            return -1;
        }
    }

    public List<Job> list(Integer page, Integer size) {
        return jobMapper.list((page - 1) * size, size);
    }

    public List<Job> findAllWithAppId() {
        return jobMapper.findAllWithAppId();
    }

    public Job findOneWithAppId(String appId) {
        return jobMapper.findOneWithAppId(appId);
    }

    public void save(Job job) {
        jobMapper.save(job);
    }

    public Job findOne(Integer id) {
        return jobMapper.findOne(id);
    }

    public void delete(Integer id) {
        jobMapper.delete(id);
    }

    public Boolean stop(Integer id) {
        return stop(jobMapper.findOne(id));
    }

    public void update(Job job) {
        jobMapper.update(job);
    }

    public void updateConfigs(Job job) {
        jobMapper.updateConfigs(job);
    }
}
