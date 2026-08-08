package com.polygo.health.interfaces.rest.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TelegramTestResponse {
    private boolean success;
    private String message;
    private String sentAt;
}
