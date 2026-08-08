package com.polygo.health.infrastructure.scheduler;

import com.polygo.health.common.logging.SchedulerLogger;
import com.polygo.health.domain.enums.JobStatus;
import com.polygo.health.domain.enums.JobType;
import com.polygo.health.infrastructure.persistence.jpa.repository.JobHealthStatusRepository;
import com.polygo.health.application.service.ConfigParametersService;
import com.polygo.health.application.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

@Slf4j
@Component
@RequiredArgsConstructor
public class DailySummaryJob {

    private final NotificationService notificationService;
    private final JobHealthStatusRepository statusRepository;
    private final ConfigParametersService dynamicConfigService;

    @Scheduled(cron = "#{@configParametersServiceImpl.getConfigValue('daily-summary','cron')}", zone = "Europe/Istanbul")
    public void sendDailySummary() {
        SchedulerLogger.execute("DailySummaryJob", () -> {
            OffsetDateTime jobRunTime = OffsetDateTime.now(ZoneId.of("Europe/Istanbul"));
            StringBuilder summaryMessage = new StringBuilder();
            summaryMessage.append("Daily Summary\n\n")
                    .append("Date & Time: ")
                    .append(jobRunTime.format(DateTimeFormatter.ofPattern("d MMMM HH:mm")))
                    .append("\n\n");

            appendJobStatus(summaryMessage, JobType.SIGNAL_OPPORTUNITY);
            appendJobStatus(summaryMessage, JobType.TRADE_EXECUTION);
            appendJobStatus(summaryMessage, JobType.SERVICE_STATUS);
            appendJobStatus(summaryMessage, JobType.MARKET_FEE_STATUS);
            appendJobStatus(summaryMessage, JobType.MARKET_STATUS);
            appendJobStatus(summaryMessage, JobType.AUDIT_STATUS);

            notificationService.sendDailySummary(summaryMessage.toString());
        });
    }

    private void appendJobStatus(StringBuilder sb, JobType jobType) {
        statusRepository.findByJobType(jobType).ifPresent(status -> {
            JobStatus jobStatus = status.getStatus();
            if (jobStatus != null) {
                switch (jobStatus) {
                    case OK -> sb.append("[OK] ");
                    case ERROR -> sb.append("[ERROR] ");
                }
            }
            sb.append(status.getMessage() != null ? status.getMessage() : "No message").append("\n");
        });
    }
}
