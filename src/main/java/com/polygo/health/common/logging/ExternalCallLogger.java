package com.polygo.health.common.logging;

import lombok.extern.slf4j.Slf4j;
import java.util.function.Supplier;

@Slf4j
public final class ExternalCallLogger {

    private ExternalCallLogger() {}

    public static <T> T execute(String providerName, String operation, Supplier<T> supplier) {
        long startTime = System.currentTimeMillis();

        MdcUtil.setProvider(providerName);

        try {
            log.info("External call started: provider={} operation={}", providerName, operation);

            T result = supplier.get();

            long duration = System.currentTimeMillis() - startTime;
            MdcUtil.setDuration(duration);
            log.info("External call completed: provider={} operation={} durationMs={}", providerName, operation, duration);

            return result;
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            MdcUtil.setDuration(duration);
            log.error("External call failed: provider={} operation={} durationMs={} error={}",
                    providerName, operation, duration, e.getMessage());
            throw e;
        } finally {
        }
    }

    public static void execute(String providerName, String operation, Runnable runnable) {
        execute(providerName, operation, () -> {
            runnable.run();
            return null;
        });
    }
}