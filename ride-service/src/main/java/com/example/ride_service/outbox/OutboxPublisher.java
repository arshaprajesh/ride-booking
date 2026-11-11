package com.example.ride_service.outbox;

import com.example.ride_service.entity.OutboxEvent;
import com.example.ride_service.repository.OutboxEventRepository;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.kafka.support.SendResult;

import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component
public class OutboxPublisher {

    private static final Logger log = LoggerFactory.getLogger(OutboxPublisher.class);
    private static final String TOPIC = "ride.requested";

    private final OutboxEventRepository outboxEventRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;

    public OutboxPublisher(
            OutboxEventRepository outboxEventRepository,
            KafkaTemplate<String, String> kafkaTemplate) {

        this.outboxEventRepository = outboxEventRepository;
        this.kafkaTemplate = kafkaTemplate;
    }

    @Scheduled(fixedDelay = 5000)//Runs the publishing task repeatedly, with a five-second delay after each execution.
    @Transactional
    public void publishPendingEvents() {

        List<OutboxEvent> events =
                outboxEventRepository.findByPublishedAtIsNullOrderByCreatedAtAsc();//Gets unpublished events.
        if (events.isEmpty()) {
            log.debug("No pending outbox events found");
            return;
        }

        log.info("Found {} pending outbox events", events.size());

        for (OutboxEvent event : events) {
            try {
                log.info(
                        "Publishing outbox event. eventId={}, aggregateId={}",
                        event.getId(),
                        event.getAggregateId()
                );

                SendResult<String, String> result =
                        kafkaTemplate.send(
                                TOPIC,
                                event.getAggregateId().toString(),
                                event.getPayload()
                        ).get();//Sends the JSON message to Kafka and waits for the send result.

                log.info(
                        "Kafka acknowledgement received. topic={}, partition={}, offset={}",
                        result.getRecordMetadata().topic(),
                        result.getRecordMetadata().partition(),
                        result.getRecordMetadata().offset()
                );//get to know Kafka acknowledgement received

                event.markPublished(Instant.now());//Marks the event as published after successful acknowledgement.

                log.info(
                        "Outbox event published successfully. eventId={}",
                        event.getId()
                );

            } catch (Exception exception) {

                event.recordFailure(exception.getMessage());//if failed , it records the error, and leaves published_at as NULL and increments the retry count.

                log.error(
                        "Failed to publish event: {}. Error: {}",
                        event.getId(),
                        exception.getMessage()
                );
            }
        }
    }
}