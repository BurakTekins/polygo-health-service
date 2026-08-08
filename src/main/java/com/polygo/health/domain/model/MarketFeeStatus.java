package com.polygo.health.domain.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

@Entity
@Table(name = "market_fee_status", schema = "health")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MarketFeeStatus {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "venue_name", nullable = false, unique = true)
    private String venueName;

    @Column(name = "healthy", nullable = false)
    private boolean healthy;

    @Column(name = "last_status_change_at", nullable = false)
    private OffsetDateTime lastStatusChangeAt;

    @Column(name = "last_notification_sent_at")
    private OffsetDateTime lastNotificationSentAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
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
