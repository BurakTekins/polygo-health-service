package com.polygo.health.application.service;

import com.polygo.health.domain.enums.JobStatus;
import com.polygo.health.domain.enums.JobType;

public interface JobHealthStatusService {

    void upsert(JobType type, JobStatus status, String message);

}
