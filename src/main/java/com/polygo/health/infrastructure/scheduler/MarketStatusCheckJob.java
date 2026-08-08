package com.polygo.health.infrastructure.scheduler;

import com.polygo.health.common.logging.SchedulerLogger;
import com.polygo.health.domain.model.MarketStatus;
import com.polygo.health.domain.enums.JobStatus;
import com.polygo.health.domain.enums.JobType;
import com.polygo.health.infrastructure.persistence.jpa.repository.NotificationMessageRepository;
import com.polygo.health.infrastructure.persistence.jpa.repository.MarketStatusRepository;
import com.polygo.health.application.service.ConfigParametersService;
import com.polygo.health.application.service.JobHealthStatusService;
import com.polygo.health.application.service.NotificationService;
import com.polygo.health.util.ErrorMessageResolver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationContext;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import org.springframework.transaction.annotation.Transactional;
import java.net.URI;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class MarketStatusCheckJob extends AbstractRetryableFetchJob<List<Map<String, Object>>> {

    private final RestTemplate restTemplate;
    private final NotificationService notificationService;
    private final MarketStatusRepository repository;
    private final NotificationMessageRepository notificationMessageRepository;
    private final ConfigParametersService dynamicConfigService;
    private final JobHealthStatusService jobHealthStatusService;
    private final ApplicationContext applicationContext;

    @Value("${services.kong-url}")
    private String kongBaseUrl;

    @Scheduled(
            fixedRateString = "#{@configParametersServiceImpl.getConfigValue('scheduling','fixed-rate-ms')}",
            initialDelayString = "#{@configParametersServiceImpl.getConfigValue('scheduling','initial-delay-ms')}"
    )
    public void checkMarketUpdates() {
        SchedulerLogger.execute("MarketStatusCheckJob", this::startAsyncJob);
    }

    @Override
    protected List<Map<String, Object>> fetch() {
        String marketsEndpoint = dynamicConfigService.getConfigValue("market-update", "endpoint");

        URI uri = UriComponentsBuilder
                .fromUriString(kongBaseUrl)
                .path(marketsEndpoint)
                .build()
                .toUri();

        ResponseEntity<Map<String, Object>> response =
                restTemplate.exchange(
                        uri,
                        HttpMethod.GET,
                        null,
                        new ParameterizedTypeReference<Map<String, Object>>() {}
                );

        Map<String, Object> body = response.getBody();

        if (body != null && body.containsKey("content")) {
            Object contentObj = body.get("content");
            if (contentObj instanceof List<?>) {
                return (List<Map<String, Object>>) contentObj;
            }
        }

        log.warn("[WARN] API returned a valid response but 'content' field was missing or empty.");
        return Collections.emptyList();
    }

    @Override
    protected void onSuccess(List<Map<String, Object>> markets) {

        if (markets == null || markets.isEmpty()) {
            handleNoData();
            return;
        }

        List<Map<String, Object>> enabledMarkets = markets.stream()
                .filter(s -> Boolean.parseBoolean(String.valueOf(s.getOrDefault("enabled", "false"))))
                .toList();

        if (enabledMarkets.isEmpty()) {
            jobHealthStatusService.upsert(
                    JobType.MARKET_STATUS,
                    JobStatus.OK,
                    "No enabled markets configured"
            );
            return;
        }

        processMarkets(enabledMarkets);
    }

    @Override
    protected void onFinalFailure(Exception e) {
        String errorMsg = "[ERROR] Critical: Failed to fetch markets after retries: " + ErrorMessageResolver.resolve(e);
        log.error(errorMsg, e);
        notificationService.sendNotification(errorMsg);

        jobHealthStatusService.upsert(
                JobType.MARKET_STATUS,
                JobStatus.ERROR,
                errorMsg
        );
    }

    private void handleNoData() {
        String errorMsg = "[ERROR] Critical: No markets returned from market-data-service (list is empty).";
        log.error(errorMsg);
        notificationService.sendNotification(errorMsg);
        jobHealthStatusService.upsert(JobType.MARKET_STATUS, JobStatus.ERROR, errorMsg);
    }

    private void processMarkets(List<Map<String, Object>> enabledMarkets) {

        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

        int thresholdMinutes = Integer.parseInt(
                dynamicConfigService.getConfigValue("market-update", "threshold-minutes"));

        int notificationThresholdMinutes = Integer.parseInt(
                dynamicConfigService.getConfigValue("market-update", "notification-threshold-minutes"));

        Map<String, MarketStatus> statusMap = repository.findAll()
                .stream()
                .collect(Collectors.toMap(
                        s -> s.getMarketName() + ":" + s.getVenueCode(),
                        Function.identity(),
                        (a, b) -> a
                ));

        List<MarketStatus> entitiesToSave = new ArrayList<>();
        List<MarketStatus> staleToNotify = new ArrayList<>();

        boolean anyStale = false;

        for (Map<String, Object> marketData : enabledMarkets) {

            String marketName = String.valueOf(
                    marketData.getOrDefault("slug",
                            marketData.getOrDefault("ticker", "unknown"))
            );

            String venueCode = String.valueOf(
                    marketData.getOrDefault("venueCode",
                            marketData.getOrDefault("venue", "POLYMARKET"))
            );

            Object updatedAtRaw = marketData.get("updatedAt");
            if (updatedAtRaw == null) {
                continue;
            }

            try {
                OffsetDateTime endpointUpdatedAt =
                        OffsetDateTime.parse(updatedAtRaw.toString());

                long minutes =
                        Duration.between(endpointUpdatedAt, now).toMinutes();

                boolean isHealthy = minutes < thresholdMinutes;

                String key = marketName + ":" + venueCode;

                MarketStatus status = statusMap.getOrDefault(
                        key,
                        MarketStatus.builder()
                                .marketName(marketName)
                                .venueCode(venueCode)
                                .build()
                );

                status.setUpdatedAt(endpointUpdatedAt);

                if (status.isHealthy() != isHealthy) {
                    status.setLastStatusChangeAt(now);
                }

                status.setHealthy(isHealthy);
                entitiesToSave.add(status);

                if (!isHealthy) {
                    anyStale = true;
                    staleToNotify.add(status);
                }

            } catch (Exception e) {
                log.error("Error parsing market {}", marketName, e);
            }
        }

        applicationContext
                .getBean(MarketStatusCheckJob.class)
                .persistStatuses(entitiesToSave);

        sendNotifications(staleToNotify, now, notificationThresholdMinutes);

        jobHealthStatusService.upsert(
                JobType.MARKET_STATUS,
                anyStale ? JobStatus.WARN : JobStatus.OK,
                anyStale ? "Stale markets detected" : "All markets are healthy"
        );
    }

    private void sendNotifications(
            List<MarketStatus> staleToNotify,
            OffsetDateTime now,
            int notificationThresholdMinutes
    ) {

        int staleCount = staleToNotify.size();

        if (staleCount == 0) {
            return;
        }

        OffsetDateTime lastNotificationTime =
                notificationMessageRepository.findLastStaleMarketsNotificationTime();

        if (lastNotificationTime != null) {
            long minutesSinceLast =
                    Duration.between(lastNotificationTime, now).toMinutes();

            if (minutesSinceLast < notificationThresholdMinutes) {
                log.info(
                        "Skipping notification. Last stale notification was {} minutes ago",
                        minutesSinceLast
                );
                return;
            }
        }

        if (staleCount <= 5) {

            for (MarketStatus status : staleToNotify) {
                String msg = String.format(
                        "[WARN] Market %s on %s is stale",
                        status.getMarketName(),
                        status.getVenueCode()
                );
                notificationService.sendNotification(msg);
                log.warn(msg);
                status.setLastNotificationSentAt(now);
            }

        } else {

            Map<String, Long> byVenue = staleToNotify.stream()
                    .collect(Collectors.groupingBy(
                            MarketStatus::getVenueCode,
                            Collectors.counting()
                    ));

            for (Map.Entry<String, Long> entry : byVenue.entrySet()) {
                String msg = String.format(
                        "[WARN] %s: %d stale markets",
                        entry.getKey(),
                        entry.getValue()
                );
                notificationService.sendNotification(msg);
                log.warn(msg);
            }

            staleToNotify.forEach(s -> s.setLastNotificationSentAt(now));
        }

        repository.saveAll(staleToNotify);
    }

    @Transactional
    public void persistStatuses(List<MarketStatus> entities) {
        repository.saveAll(entities);
    }
}
