package com.polygo.health.infrastructure.persistence.jpa.repository;

import com.polygo.health.domain.model.MarketStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

@Repository
public interface MarketStatusRepository extends JpaRepository<MarketStatus, Long> {

    @Deprecated
    Optional<MarketStatus> findByMarketName(String marketName);

    List<MarketStatus> findByHealthyFalse();

    Optional<MarketStatus> findByMarketNameAndVenueCode(String marketName, String venueCode);

    @Query("""
        SELECT s
        FROM MarketStatus s
        WHERE s.healthy = false
          AND s.lastStatusChangeAt < :threshold
    """)
    List<MarketStatus> findStaleLongerThan(@Param("threshold") OffsetDateTime threshold);

    default MarketStatus saveWithStateUpdate(
            MarketStatus status,
            boolean newHealthy,
            OffsetDateTime marketUpdatedAt
    ) {
        boolean stateChanged = status.isHealthy() != newHealthy;

        if (stateChanged) {
            status.setHealthy(newHealthy);
            status.setLastStatusChangeAt(OffsetDateTime.now(ZoneOffset.UTC));
        }

        status.setUpdatedAt(marketUpdatedAt);

        return save(status);
    }
}