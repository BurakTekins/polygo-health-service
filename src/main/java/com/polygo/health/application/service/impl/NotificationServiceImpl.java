package com.polygo.health.application.service.impl;

import com.polygo.health.interfaces.rest.request.TelegramNotificationRequest;
import com.polygo.health.domain.model.DailySummaryMessage;
import com.polygo.health.domain.model.NotificationMessage;
import com.polygo.health.infrastructure.persistence.jpa.repository.DailySummaryMessageRepository;
import com.polygo.health.infrastructure.persistence.jpa.repository.NotificationMessageRepository;
import com.polygo.health.application.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationServiceImpl implements NotificationService {

    private final NotificationMessageRepository messageRepository;
    private final DailySummaryMessageRepository dailySummaryRepository;
    private final KafkaTemplate<String, TelegramNotificationRequest> kafkaTemplate;

    @Value("${notification.kafka-topic}")
    private String kafkaTopic;

    @Value("${notification.telegram-chat-id}")
    private String telegramChatId;

    @Override
    @Transactional
    public void sendNotification(String message) {
        messageRepository.save(
                NotificationMessage.builder()
                        .message(message)
                        .build()
        );
    }

    @Override
    @Transactional
    public void sendDailySummary(String message) {
        kafkaTemplate.send(
                kafkaTopic,
                TelegramNotificationRequest.builder()
                        .chatId(telegramChatId)
                        .message(message)
                        .type("telegram")
                        .build()
        );

        dailySummaryRepository.save(
                DailySummaryMessage.builder()
                        .message(message)
                        .build()
        );
    }
}
