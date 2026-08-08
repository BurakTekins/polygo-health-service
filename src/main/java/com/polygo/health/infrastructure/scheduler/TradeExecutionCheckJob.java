package com.polygo.health.infrastructure.scheduler;

import com.polygo.health.common.logging.SchedulerLogger;
import com.polygo.health.domain.enums.JobStatus;
import com.polygo.health.domain.enums.JobType;
import com.polygo.health.application.service.ConfigParametersService;
import com.polygo.health.application.service.JobHealthStatusService;
import com.polygo.health.application.service.NotificationService;
import com.polygo.health.util.DurationFormatter;
import com.polygo.health.util.ErrorMessageResolver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class TradeExecutionCheckJob extends AbstractRetryableFetchJob<List<Map<String, Object>>> {

    private final RestTemplate restTemplate;
    private final NotificationService notificationService;
    private final ConfigParametersService configParametersService;
    private final JobHealthStatusService jobHealthStatusService;

    @Value("${services.kong-url}")
    private String kongBaseUrl;

    private OffsetDateTime lastKnownTradeTime = null;
    private long lastAlertedLevel = 0;
    private OffsetDateTime lastErrorNotificationTime = null;

    @Scheduled(
            fixedRateString = "#{@configParametersServiceImpl.getConfigValue('scheduling','fixed-rate-ms')}",
            initialDelayString = "#{@configParametersServiceImpl.getConfigValue('scheduling','initial-delay-ms')}"
    )
    public void checkTradeExecutions() {
        SchedulerLogger.execute("TradeExecutionCheckJob", this::startAsyncJob);
    }

    @Override
    protected List<Map<String, Object>> fetch() {

        String endpoint = configParametersService.getConfigValue("trade-execution", "endpoint");

        URI uri = UriComponentsBuilder
                .fromUriString(kongBaseUrl)
                .path(endpoint)
                .build()
                .toUri();

        ResponseEntity<List<Map<String, Object>>> response =
                restTemplate.exchange(
                        uri,
                        HttpMethod.GET,
                        null,
                        new ParameterizedTypeReference<List<Map<String, Object>>>() {}
                );

        return response.getBody();
    }

    @Override
    protected void onSuccess(List<Map<String, Object>> tradeExecutions) {

        lastErrorNotificationTime = null;

        OffsetDateTime nowUtc = OffsetDateTime.now(ZoneOffset.UTC);

        int thresholdMinutes = Integer.parseInt(
                configParametersService.getConfigValue("trade-execution", "threshold-minutes"));

        int notificationThresholdMinutes = Integer.parseInt(
                configParametersService.getConfigValue("trade-execution", "notification-threshold-minutes"));

        if (tradeExecutions == null || tradeExecutions.isEmpty()) {
            handleNoData(nowUtc, thresholdMinutes, notificationThresholdMinutes);
            return;
        }

        OffsetDateTime lastDetected = extractLastTradeTime(tradeExecutions);
        if (lastDetected == null) {
            handleNoData(nowUtc, thresholdMinutes, notificationThresholdMinutes);
            return;
        }

        if (lastKnownTradeTime == null || lastDetected.isAfter(lastKnownTradeTime)) {

            log.info("New trade execution detected at {}, resetting alert level", lastDetected);

            lastKnownTradeTime = lastDetected;
            lastAlertedLevel = 0;

            jobHealthStatusService.upsert(
                    JobType.TRADE_EXECUTION,
                    JobStatus.OK,
                    "Trade executions detected recently"
            );
            return;
        }

        checkStaleness(nowUtc, thresholdMinutes, notificationThresholdMinutes);
    }

    @Override
    protected void onFinalFailure(Exception e) {

        String errorMsg = "Trade executions API failed after retries: " +
                ErrorMessageResolver.resolve(e);

        int notificationThresholdMinutes = Integer.parseInt(
                configParametersService.getConfigValue("trade-execution", "notification-threshold-minutes"));

        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

        if (lastErrorNotificationTime == null ||
                Duration.between(lastErrorNotificationTime, now).toMinutes() >= notificationThresholdMinutes) {

            notificationService.sendNotification(errorMsg);
            lastErrorNotificationTime = now;
        }

        jobHealthStatusService.upsert(
                JobType.TRADE_EXECUTION,
                JobStatus.ERROR,
                errorMsg
        );
    }

    private void checkStaleness(
            OffsetDateTime nowUtc,
            int thresholdMinutes,
            int notificationThresholdMinutes
    ) {
        if (lastKnownTradeTime == null) {
            return;
        }

        Duration duration = Duration.between(lastKnownTradeTime, nowUtc);
        long minutesPassed = duration.toMinutes();

        long currentLevel = minutesPassed / notificationThresholdMinutes;

        if (minutesPassed >= thresholdMinutes && currentLevel > lastAlertedLevel) {

            String msg = "More than " +
                    DurationFormatter.fromDuration(duration) +
                    " have passed since the last trade execution.";

            notificationService.sendNotification(msg);
            lastAlertedLevel = currentLevel;

            jobHealthStatusService.upsert(
                    JobType.TRADE_EXECUTION,
                    JobStatus.WARN,
                    msg
            );
        }
    }

    private void handleNoData(
            OffsetDateTime nowUtc,
            int thresholdMinutes,
            int notificationThresholdMinutes
    ) {
        log.warn("Trade execution API returned no usable data");

        if (lastKnownTradeTime != null) {
            checkStaleness(nowUtc, thresholdMinutes, notificationThresholdMinutes);
        } else {
            jobHealthStatusService.upsert(
                    JobType.TRADE_EXECUTION,
                    JobStatus.WARN,
                    "No trade executions returned"
            );
        }
    }

    @SuppressWarnings("unchecked")
    private OffsetDateTime extractLastTradeTime(List<Map<String, Object>> tradeExecutions) {
        try {
            Map<String, Object> wrapper = tradeExecutions.get(0);
            Map<String, Object> tradeExecution = (Map<String, Object>) wrapper.get("tradeExecution");
            List<Map<String, Object>> trades = (List<Map<String, Object>>) tradeExecution.get("trades");

            if (trades == null || trades.isEmpty()) {
                return null;
            }

            Object executedAt = trades.get(0).get("executedAt");
            return executedAt != null
                    ? OffsetDateTime.parse(executedAt.toString())
                    : null;

        } catch (Exception e) {
            log.error("Failed to extract last trade time", e);
            return null;
        }
    }
}
