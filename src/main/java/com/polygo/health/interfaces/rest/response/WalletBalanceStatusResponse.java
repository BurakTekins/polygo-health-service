package com.polygo.health.interfaces.rest.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class WalletBalanceStatusResponse {
    private String venueCode;
    private OffsetDateTime lastSnapshotTime;
    private boolean isHealthy;
    private OffsetDateTime checkedAt;
}
