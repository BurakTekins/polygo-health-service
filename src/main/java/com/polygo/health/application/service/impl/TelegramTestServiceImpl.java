package com.polygo.health.application.service.impl;

import com.polygo.health.application.service.NotificationService;
import com.polygo.health.application.service.TelegramTestService;
import com.polygo.health.interfaces.rest.request.TelegramTestRequest;
import com.polygo.health.interfaces.rest.response.TelegramTestResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;

@Slf4j
@Service
@RequiredArgsConstructor
public class TelegramTestServiceImpl implements TelegramTestService {

    private final NotificationService notificationService;

    @Override
    public TelegramTestResponse sendTestMessage(TelegramTestRequest request) {
        try {
            String customMessage = request != null && request.getMessage() != null && !request.getMessage().isEmpty()
                    ? request.getMessage()
                    : "🧪 Test mesajı ";
            
            String timestamp = OffsetDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'"));
            String message = customMessage + " - [" + timestamp + "]";

            notificationService.sendDailySummary(message);

            log.info("Test Telegram mesajı başarıyla gönderildi: {}", message);

            return TelegramTestResponse.builder()
                    .success(true)
                    .message("Telegram mesajı başarıyla gönderildi")
                    .sentAt(timestamp)
                    .build();
        } catch (Exception e) {
            log.error("Telegram mesajı gönderilirken hata oluştu", e);

            return TelegramTestResponse.builder()
                    .success(false)
                    .message("Telegram mesajı gönderilemedi: " + e.getMessage())
                    .sentAt(OffsetDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'")))
                    .build();
        }
    }
}
