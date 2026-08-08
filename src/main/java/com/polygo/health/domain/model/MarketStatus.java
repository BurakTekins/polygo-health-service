package com.polygo.health.domain.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

@Entity
@Table(name = "market_status", schema = "health",
        uniqueConstraints = @UniqueConstraint(columnNames = {"market_name", "venue_code"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MarketStatus {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "market_name", nullable = false)
    private String marketName;

    @Column(name = "venue_code", nullable = false)
    private String venueCode;

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