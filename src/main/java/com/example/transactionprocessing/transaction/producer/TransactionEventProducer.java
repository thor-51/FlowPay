package com.example.transactionprocessing.transaction.producer;

import com.example.transactionprocessing.transaction.entity.Transaction;
import com.example.transactionprocessing.transaction.event.TransactionCreatedEvent;
import com.example.transactionprocessing.transaction.event.TransactionFailedEvent;
import com.example.transactionprocessing.transaction.event.TransactionProcessedEvent;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * All publishes key the Kafka record by transaction id (not a random key), so every event for a
 * given transaction — created, processed, failed/dead-lettered, and any retries — lands on the
 * same partition and is therefore strictly ordered for that transaction, even though the topic
 * as a whole is partitioned for throughput.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class TransactionEventProducer {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Value("${app.kafka.topics.transaction-created}")
    private String createdTopic;

    @Value("${app.kafka.topics.transaction-processed}")
    private String processedTopic;

    @Value("${app.kafka.topics.transaction-failed}")
    private String failedTopic;

    @Value("${app.kafka.topics.transaction-dead-letter}")
    private String deadLetterTopic;

    public void publishTransactionCreated(Transaction transaction) {
        TransactionCreatedEvent event = TransactionCreatedEvent.builder()
                .transactionId(transaction.getId())
                .transactionReference(transaction.getTransactionReference())
                .sourceAccountId(transaction.getSourceAccountId())
                .destinationAccountId(transaction.getDestinationAccountId())
                .amount(transaction.getAmount())
                .currency(transaction.getCurrency())
                .idempotencyKey(transaction.getIdempotencyKey())
                .createdAt(Instant.now())
                .build();

        send(createdTopic, transaction.getId().toString(), event);
    }

    public void publishTransactionProcessed(Transaction transaction) {
        TransactionProcessedEvent event = TransactionProcessedEvent.builder()
                .transactionId(transaction.getId())
                .transactionReference(transaction.getTransactionReference())
                .status(transaction.getStatus().name())
                .processedAt(transaction.getProcessedAt())
                .build();

        send(processedTopic, transaction.getId().toString(), event);
    }

    public void publishTransactionFailed(Transaction transaction, String reason, boolean deadLettered) {
        TransactionFailedEvent event = TransactionFailedEvent.builder()
                .transactionId(transaction.getId())
                .transactionReference(transaction.getTransactionReference())
                .reason(reason)
                .retryCount(transaction.getRetryCount())
                .failedAt(Instant.now())
                .deadLettered(deadLettered)
                .build();

        send(deadLettered ? deadLetterTopic : failedTopic, transaction.getId().toString(), event);
    }

    private void send(String topic, String key, Object payload) {
        kafkaTemplate.send(topic, key, payload).whenComplete((result, ex) -> {
            if (ex != null) {
                log.error("Failed to publish event to topic={} key={}: {}", topic, key, ex.getMessage(), ex);
            } else {
                log.debug(
                        "Published event topic={} partition={} offset={}",
                        topic,
                        result.getRecordMetadata().partition(),
                        result.getRecordMetadata().offset());
            }
        });
    }
}
