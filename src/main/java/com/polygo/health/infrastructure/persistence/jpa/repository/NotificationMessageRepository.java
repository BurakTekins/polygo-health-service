package com.polygo.health.infrastructure.persistence.jpa.repository;

import com.polygo.health.domain.model.NotificationMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;

@Repository
public interface NotificationMessageRepository
        extends JpaRepository<NotificationMessage, Long> {

    List<NotificationMessage> findBySentAtIsNullOrderByCreatedAtAsc();

    List<NotificationMessage> findByCreatedAtBetween(
            OffsetDateTime start,
            OffsetDateTime end
    );

    NotificationMessage findTopByCreatedAtBetweenOrderByCreatedAtDesc(
            OffsetDateTime start,
            OffsetDateTime end
    );

    @Query("""
        SELECT MAX(n.createdAt)
        FROM NotificationMessage n
        WHERE n.message LIKE '%stale markets%'
    """)
    OffsetDateTime findLastStaleMarketsNotificationTime();

    @Modifying
    @Transactional
    @Query(value = """
        DELETE FROM health.notification_messages
        WHERE id IN (
            SELECT id FROM health.notification_messages
            WHERE created_at < :cutoffDate
            ORDER BY id
            LIMIT :batchSize
        )
    """, nativeQuery = true)
    int deleteOldMessagesInBatch(@Param("cutoffDate") OffsetDateTime cutoffDate,
                                 @Param("batchSize") int batchSize);
}
