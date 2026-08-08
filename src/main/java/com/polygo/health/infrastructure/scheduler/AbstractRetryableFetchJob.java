package com.polygo.health.infrastructure.scheduler;

import com.polygo.health.util.ErrorMessageResolver;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.client.HttpServerErrorException;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
public abstract class AbstractRetryableFetchJob<T> {

    @Autowired
    @Qualifier("jobTaskExecutor")
    private Executor jobTaskExecutor;

    private final AtomicBoolean isRunning = new AtomicBoolean(false);

    public void startAsyncJob() {
        if (isRunning.get()) {
            log.warn("Job is already running/retrying. Skipping this execution.");
            return;
        }

        isRunning.set(true);

        Map<String, String> contextMap = MDC.getCopyOfContextMap();

        CompletableFuture.runAsync(() -> {
            if (contextMap != null) {
                MDC.setContextMap(contextMap);
            }

            try {
                fetchWithRetry(1);
            } finally {
                MDC.clear();
                isRunning.set(false);
            }
        }, jobTaskExecutor);
    }

    private void fetchWithRetry(int attempt) {
        try {
            T result = fetch();
            onSuccess(result);

        } catch (Exception e) {

            boolean retryable = e instanceof HttpServerErrorException;

            if (!retryable || attempt >= 3) {
                log.error("Fetch failed after {} attempts: {}", attempt, ErrorMessageResolver.resolve(e));
                onFinalFailure(e);
                return;
            }

            long waitMillis = (attempt == 1) ? 15_000 : 60_000;

            log.warn("Fetch attempt {} failed ({}). Next retry in {} ms",
                    attempt,
                    ErrorMessageResolver.resolve(e),
                    waitMillis
            );

            try {
                Thread.sleep(waitMillis);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                return;
            }

            fetchWithRetry(attempt + 1);
        }
    }

    protected abstract T fetch() throws Exception;

    protected abstract void onSuccess(T result);

    protected abstract void onFinalFailure(Exception e);
}