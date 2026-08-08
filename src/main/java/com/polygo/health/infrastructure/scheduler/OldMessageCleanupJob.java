package com.polygo.health.infrastructure.scheduler;

import com.polygo.health.common.logging.SchedulerLogger;
import com.polygo.health.infrastructure.persistence.jpa.repository.DailySummaryMessageRepository;
import com.polygo.health.infrastructure.persistence.jpa.repository.NotificationMessageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

@Slf4j
@Component
@RequiredArgsConstructor
public class OldMessageCleanupJob {

    private final NotificationMessageRepository notificationMessageRepository;
    private final DailySummaryMessageRepository dailySummaryMessageRepository;

    @Value("${cleanup.message.retention-days:30}")
    private int retentionDays;

    @Value("${cleanup.message.batch-size:10000}")
    private int batchSize;


    @Scheduled(cron = "${cleanup.message.cron:0 0 * * * *}")
    public void cleanupOldMessages() {
        SchedulerLogger.execute("OldMessageCleanupJob", () -> {
            OffsetDateTime cutoffDate = OffsetDateTime.now(ZoneOffset.UTC).minusDays(retentionDays);
            log.info("Deleting messages older than {} (retention: {} days)", cutoffDate, retentionDays);

            int totalNotificationDeleted = cleanupNotificationMessages(cutoffDate);
            int totalDailySummaryDeleted = cleanupDailySummaryMessages(cutoffDate);

            log.info("Total deleted: notification_messages={}, daily_summary_messages={}",
                    totalNotificationDeleted, totalDailySummaryDeleted);
        });
    }
    private int cleanupNotificationMessages(OffsetDateTime cutoffDate) {
        int totalDeleted = 0;
        int deletedInBatch;

        do {
            deletedInBatch = notificationMessageRepository.deleteOldMessagesInBatch(cutoffDate, batchSize);
            totalDeleted += deletedInBatch;

            if (deletedInBatch > 0) {
                log.debug("[OldMessageCleanupJob] Deleted {} notification_messages in batch, total so far: {}",
                        deletedInBatch, totalDeleted);
            }
        } while (deletedInBatch > 0);

        if (totalDeleted > 0) {
            log.info("[OldMessageCleanupJob] Deleted {} old records from notification_messages", totalDeleted);
        }

        return totalDeleted;
    }


    private int cleanupDailySummaryMessages(OffsetDateTime cutoffDate) {
        int totalDeleted = 0;
        int deletedInBatch;

        do {
            deletedInBatch = dailySummaryMessageRepository.deleteOldMessagesInBatch(cutoffDate, batchSize);
            totalDeleted += deletedInBatch;

            if (deletedInBatch > 0) {
                log.debug("[OldMessageCleanupJob] Deleted {} daily_summary_messages in batch, total so far: {}",
                        deletedInBatch, totalDeleted);
            }
        } while (deletedInBatch > 0);

        if (totalDeleted > 0) {
            log.info("[OldMessageCleanupJob] Deleted {} old records from daily_summary_messages", totalDeleted);
        }

        return totalDeleted;
    }
}
