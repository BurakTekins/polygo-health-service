package com.polygo.health.infrastructure.persistence.jpa.repository;

import com.polygo.health.domain.model.JobHealthStatus;
import com.polygo.health.domain.enums.JobType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface JobHealthStatusRepository
        extends JpaRepository<JobHealthStatus, Long> {

    Optional<JobHealthStatus> findByJobType(JobType jobType);
}
