package com.polygo.health.infrastructure.persistence.jpa.repository;

import com.polygo.health.domain.model.ServiceStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

@Repository
public interface ServiceStatusRepository extends JpaRepository<ServiceStatus, Long> {

    Optional<ServiceStatus> findByServiceName(String serviceName);

    List<ServiceStatus> findByHealthyFalse();

    @Query("""
        SELECT s
        FROM ServiceStatus s
        WHERE s.healthy = false
          AND s.lastStatusChangeAt < :threshold
    """)
    List<ServiceStatus> findDownLongerThan(@Param("threshold") OffsetDateTime threshold);

    default ServiceStatus saveWithStateUpdate(ServiceStatus status, boolean newHealthy) {

        boolean stateChanged = status.isHealthy() != newHealthy;
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

        if (stateChanged) {
            status.setHealthy(newHealthy);
            status.setLastStatusChangeAt(now);

            return save(status);
        }

        status.setUpdatedAt(now);

        return status;
    }
}
