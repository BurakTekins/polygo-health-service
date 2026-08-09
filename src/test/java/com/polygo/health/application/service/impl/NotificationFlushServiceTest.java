package com.polygo.health.application.service.impl;

import com.polygo.health.domain.model.NotificationMessage;
import com.polygo.health.infrastructure.persistence.jpa.repository.NotificationMessageRepository;
import com.polygo.health.interfaces.rest.request.TelegramNotificationRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class NotificationFlushServiceTest {
    private final NotificationMessageRepository repository = mock(NotificationMessageRepository.class);
    private final KafkaTemplate<String, TelegramNotificationRequest> kafka = mock(KafkaTemplate.class);
    private final NotificationFlushService service = new NotificationFlushService(repository, kafka);

    @BeforeEach
    void setup() {
        ReflectionTestUtils.setField(service, "kafkaTopic", "test-notifications");
        ReflectionTestUtils.setField(service, "telegramChatId", "synthetic-chat");
    }

    @Test
    void emptyQueueDoesNotPublishOrWrite() {
        when(repository.findBySentAtIsNullOrderByCreatedAtAsc()).thenReturn(List.of());
        service.flushPendingNotifications();
        verifyNoInteractions(kafka);
        verify(repository, never()).saveAll(any());
    }

    @Test
    void publishesPendingMessagesInRepositoryOrder() {
        var first = NotificationMessage.builder().message("first").build();
        var second = NotificationMessage.builder().message("second").build();
        when(repository.findBySentAtIsNullOrderByCreatedAtAsc()).thenReturn(List.of(first, second));
        when(kafka.send(eq("test-notifications"), any(TelegramNotificationRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(null));
        service.flushPendingNotifications();
        var request = org.mockito.ArgumentCaptor.forClass(TelegramNotificationRequest.class);
        verify(kafka).send(eq("test-notifications"), request.capture());
        assertThat(request.getValue().getMessage()).isEqualTo("first\nsecond");
        assertThat(request.getValue().getChatId()).isEqualTo("synthetic-chat");
        assertThat(first.getSentAt()).isNotNull().isEqualTo(second.getSentAt());
        verify(repository).saveAll(List.of(first, second));
    }

    @Test
    void synchronousPublishFailureLeavesMessagesPending() {
        var pending = NotificationMessage.builder().message("retry me").build();
        when(repository.findBySentAtIsNullOrderByCreatedAtAsc()).thenReturn(List.of(pending));
        var failure = new IllegalStateException("producer unavailable");
        when(kafka.send(eq("test-notifications"), any(TelegramNotificationRequest.class))).thenThrow(failure);
        assertThatThrownBy(service::flushPendingNotifications).isSameAs(failure);
        assertThat(pending.getSentAt()).isNull();
        verify(repository, never()).saveAll(any());
    }
}
