package com.polygo.health.application.service.impl;

import com.polygo.health.interfaces.rest.request.TelegramNotificationRequest;
import com.polygo.health.domain.model.NotificationMessage;
import com.polygo.health.infrastructure.persistence.jpa.repository.NotificationMessageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationFlushService {

    private final NotificationMessageRepository messageRepository;
    private final KafkaTemplate<String, TelegramNotificationRequest> kafkaTemplate;

    @Value("${notification.kafka-topic}")
    private String kafkaTopic;

    @Value("${notification.telegram-chat-id}")
    private String telegramChatId;

    @Transactional
    public void flushPendingNotifications() {
        List<NotificationMessage> pending =
                messageRepository.findBySentAtIsNullOrderByCreatedAtAsc();

        if (pending.isEmpty()) {
            return;
        }

        String combinedMessage = pending.stream()
                .map(NotificationMessage::getMessage)
                .collect(Collectors.joining("\n"));

        kafkaTemplate.send(
                kafkaTopic,
                TelegramNotificationRequest.builder()
                        .chatId(telegramChatId)
                        .message(combinedMessage)
                        .type("telegram")
                        .build()
        );

        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        pending.forEach(m -> m.setSentAt(now));
        messageRepository.saveAll(pending);

        log.info("📨 {} notifications flushed", pending.size());
    }
}
