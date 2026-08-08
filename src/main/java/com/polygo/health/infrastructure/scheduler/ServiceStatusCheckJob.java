package com.polygo.health.infrastructure.scheduler;

import com.polygo.health.common.logging.SchedulerLogger;
import com.polygo.health.domain.model.ServiceStatus;
import com.polygo.health.domain.enums.JobStatus;
import com.polygo.health.domain.enums.JobType;
import com.polygo.health.infrastructure.persistence.jpa.repository.ServiceStatusRepository;
import com.polygo.health.application.service.ConfigParametersService;
import com.polygo.health.application.service.JobHealthStatusService;
import com.polygo.health.application.service.NotificationService;
import com.polygo.health.util.DurationFormatter;
import com.polygo.health.util.ErrorMessageResolver;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class ServiceStatusCheckJob extends AbstractRetryableFetchJob<Void> {

    private final ServiceStatusRepository repository;
    private final RestTemplate restTemplate;
    private final NotificationService notificationService;
    private final ConfigParametersService dynamicConfigService;
    private final JobHealthStatusService jobHealthStatusService;

    @Value("${services.kong-url}")
    private String kongBaseUrl;

    @Value("${services.routes.bot}")
    private String botRoute;

    @Value("${services.routes.market-data}")
    private String marketDataRoute;

    @Value("${services.routes.notification}")
    private String notificationRoute;

    @Value("${services.routes.strategy}")
    private String strategyRoute;

    @Value("${services.routes.wallet}")
    private String walletRoute;

    @Value("${services.routes.audit}")
    private String auditRoute;

    @Scheduled(fixedRateString = "#{@configParametersServiceImpl.getConfigValue('scheduling','fixed-rate-ms') ?: '60000'}",
            initialDelayString = "#{@configParametersServiceImpl.getConfigValue('scheduling','initial-delay-ms') ?: '10000'}")
    public void checkServices() {
        SchedulerLogger.execute("ServiceStatusCheckJob", this::startAsyncJob);
    }

    @Override
    @Transactional
    protected Void fetch() {
        boolean overallError = false;
        StringBuilder overallMessage = new StringBuilder();

        String serviceUrlsRaw = dynamicConfigService.getConfigValue("health-check", "service-urls");
        List<String> endpoints = Arrays.asList(serviceUrlsRaw.split(","));

        List<String> serviceUrls = endpoints.stream()
                .map(e -> UriComponentsBuilder.fromUriString(kongBaseUrl)
                        .path(e)
                        .build()
                        .toUri()
                        .toString())
                .toList();

        int thresholdMinutes = Integer
                .parseInt(dynamicConfigService.getConfigValue("health-check", "threshold-minutes"));
        int notificationThresholdMinutes = Integer
                .parseInt(dynamicConfigService.getConfigValue("health-check", "notification-threshold-minutes"));

        for (String url : serviceUrls) {
            String name = extractServiceName(url);

            try {
                Map<String, Object> response = restTemplate.getForObject(url, Map.class);
                boolean isUp = response != null && "UP".equalsIgnoreCase((String) response.get("status"));

                repository.findByServiceName(name)
                        .map(s -> repository.saveWithStateUpdate(s, isUp))
                        .orElseGet(() -> {
                            ServiceStatus newStatus = ServiceStatus.builder()
                                    .serviceName(name)
                                    .healthy(isUp)
                                    .lastStatusChangeAt(OffsetDateTime.now(ZoneOffset.UTC))
                                    .build();
                            return repository.save(newStatus);
                        });

                if (!isUp) {
                    overallError = true;
                    overallMessage.append("Service ").append(name).append(" is DOWN; ");
                }

            } catch (Exception e) {
                log.warn("[ERROR] {} is unreachable: {}", name, ErrorMessageResolver.resolve(e));
                overallError = true;
                overallMessage.append("Service ").append(name).append(" unreachable; ");

                repository.findByServiceName(name)
                        .map(s -> repository.saveWithStateUpdate(s, false))
                        .orElseGet(() -> {
                            ServiceStatus newStatus = ServiceStatus.builder()
                                    .serviceName(name)
                                    .healthy(false)
                                    .lastStatusChangeAt(OffsetDateTime.now(ZoneOffset.UTC))
                                    .build();
                            return repository.save(newStatus);
                        });
            }
        }

        notifyDownServices(thresholdMinutes, notificationThresholdMinutes);

        jobHealthStatusService.upsert(
                JobType.SERVICE_STATUS,
                overallError ? JobStatus.ERROR : JobStatus.OK,
                overallError ? overallMessage.toString() : "All services are healthy");

        return null;
    }

    @Override
    protected void onSuccess(Void result) {
    }

    @Override
    protected void onFinalFailure(Exception e) {
        String msg = "[ERROR] Service Health Check failed critically: " + ErrorMessageResolver.resolve(e);
        log.error(msg, e);

        jobHealthStatusService.upsert(
                JobType.SERVICE_STATUS,
                JobStatus.ERROR,
                msg
        );
    }

    private String extractServiceName(String url) {
        if (url.contains(botRoute)) return "bot-service";
        if (url.contains(marketDataRoute)) return "market-data-service";
        if (url.contains(notificationRoute)) return "notification-service";
        if (url.contains(strategyRoute)) return "strategy-service";
        if (url.contains(walletRoute)) return "wallet-service";
        if (url.contains(auditRoute)) return "audit-service";
        return "unknown";
    }

    private void notifyDownServices(int thresholdMinutes, int notificationThresholdMinutes) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        OffsetDateTime threshold = now.minusMinutes(thresholdMinutes);
        List<ServiceStatus> downLong = repository.findDownLongerThan(threshold);

        for (ServiceStatus s : downLong) {
            if (s.getLastNotificationSentAt() != null &&
                    Duration.between(s.getLastNotificationSentAt(), now).toMinutes() < notificationThresholdMinutes) {
                continue;
            }

            Duration downDuration = Duration.between(s.getLastStatusChangeAt(), now);

            log.warn("[ALERT] {} has been DOWN for {}", s.getServiceName(), DurationFormatter.fromDuration(downDuration));
            notificationService.sendNotification(
                    "[ALERT] " + s.getServiceName() + " has been down for " + DurationFormatter.fromDuration(downDuration) + "."
            );

            s.setLastNotificationSentAt(now);
            repository.save(s);
        }
    }
}
