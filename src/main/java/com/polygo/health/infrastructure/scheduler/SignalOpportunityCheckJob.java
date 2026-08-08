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
public class SignalOpportunityCheckJob extends AbstractRetryableFetchJob<List<Map<String, Object>>> {

    private final RestTemplate restTemplate;
    private final NotificationService notificationService;
    private final ConfigParametersService dynamicConfigService;
    private final JobHealthStatusService jobHealthStatusService;

    @Value("${services.kong-url}")
    private String kongBaseUrl;

    private OffsetDateTime lastKnownOpportunityTime = null;
    private long lastAlertedLevel = 0;
    private OffsetDateTime lastErrorSentAt = null;

    @Scheduled(fixedRateString = "#{@configParametersServiceImpl.getConfigValue('scheduling','fixed-rate-ms') ?: '60000'}",
            initialDelayString = "#{@configParametersServiceImpl.getConfigValue('scheduling','initial-delay-ms') ?: '10000'}")
    public void checkLatestSignalOpportunity() {
        SchedulerLogger.execute("SignalOpportunityCheckJob", this::startAsyncJob);
    }

    @Override
    protected List<Map<String, Object>> fetch() {
        String endpoint = dynamicConfigService.getConfigValue("signal-opportunity", "endpoint");

        URI uri = UriComponentsBuilder
                .fromUriString(kongBaseUrl)
                .path(endpoint)
                .build()
                .toUri();

        ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                uri,
                HttpMethod.GET,
                null,
                new ParameterizedTypeReference<Map<String, Object>>() {}
        );

        Map<String, Object> body = response.getBody();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> content = body != null ? (List<Map<String, Object>>) body.get("content") : null;

        return content;
    }

    @Override
    protected void onSuccess(List<Map<String, Object>> content) {
        OffsetDateTime nowUtc = OffsetDateTime.now(ZoneOffset.UTC);
        int thresholdMinutes = Integer.parseInt(dynamicConfigService.getConfigValue("signal-opportunity", "threshold-minutes"));
        int notificationThresholdMinutes = Integer.parseInt(dynamicConfigService.getConfigValue("signal-opportunity", "notification-threshold-minutes"));

        if (content == null || content.isEmpty()) {
            handleNoData(nowUtc, thresholdMinutes, notificationThresholdMinutes);
            return;
        }

        OffsetDateTime lastDetected = extractLastOpportunityTime(content);
        if (lastDetected == null) {
            handleNoData(nowUtc, thresholdMinutes, notificationThresholdMinutes);
            return;
        }

        if (lastKnownOpportunityTime == null || lastDetected.isAfter(lastKnownOpportunityTime)) {
            log.info("New opportunity detected at {}, resetting alert level", lastDetected);

            lastKnownOpportunityTime = lastDetected;
            lastAlertedLevel = 0;
            lastErrorSentAt = null;

            jobHealthStatusService.upsert(
                    JobType.SIGNAL_OPPORTUNITY,
                    JobStatus.OK,
                    "Signal opportunities detected recently"
            );
            return;
        }

        checkStaleness(nowUtc, thresholdMinutes, notificationThresholdMinutes);
    }

    @Override
    protected void onFinalFailure(Exception e) {

        String errorMsg = "Signal opportunity API failed after retries: " +
                ErrorMessageResolver.resolve(e);

        int notificationThresholdMinutes = Integer.parseInt(
                dynamicConfigService.getConfigValue("signal-opportunity", "notification-threshold-minutes"));

        OffsetDateTime nowUtc = OffsetDateTime.now(ZoneOffset.UTC);

        if (lastErrorSentAt == null ||
                Duration.between(lastErrorSentAt, nowUtc).toMinutes() >= notificationThresholdMinutes) {

            notificationService.sendNotification(errorMsg);
            lastErrorSentAt = nowUtc;
        }

        jobHealthStatusService.upsert(
                JobType.SIGNAL_OPPORTUNITY,
                JobStatus.ERROR,
                errorMsg
        );
    }

    private void checkStaleness(
            OffsetDateTime nowUtc,
            int thresholdMinutes,
            int notificationThresholdMinutes
    ) {
        if (lastKnownOpportunityTime == null) return;

        Duration duration = Duration.between(lastKnownOpportunityTime, nowUtc);
        long minutesPassed = duration.toMinutes();
        long currentLevel = minutesPassed / notificationThresholdMinutes;

        if (minutesPassed >= thresholdMinutes && currentLevel > lastAlertedLevel) {

            String msg = "More than " +
                    DurationFormatter.fromDuration(duration) +
                    " have passed since the last signal opportunity.";

            notificationService.sendNotification(msg);
            lastAlertedLevel = currentLevel;

            jobHealthStatusService.upsert(
                    JobType.SIGNAL_OPPORTUNITY,
                    JobStatus.WARN,
                    msg
            );
        }
        else if (minutesPassed < thresholdMinutes) {

            jobHealthStatusService.upsert(
                    JobType.SIGNAL_OPPORTUNITY,
                    JobStatus.OK,
                    "Signal opportunities within threshold"
            );
        }
    }

    private void handleNoData(
            OffsetDateTime nowUtc,
            int thresholdMinutes,
            int notificationThresholdMinutes
    ) {
        log.warn("Endpoint returned no opportunities");

        if (lastKnownOpportunityTime != null) {
            checkStaleness(nowUtc, thresholdMinutes, notificationThresholdMinutes);
        } else {
            jobHealthStatusService.upsert(
                    JobType.SIGNAL_OPPORTUNITY,
                    JobStatus.WARN,
                    "No signal opportunities returned"
            );
        }
    }

    private OffsetDateTime extractLastOpportunityTime(List<Map<String, Object>> content) {
        try {
            Map<String, Object> latestOpp = content.get(0);
            Object detectedAt = latestOpp.get("detectedAt");

            return detectedAt != null ? OffsetDateTime.parse(detectedAt.toString()) : null;
        } catch (Exception e) {
            log.error("Failed to parse detectedAt timestamp", e);
            return null;
        }
    }
}
