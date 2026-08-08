package com.polygo.health.infrastructure.scheduler;

import com.polygo.health.common.logging.SchedulerLogger;
import com.polygo.health.domain.model.AuditStatus;
import com.polygo.health.domain.enums.JobStatus;
import com.polygo.health.domain.enums.JobType;
import com.polygo.health.infrastructure.persistence.jpa.repository.AuditStatusRepository;
import com.polygo.health.application.service.ConfigParametersService;
import com.polygo.health.application.service.JobHealthStatusService;
import com.polygo.health.application.service.NotificationService;
import com.polygo.health.util.DurationFormatter;
import com.polygo.health.util.ErrorMessageResolver;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
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
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class AuditStatusCheckJob extends AbstractRetryableFetchJob<Void> {

    private final RestTemplate restTemplate;
    private final NotificationService notificationService;
    private final AuditStatusRepository repository;
    private final ConfigParametersService dynamicConfigService;
    private final JobHealthStatusService jobHealthStatusService;
    private final ObjectMapper objectMapper;

    @Value("${services.kong-url}")
    private String kongBaseUrl;

    @Value("${services.endpoints.audit-search}")
    private String auditSearchPath;

    @Scheduled(fixedRateString = "#{@configParametersServiceImpl.getConfigValue('scheduling','fixed-rate-ms') ?: '60000'}",
            initialDelayString = "#{@configParametersServiceImpl.getConfigValue('scheduling','initial-delay-ms') ?: '10000'}")
    public void checkAuditLogs() {
        SchedulerLogger.execute("AuditStatusCheckJob", this::startAsyncJob);
    }

    @Override
    protected Void fetch() {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        String auditEndpoint = dynamicConfigService.getConfigValue("audit", "endpoint");
        List<String> monitoredServices = dynamicConfigService.getConfigList("audit", "monitored-services");

        boolean overallError = false;
        boolean anyFetchFailed = false;
        String overallMessage = "All audit services healthy";

        for (String serviceName : monitoredServices) {
            int thresholdMinutesAudit = Integer.parseInt(dynamicConfigService.getConfigValue("audit", serviceName + "-threshold-minutes"));
            int notificationThresholdMinutes = Integer.parseInt(dynamicConfigService.getConfigValue("audit", serviceName + "-notification-threshold-minutes"));

            boolean hasLogs;
            try {
                // thresholdMinutes parametresini buraya ekledik
                hasLogs = fetchAuditLogs(serviceName, auditEndpoint, thresholdMinutesAudit);
            } catch (Exception e) {
                hasLogs = false;
                overallError = true;
                anyFetchFailed = true;
                String resolved = ErrorMessageResolver.resolve(e);
                overallMessage = String.format("[ERROR] Failed to fetch audit logs for service %s: %s", serviceName, resolved);
                handleFetchError(serviceName, e, now, notificationThresholdMinutes);
            }

            saveOrUpdateStatus(serviceName, hasLogs, now);

            OffsetDateTime threshold = now.minusMinutes(thresholdMinutesAudit);
            List<AuditStatus> downLong = repository.findDownLongerThanForService(serviceName, threshold);
            notifyDownAuditServices(downLong, now, notificationThresholdMinutes, thresholdMinutesAudit);
        }

        List<AuditStatus> allDownLong = monitoredServices.stream()
                .flatMap(serviceName -> {
                    int thresholdMinutesAudit = Integer.parseInt(dynamicConfigService.getConfigValue("audit", serviceName + "-threshold-minutes"));
                    OffsetDateTime threshold = now.minusMinutes(thresholdMinutesAudit);
                    return repository.findDownLongerThanForService(serviceName, threshold).stream();
                })
                .toList();

        boolean anyServiceDown = !allDownLong.isEmpty();

        JobStatus finalStatus;
        if (anyServiceDown) {
            finalStatus = JobStatus.WARN;
            overallMessage = allDownLong.stream()
                    .map(status -> "[WARN] No audit logs for service " + status.getServiceName())
                    .collect(Collectors.joining("; "));
        } else if (overallError) {
            finalStatus = JobStatus.ERROR;
        } else {
            finalStatus = JobStatus.OK;
        }

        jobHealthStatusService.upsert(
                JobType.AUDIT_STATUS,
                finalStatus,
                overallMessage
        );

        if (anyFetchFailed) {
            throw new RuntimeException("Audit fetch failed");
        }

        return null;
    }

    @Override
    protected void onSuccess(Void result) {
    }

    @Override
    protected void onFinalFailure(Exception e) {
        String errorMsg = "[ERROR] Audit Job failed after retries: " + ErrorMessageResolver.resolve(e);
        log.error(errorMsg, e);
        notificationService.sendNotification(errorMsg);
        jobHealthStatusService.upsert(
                JobType.AUDIT_STATUS,
                JobStatus.ERROR,
                errorMsg
        );
    }

    private boolean fetchAuditLogs(String serviceName, String auditEndpoint, int thresholdMinutes) {
        try {
            URI uri = UriComponentsBuilder
                    .fromUriString(kongBaseUrl)
                    .path(auditEndpoint)
                    .path(auditSearchPath)
                    .queryParam("entityType", serviceName)
                    .build()
                    .toUri();

            ResponseEntity<String> response = restTemplate.exchange(uri, HttpMethod.GET, null, String.class);
            if (response.getBody() == null || response.getBody().trim().isEmpty()) return false;

            JsonNode root = objectMapper.readTree(response.getBody());

            JsonNode latestLog = root.has("content") && root.get("content").isArray() && !root.get("content").isEmpty()
                    ? root.get("content").get(0)
                    : (root.isArray() && !root.isEmpty() ? root.get(0) : root);

            if (latestLog.path("occurredAt").isMissingNode()) return false;

            OffsetDateTime logTime = OffsetDateTime.parse(latestLog.path("occurredAt").asText());
            return Duration.between(logTime, OffsetDateTime.now(ZoneOffset.UTC)).toMinutes() <= thresholdMinutes;

        } catch (Exception e) {
            log.error("Error parsing audit logs for {}: {}", serviceName, e.getMessage());
            return false;
        }
    }

    private void handleFetchError(String serviceName, Exception e, OffsetDateTime now, int notificationThresholdMinutes) {
        String resolved = ErrorMessageResolver.resolve(e);
        String msg = String.format("[ERROR] Failed to fetch audit logs for service %s: %s", serviceName, resolved);

        AuditStatus status = repository.findByServiceName(serviceName).orElse(null);

        boolean shouldNotify = status == null || status.getLastNotificationSentAt() == null ||
                Duration.between(status.getLastNotificationSentAt(), now).toMinutes() >= notificationThresholdMinutes;

        if (shouldNotify) {
            notificationService.sendNotification(msg);
            if (status != null) {
                status.setLastNotificationSentAt(now);
                repository.save(status);
            }
        }
    }

    private void saveOrUpdateStatus(String serviceName, boolean hasLogs, OffsetDateTime now) {
        repository.findByServiceName(serviceName)
                .map(status -> repository.saveWithStateUpdate(status, hasLogs))
                .orElseGet(() -> {
                    AuditStatus newStatus = AuditStatus.builder()
                            .serviceName(serviceName)
                            .healthy(hasLogs)
                            .lastStatusChangeAt(now)
                            .build();
                    return repository.save(newStatus);
                });
    }

    private void notifyDownAuditServices(List<AuditStatus> downLong, OffsetDateTime now, int notificationThresholdMinutes, int thresholdMinutesAudit) {
        for (AuditStatus status : downLong) {
            if (status.getLastNotificationSentAt() != null &&
                    Duration.between(status.getLastNotificationSentAt(), now).toMinutes() < notificationThresholdMinutes) {
                continue;
            }
            String msg = String.format("[WARN] No audit logs for service %s in the last %s",
                    status.getServiceName(), DurationFormatter.fromMinutes(thresholdMinutesAudit));
            notificationService.sendNotification(msg);

            status.setLastNotificationSentAt(now);
            repository.save(status);
        }
    }
}
