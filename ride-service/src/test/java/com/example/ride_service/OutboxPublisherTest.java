
package com.example.ride_service;

import com.example.ride_service.entity.OutboxEvent;
import com.example.ride_service.repository.OutboxEventRepository;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import com.example.ride_service.outbox.OutboxPublisher;

class OutboxPublisherTest {

    private OutboxEventRepository outboxEventRepository;
    private KafkaTemplate<String, String> kafkaTemplate;
    private OutboxPublisher outboxPublisher;

    private OutboxEvent event;

    @BeforeEach
    void setUp() {
        outboxEventRepository = mock(OutboxEventRepository.class);
        kafkaTemplate = mockKafkaTemplate();
        outboxPublisher = new OutboxPublisher(
                outboxEventRepository,
                kafkaTemplate
        );

        event = new OutboxEvent(
                UUID.randomUUID(),
                "RIDE",
                UUID.randomUUID(),
                "ride.requested",
                "{\"status\":\"REQUESTED\"}"
        );

        when(outboxEventRepository
                .findByPublishedAtIsNullOrderByCreatedAtAsc())
                .thenReturn(List.of(event));
    }

    @Test
    void shouldIncrementRetryCountWhenKafkaPublishingFails() {

        CompletableFuture<SendResult<String, String>> failedFuture =
                new CompletableFuture<>();

        failedFuture.completeExceptionally(
                new RuntimeException("Kafka unavailable")
        );

        when(kafkaTemplate.send(
                eq("ride.requested"),
                anyString(),
                anyString()
        )).thenReturn(failedFuture);

        outboxPublisher.publishPendingEvents();

        assertEquals(1, event.getRetryCount());
        assertTrue(event.getLastError().contains("Kafka unavailable"));
        assertNull(event.getPublishedAt());
    }

    @Test
    void shouldMarkEventPublishedWhenKafkaPublishingSucceeds() {

        // Simulate an earlier failure.
        event.recordFailure("Previous Kafka failure");

        SendResult<String, String> sendResult = mock(SendResult.class);
        RecordMetadata metadata = mock(RecordMetadata.class);

        when(sendResult.getRecordMetadata()).thenReturn(metadata);
        when(metadata.topic()).thenReturn("ride.requested");
        when(metadata.partition()).thenReturn(0);
        when(metadata.offset()).thenReturn(123L);

        CompletableFuture<SendResult<String, String>> successfulFuture =
                CompletableFuture.completedFuture(sendResult);

        when(kafkaTemplate.send(
                eq("ride.requested"),
                anyString(),
                anyString()
        )).thenReturn(successfulFuture);

        outboxPublisher.publishPendingEvents();

        assertNotNull(event.getPublishedAt());
        assertNull(event.getLastError());
        assertEquals(1, event.getRetryCount());
    }

    @Test
    void shouldRetryFailedEventAndPublishSuccessfully() {

        CompletableFuture<SendResult<String, String>> failedFuture =
                new CompletableFuture<>();

        failedFuture.completeExceptionally(
                new RuntimeException("Temporary Kafka failure")
        );

        SendResult<String, String> sendResult = mock(SendResult.class);
        RecordMetadata metadata = mock(RecordMetadata.class);

        when(sendResult.getRecordMetadata()).thenReturn(metadata);
        when(metadata.topic()).thenReturn("ride.requested");
        when(metadata.partition()).thenReturn(0);
        when(metadata.offset()).thenReturn(124L);

        CompletableFuture<SendResult<String, String>> successfulFuture =
                CompletableFuture.completedFuture(sendResult);

        when(kafkaTemplate.send(
                eq("ride.requested"),
                anyString(),
                anyString()
        )).thenReturn(failedFuture, successfulFuture);

        // First attempt fails.
        outboxPublisher.publishPendingEvents();

        assertEquals(1, event.getRetryCount());
        assertNull(event.getPublishedAt());
        assertNotNull(event.getLastError());

        // Second attempt succeeds.
        outboxPublisher.publishPendingEvents();

        assertEquals(1, event.getRetryCount());
        assertNotNull(event.getPublishedAt());
        assertNull(event.getLastError());

        verify(kafkaTemplate, times(2)).send(
                eq("ride.requested"),
                anyString(),
                anyString()
        );
    }
    @SuppressWarnings("unchecked")
    private static KafkaTemplate<String, String> mockKafkaTemplate() {
        return (KafkaTemplate<String, String>) mock(KafkaTemplate.class);
    }
}
