package com.polygo.health.interfaces.rest.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class StatusResponse {
    private Long id;
    private String name;
    private boolean healthy;
    private OffsetDateTime updatedAt;
    private OffsetDateTime lastStatusChangeAt;
    private OffsetDateTime lastNotificationSentAt;
}
