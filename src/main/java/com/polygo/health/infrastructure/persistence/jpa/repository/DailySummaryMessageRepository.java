package com.polygo.health.infrastructure.persistence.jpa.repository;

import com.polygo.health.domain.model.DailySummaryMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;

@Repository
public interface DailySummaryMessageRepository extends JpaRepository<DailySummaryMessage, Long> {

    @Modifying
    @Transactional
    @Query(value = """
        DELETE FROM health.daily_summary_messages
        WHERE id IN (
            SELECT id FROM health.daily_summary_messages
            WHERE created_at < :cutoffDate
            ORDER BY id
            LIMIT :batchSize
        )
    """, nativeQuery = true)
    int deleteOldMessagesInBatch(@Param("cutoffDate") OffsetDateTime cutoffDate,
                                 @Param("batchSize") int batchSize);
}
