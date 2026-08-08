package com.polygo.health.infrastructure.scheduler;

import com.polygo.health.common.logging.SchedulerLogger;
import com.polygo.health.domain.model.MarketFeeStatus;
import com.polygo.health.domain.enums.JobStatus;
import com.polygo.health.domain.enums.JobType;
import com.polygo.health.infrastructure.persistence.jpa.repository.MarketFeeStatusRepository;
import com.polygo.health.application.service.ConfigParametersService;
import com.polygo.health.application.service.WalletBalanceHealthCheckService;
import com.polygo.health.application.service.JobHealthStatusService;
import com.polygo.health.application.service.NotificationService;
import com.polygo.health.util.DurationFormatter;
import com.polygo.health.util.ErrorMessageResolver;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
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
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class MarketFeeStatusCheckJob extends AbstractRetryableFetchJob<Void> {

    private final RestTemplate restTemplate;
    private final NotificationService notificationService;
    private final MarketFeeStatusRepository repository;
    private final ConfigParametersService dynamicConfigService;
    private final JobHealthStatusService jobHealthStatusService;
    private final WalletBalanceHealthCheckService walletBalanceHealthCheckService;

    @Value("${services.kong-url}")
    private String kongBaseUrl;

    @Scheduled(fixedRateString = "#{@configParametersServiceImpl.getConfigValue('scheduling','fixed-rate-ms') ?: '60000'}",
            initialDelayString = "#{@configParametersServiceImpl.getConfigValue('scheduling','initial-delay-ms') ?: '10000'}")
    public void checkVenueFees() {
        SchedulerLogger.execute("MarketFeeStatusCheckJob", this::startAsyncJob);
    }

    @Override
    @Transactional
    protected Void fetch() {
        String venuesEndpoint = dynamicConfigService.getConfigValue("market-fee", "venues-endpoint");
        String marketFeeEndpoint = dynamicConfigService.getConfigValue("market-fee", "market-fee-endpoint");
        int thresholdMinutes = Integer.parseInt(dynamicConfigService.getConfigValue("market-fee", "threshold-minutes"));
        int notificationThresholdMinutes = Integer.parseInt(dynamicConfigService.getConfigValue("market-fee", "notification-threshold-minutes"));

        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

        URI venuesUri = UriComponentsBuilder
                .fromUriString(kongBaseUrl)
                .path(venuesEndpoint)
                .build()
                .toUri();

        List<Map<String, Object>> venues = restTemplate.exchange(
                venuesUri,
                HttpMethod.GET,
                null,
                new ParameterizedTypeReference<List<Map<String, Object>>>() {}
        ).getBody();

        if (venues == null || venues.isEmpty()) {
            handleNoVenues();
            return null;
        }

        boolean overallError = false;
        String overallMessage = "";

        List<Map<String, Object>> activeVenues = venues.stream()
                .filter(ex -> Boolean.TRUE.equals(ex.get("enabled")))
                .toList();

        List<Map<String, Object>> passiveVenues = venues.stream()
                .filter(ex -> !Boolean.TRUE.equals(ex.get("enabled")))
                .toList();

        try {
            for (Map<String, Object> venue : activeVenues) {
                processVenue(venue, marketFeeEndpoint, now, thresholdMinutes, notificationThresholdMinutes, true);
            }

            for (Map<String, Object> venue : passiveVenues) {
                processVenue(venue, marketFeeEndpoint, now, thresholdMinutes, notificationThresholdMinutes, false);
            }
        } catch (Exception e) {
            overallError = true;
            overallMessage = "Error processing venues: " + ErrorMessageResolver.resolve(e);
            log.error(overallMessage, e);
        }

        jobHealthStatusService.upsert(
                JobType.MARKET_FEE_STATUS,
                overallError ? JobStatus.ERROR : JobStatus.OK,
                overallError ? overallMessage : "Polymarket venue fees checked successfully"
        );

        Map<String, Boolean> venueEnabledMap = venues.stream()
                .collect(Collectors.toMap(
                        ex -> ((String) ex.get("code")).toLowerCase(),
                        ex -> Boolean.TRUE.equals(ex.get("enabled")),
                        (existing, replacement) -> existing));

        walletBalanceHealthCheckService.performHealthCheck(venueEnabledMap);

        return null;
    }

    @Override
    protected void onSuccess(Void result) {
    }

    @Override
    protected void onFinalFailure(Exception e) {
        String msg = "[ERROR] Failed to fetch venues from Kong after retries: " + ErrorMessageResolver.resolve(e);
        log.error(msg);
        notificationService.sendNotification(msg);

        jobHealthStatusService.upsert(
                JobType.MARKET_FEE_STATUS,
                JobStatus.ERROR,
                msg
        );
    }

    private void handleNoVenues() {
        String msg = "[WARN] No venues returned from Kong";
        log.warn(msg);
        notificationService.sendNotification(msg);

        jobHealthStatusService.upsert(
                JobType.MARKET_FEE_STATUS,
                JobStatus.ERROR,
                msg
        );
    }

    private void processVenue(Map<String, Object> venue,
                                 String marketFeeEndpoint,
                                 OffsetDateTime now,
                                 int thresholdMinutes,
                                 int notificationThresholdMinutes,
                                 boolean sendNotifications) {

        String displayName = (String) venue.getOrDefault("name", "unknown");
        String venueCode = ((String) venue.get("code")).toLowerCase();

        URI feeUri = UriComponentsBuilder
                .fromUriString(kongBaseUrl)
                .path(marketFeeEndpoint)
                .queryParam("venue", venueCode)
                .build()
                .toUri();

        log.debug("Fetching fees for venue='{}' (API id='{}') using URI: {}", displayName, venueCode, feeUri);

        try {
            List<Map<String, Object>> fees = restTemplate.exchange(
                    feeUri, HttpMethod.GET, null,
                    new ParameterizedTypeReference<List<Map<String, Object>>>() {
                    }).getBody();

            if (fees == null || fees.isEmpty()) {
                if (Boolean.TRUE.equals(venue.get("enabled"))) {
                    log.info("Venue '{}' is enabled but returned no active markets (empty list). Marking as HEALTHY.", displayName);

                    MarketFeeStatus status = repository.findByVenueName(displayName)
                            .orElse(MarketFeeStatus.builder()
                                    .venueName(displayName)
                                    .lastStatusChangeAt(now)
                                    .build());

                    status.setHealthy(true);
                    repository.save(status);
                }
                return;
            }

            for (Map<String, Object> fee : fees) {
                Object updatedAtRaw = fee.get("updatedAt");
                if (updatedAtRaw == null)
                    continue;

                try {
                    OffsetDateTime updatedAt = OffsetDateTime.parse(updatedAtRaw.toString());
                    Duration diff = Duration.between(updatedAt, now);
                    boolean healthy = diff.toMinutes() < thresholdMinutes;

                    MarketFeeStatus status = repository.findByVenueName(displayName)
                            .orElse(MarketFeeStatus.builder()
                                    .venueName(displayName)
                                    .healthy(healthy)
                                    .lastStatusChangeAt(now)
                                    .build());

                    repository.saveWithStateUpdate(status, healthy, updatedAt);

                    if (!healthy && sendNotifications) {
                        if (status.getLastNotificationSentAt() == null ||
                                Duration.between(status.getLastNotificationSentAt(), now)
                                        .toMinutes() >= notificationThresholdMinutes) {

                            String msg = String.format(
                                    "[WARN] Venue fee for %s is stale — no update for %s",
                                    status.getVenueName(),
                                    DurationFormatter.fromDuration(diff));
                            log.warn(msg);
                            notificationService.sendNotification(msg);
                            status.setLastNotificationSentAt(now);
                            repository.save(status);
                        }
                    }

                } catch (Exception parseEx) {
                    if (sendNotifications) {
                        String msg = "[WARN] Failed to parse updatedAt for venue " + displayName + ": "
                                + ErrorMessageResolver.resolve(parseEx);
                        log.warn(msg);
                        notificationService.sendNotification(msg);
                    }
                }
            }

        } catch (Exception e) {
            if (sendNotifications) {
                String msg = "[ERROR] Failed to fetch venue fees for " + displayName + ": "
                        + ErrorMessageResolver.resolve(e);
                log.error(msg);
                notificationService.sendNotification(msg);
            }
        }
    }
}
