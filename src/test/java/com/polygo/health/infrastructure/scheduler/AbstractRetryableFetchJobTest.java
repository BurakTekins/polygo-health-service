package com.polygo.health.infrastructure.scheduler;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.http.HttpStatus;
import java.util.ArrayDeque;
import java.util.concurrent.Executor;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class AbstractRetryableFetchJobTest {
    private final AbstractRetryableFetchJob<String> job = mock(AbstractRetryableFetchJob.class,
            withSettings().defaultAnswer(CALLS_REAL_METHODS).useConstructor());

    @AfterEach
    void clearContext() { MDC.clear(); }

    @Test
    void successIsDeliveredAndNextScheduledRunCanExecute() throws Exception {
        ReflectionTestUtils.setField(job, "jobTaskExecutor", (Executor) Runnable::run);
        doReturn("healthy").when(job).fetch();
        job.startAsyncJob();
        job.startAsyncJob();
        verify(job, times(2)).onSuccess("healthy");
        verify(job, never()).onFinalFailure(any());
    }

    @Test
    void queuedRunSuppressesDuplicateExecutionAndCarriesCorrelationContext() throws Exception {
        var queue = new ArrayDeque<Runnable>();
        ReflectionTestUtils.setField(job, "jobTaskExecutor", (Executor) queue::add);
        MDC.put("correlationId", "job-123");
        doAnswer(invocation -> {
            assertThat(MDC.get("correlationId")).isEqualTo("job-123");
            return "healthy";
        }).when(job).fetch();
        job.startAsyncJob();
        job.startAsyncJob();
        assertThat(queue).hasSize(1);
        MDC.clear();
        queue.remove().run();
        assertThat(MDC.getCopyOfContextMap()).isNullOrEmpty();
        verify(job).fetch();
    }

    @Test
    void nonRetryableFailureIsDeliveredOnceAndAllowsNextRun() throws Exception {
        ReflectionTestUtils.setField(job, "jobTaskExecutor", (Executor) Runnable::run);
        var failure = new IllegalArgumentException("bad response");
        doThrow(failure).doReturn("recovered").when(job).fetch();
        job.startAsyncJob();
        job.startAsyncJob();
        verify(job).onFinalFailure(failure);
        verify(job).onSuccess("recovered");
        verify(job, times(2)).fetch();
    }

    @Test
    void interruptionCancelsServerErrorRetryAndPreservesInterruptFlag() throws Exception {
        ReflectionTestUtils.setField(job, "jobTaskExecutor", (Executor) Runnable::run);
        doThrow(new HttpServerErrorException(HttpStatus.SERVICE_UNAVAILABLE)).when(job).fetch();
        Thread.currentThread().interrupt();
        try {
            job.startAsyncJob();
            assertThat(Thread.currentThread().isInterrupted()).isTrue();
            verify(job).fetch();
            verify(job, never()).onSuccess(any());
            verify(job, never()).onFinalFailure(any());
        } finally {
            Thread.interrupted();
        }
    }
}
