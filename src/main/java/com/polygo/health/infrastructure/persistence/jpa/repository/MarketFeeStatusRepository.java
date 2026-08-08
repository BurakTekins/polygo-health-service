package com.polygo.health.infrastructure.persistence.jpa.repository;

import com.polygo.health.domain.model.MarketFeeStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

@Repository
public interface MarketFeeStatusRepository extends JpaRepository<MarketFeeStatus, Long> {

    Optional<MarketFeeStatus> findByVenueName(String venueName);

    @Query("""
        SELECT s
        FROM MarketFeeStatus s
        WHERE s.healthy = false
          AND s.lastStatusChangeAt < :threshold
    """)
    List<MarketFeeStatus> findStaleLongerThan(@Param("threshold") OffsetDateTime threshold);

    default MarketFeeStatus saveWithStateUpdate(
            MarketFeeStatus status,
            boolean newHealthy,
            OffsetDateTime feeUpdatedAt
    ) {
        boolean stateChanged = status.isHealthy() != newHealthy;

        if (stateChanged) {
            status.setHealthy(newHealthy);
            status.setLastStatusChangeAt(OffsetDateTime.now(ZoneOffset.UTC));
        }

        status.setUpdatedAt(feeUpdatedAt);

        return save(status);
    }

}
