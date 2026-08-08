package com.polygo.health.application.service.impl;

import com.polygo.health.domain.model.JobHealthStatus;
import com.polygo.health.domain.enums.JobStatus;
import com.polygo.health.domain.enums.JobType;
import com.polygo.health.infrastructure.persistence.jpa.repository.JobHealthStatusRepository;
import com.polygo.health.application.service.JobHealthStatusService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

@Service
@RequiredArgsConstructor
public class JobHealthStatusServiceImpl implements JobHealthStatusService {

    private final JobHealthStatusRepository repository;

    @Override
    @Transactional
    public void upsert(JobType type, JobStatus status, String message) {

        JobHealthStatus entity = repository
                .findByJobType(type)
                .orElseGet(() -> JobHealthStatus.builder()
                        .jobType(type)
                        .build());

        entity.setStatus(status);
        entity.setMessage(message);
        entity.setLastCheckedAt(OffsetDateTime.now(ZoneOffset.UTC));

        repository.save(entity);
    }
}
