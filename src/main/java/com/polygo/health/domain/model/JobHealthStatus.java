package com.polygo.health.domain.model;

import com.polygo.health.domain.enums.JobStatus;
import com.polygo.health.domain.enums.JobType;
import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

@Entity
@Table(
        name = "job_health_status",
        schema = "health",
        uniqueConstraints = @UniqueConstraint(columnNames = "job_type")
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class JobHealthStatus {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "job_type", nullable = false, updatable = false)
    private JobType jobType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private JobStatus status;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String message;

    @Column(name = "last_checked_at", nullable = false)
    private OffsetDateTime lastCheckedAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    @PreUpdate
    void touch() {
        this.updatedAt = OffsetDateTime.now(ZoneOffset.UTC);
        if (this.lastCheckedAt == null) {
            this.lastCheckedAt = this.updatedAt;
        }
    }
}
