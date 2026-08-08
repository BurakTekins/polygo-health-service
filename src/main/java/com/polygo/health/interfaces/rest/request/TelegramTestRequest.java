package com.polygo.health.interfaces.rest.request;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TelegramTestRequest {
    private String message;
}
