package com.polygo.health.infrastructure.persistence.jpa.repository;

import com.polygo.health.domain.model.AuditStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

@Repository
public interface AuditStatusRepository extends JpaRepository<AuditStatus, Long> {

    Optional<AuditStatus> findByServiceName(String serviceName);

    @Query("""
        SELECT a
        FROM AuditStatus a
        WHERE a.serviceName = :serviceName
          AND a.healthy = false
          AND a.lastStatusChangeAt < :threshold
    """)
    List<AuditStatus> findDownLongerThanForService(
            String serviceName,
            OffsetDateTime threshold
    );


    default AuditStatus saveWithStateUpdate(AuditStatus status, boolean newHealthy) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

        boolean changed = status.isHealthy() != newHealthy;
        if (changed) {
            status.setHealthy(newHealthy);
            status.setLastStatusChangeAt(now);
        }

        status.setUpdatedAt(now);
        return save(status);
    }

}
