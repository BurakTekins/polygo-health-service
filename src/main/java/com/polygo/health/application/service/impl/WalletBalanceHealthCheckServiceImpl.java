package com.polygo.health.application.service.impl;

import com.polygo.health.domain.model.WalletBalanceStatus;
import com.polygo.health.domain.enums.JobStatus;
import com.polygo.health.domain.enums.JobType;
import com.polygo.health.infrastructure.persistence.jpa.repository.WalletBalanceStatusRepository;
import com.polygo.health.application.service.WalletBalanceHealthCheckService;
import com.polygo.health.application.service.JobHealthStatusService;
import com.polygo.health.application.service.NotificationService;
import com.polygo.health.util.ErrorMessageResolver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

@Slf4j
@Service
@RequiredArgsConstructor
public class WalletBalanceHealthCheckServiceImpl implements WalletBalanceHealthCheckService {

    private static final int THROTTLE_HOURS = 1;
    private static final int UNHEALTHY_THRESHOLD_HOURS = 2;

    private final WalletBalanceStatusRepository repository;
    private final RestTemplate restTemplate;
    private final NotificationService notificationService;
    private final JobHealthStatusService jobHealthStatusService;

    @Value("${services.kong-url}")
    private String kongBaseUrl;

    @Value("${services.endpoints.wallet-balance-snapshot}")
    private String balanceSnapshotEndpoint;

    private final AtomicReference<OffsetDateTime> lastExecutionTime = new AtomicReference<>();

    @Override
    @Transactional
    public void performHealthCheck(Map<String, Boolean> venueEnabledMap) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

        OffsetDateTime lastExecution = lastExecutionTime.get();
        if (lastExecution != null) {
            Duration sinceLast = Duration.between(lastExecution, now);
            if (sinceLast.toHours() < THROTTLE_HOURS) {
                log.debug("Wallet balance health check skipped - last check was {} minutes ago",
                        sinceLast.toMinutes());
                return;
            }
        }

        log.info("Wallet balance health check started at {}", now);

        boolean hasError = false;
        StringBuilder errorMessage = new StringBuilder();

        try {
            String url = UriComponentsBuilder.fromUriString(kongBaseUrl)
                    .path(balanceSnapshotEndpoint)
                    .build()
                    .toUri()
                    .toString();

            ResponseEntity<List<Map<String, Object>>> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    null,
                    new ParameterizedTypeReference<>() {
                    });

            List<Map<String, Object>> venues = response.getBody();

            if (venues == null || venues.isEmpty()) {
                log.warn("[WARN] No wallet balance data returned from API");
                hasError = true;
                errorMessage.append("No wallet balance data returned");
            } else {
                for (Map<String, Object> venue : venues) {
                    if (processVenue(venue, now, errorMessage, venueEnabledMap)) {
                        hasError = true;
                    }
                }
            }

        } catch (Exception e) {
            log.error("[ERROR] Failed to fetch wallet balance snapshots: {}", ErrorMessageResolver.resolve(e));
            hasError = true;
            errorMessage.append("Failed to fetch wallet snapshots: ")
                    .append(ErrorMessageResolver.resolve(e));
        }

        lastExecutionTime.set(now);

        String statusMessage;
        if (hasError && !errorMessage.isEmpty()) {
            statusMessage = "Unhealthy venues: " +
                    errorMessage.toString().replaceAll(", $", "");
        } else if (hasError) {
            statusMessage = errorMessage.toString();
        } else {
            statusMessage = "All venues have healthy wallet snapshots";
        }

        jobHealthStatusService.upsert(
                JobType.WALLET_BALANCE_STATUS,
                hasError ? JobStatus.ERROR : JobStatus.OK,
                statusMessage);

        log.info("Wallet balance health check completed");
    }

    private boolean processVenue(Map<String, Object> venue, OffsetDateTime now, StringBuilder errorMessage,
            Map<String, Boolean> venueEnabledMap) {
        String venueCode = (String) venue.get("venueCode");
        String lastSnapshotTimeStr = (String) venue.get("lastSnapshotTime");

        if (venueCode == null) {
            log.warn("[WARN] Skipping venue with null venueCode");
            return false;
        }

        OffsetDateTime lastSnapshotTime = null;
        boolean isHealthy = false;

        if (lastSnapshotTimeStr != null) {
            try {
                lastSnapshotTime = OffsetDateTime.parse(lastSnapshotTimeStr);
                Duration sinceLast = Duration.between(lastSnapshotTime, now);
                isHealthy = sinceLast.toHours() < UNHEALTHY_THRESHOLD_HOURS;
            } catch (Exception e) {
                log.warn("[WARN] Failed to parse lastSnapshotTime for {}: {}", venueCode, lastSnapshotTimeStr);
            }
        }

        WalletBalanceStatus status = repository.findByVenueCode(venueCode)
                .orElseGet(() -> WalletBalanceStatus.builder()
                        .venueCode(venueCode)
                        .build());

        status.setLastSnapshotTime(lastSnapshotTime);
        status.setHealthy(isHealthy);
        status.setCheckedAt(now);

        repository.save(status);

        if (!isHealthy) {
            if (Boolean.TRUE.equals(venueEnabledMap.get(venueCode.toLowerCase()))) {
                String message = String.format("[ALERT] Venue %s wallet snapshot is stale. Last snapshot: %s",
                        venueCode,
                        lastSnapshotTime != null ? lastSnapshotTime.toString() : "never");

                log.warn(message);
                notificationService.sendNotification(message);
                errorMessage.append(venueCode).append(", ");
                return true;
            } else {
                log.info("[WARN] Venue {} wallet balance is stale (disabled/passive)",
                        venueCode);
                return false;
            }
        }
        return false;
    }
}
