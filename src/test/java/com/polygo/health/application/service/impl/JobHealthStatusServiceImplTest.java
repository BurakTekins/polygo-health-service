package com.polygo.health.application.service.impl;

import com.polygo.health.domain.enums.*;
import com.polygo.health.domain.model.JobHealthStatus;
import com.polygo.health.infrastructure.persistence.jpa.repository.JobHealthStatusRepository;
import org.junit.jupiter.api.Test;
import java.util.Optional;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class JobHealthStatusServiceImplTest {
    private final JobHealthStatusRepository repository = mock(JobHealthStatusRepository.class);
    private final JobHealthStatusServiceImpl service = new JobHealthStatusServiceImpl(repository);

    @Test
    void firstCheckCreatesStatusForTheRequestedJob() {
        when(repository.findByJobType(JobType.SERVICE_STATUS)).thenReturn(Optional.empty());
        service.upsert(JobType.SERVICE_STATUS, JobStatus.ERROR, "offline");
        var saved = org.mockito.ArgumentCaptor.forClass(JobHealthStatus.class);
        verify(repository).save(saved.capture());
        assertThat(saved.getValue().getJobType()).isEqualTo(JobType.SERVICE_STATUS);
        assertThat(saved.getValue().getStatus()).isEqualTo(JobStatus.ERROR);
        assertThat(saved.getValue().getMessage()).isEqualTo("offline");
        assertThat(saved.getValue().getLastCheckedAt().getOffset()).isEqualTo(java.time.ZoneOffset.UTC);
    }

    @Test
    void recoveryUpdatesExistingRowInsteadOfCreatingDuplicate() {
        var existing = JobHealthStatus.builder().id(7L).jobType(JobType.SERVICE_STATUS)
                .status(JobStatus.ERROR).message("offline").build();
        when(repository.findByJobType(JobType.SERVICE_STATUS)).thenReturn(Optional.of(existing));
        service.upsert(JobType.SERVICE_STATUS, JobStatus.OK, "recovered");
        verify(repository).save(existing);
        assertThat(existing.getId()).isEqualTo(7L);
        assertThat(existing.getStatus()).isEqualTo(JobStatus.OK);
        assertThat(existing.getMessage()).isEqualTo("recovered");
    }

    @Test
    void readFailurePropagatesWithoutAttemptingWrite() {
        var failure = new IllegalStateException("database unavailable");
        when(repository.findByJobType(any())).thenThrow(failure);
        assertThatThrownBy(() -> service.upsert(JobType.SERVICE_STATUS, JobStatus.OK, "ok"))
                .isSameAs(failure);
        verify(repository, never()).save(any());
    }
}
