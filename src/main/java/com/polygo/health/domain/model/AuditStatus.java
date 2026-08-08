package com.polygo.health.domain.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

@Entity
@Table(name = "audit_status", schema = "health")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuditStatus {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "service_name", nullable = false, unique = true)
    private String serviceName;

    @Column(name = "healthy", nullable = false)
    private boolean healthy;

    @Column(name = "last_status_change_at", nullable = false)
    private OffsetDateTime lastStatusChangeAt;

    @Column(name = "last_notification_sent_at")
    private OffsetDateTime lastNotificationSentAt;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    @PrePersist
    public void prePersist() {
        if (lastStatusChangeAt == null)
            lastStatusChangeAt = OffsetDateTime.now(ZoneOffset.UTC);
    }
}
