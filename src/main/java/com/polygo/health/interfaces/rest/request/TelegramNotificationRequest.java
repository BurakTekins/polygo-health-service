package com.polygo.health.interfaces.rest.request;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TelegramNotificationRequest {
    private String chatId;
    private String message;
    private String type;
}
