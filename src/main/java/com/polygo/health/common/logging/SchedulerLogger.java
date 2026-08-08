package com.polygo.health.common.logging;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public final class SchedulerLogger {
    private SchedulerLogger() {}

    public static void execute(String jobName, Runnable runnable) {
        String correlationId = MdcUtil.generateJobCorrelationId(jobName);
        long startTime = System.currentTimeMillis();

        try {
            MdcUtil.setCorrelationId(correlationId);
            MdcUtil.setJobName(jobName);
            log.info("Scheduled job started: jobName={}", jobName);

            runnable.run();

            long duration = System.currentTimeMillis() - startTime;
            MdcUtil.setDuration(duration);
            log.info("Scheduled job completed: jobName={} durationMs={}", jobName, duration);
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            MdcUtil.setDuration(duration);
            log.error("Scheduled job failed: jobName={} durationMs={} error={}", jobName, duration, e.getMessage(), e);
            throw e;
        } finally {
            MdcUtil.clear();
        }
    }
}
