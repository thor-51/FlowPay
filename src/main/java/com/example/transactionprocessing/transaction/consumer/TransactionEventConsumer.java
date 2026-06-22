package com.example.transactionprocessing.transaction.consumer;

import com.example.transactionprocessing.transaction.event.TransactionCreatedEvent;
import com.example.transactionprocessing.transaction.service.TransactionProcessingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class TransactionEventConsumer {

    private final TransactionProcessingService transactionProcessingService;

    @KafkaListener(
            topics = "${app.kafka.topics.transaction-created}",
            groupId = "${spring.kafka.consumer.group-id}",
            containerFactory = "kafkaListenerContainerFactory")
    public void onTransactionCreated(
            @Payload TransactionCreatedEvent event,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {

        log.info("Consumed TransactionCreatedEvent transactionId={} reference={} partition={} offset={}",
                event.getTransactionId(), event.getTransactionReference(), partition, offset);

        try {
            transactionProcessingService.process(event.getTransactionId());
        } catch (Exception ex) {
            // TransactionProcessingService.process(...) already catches and records every
            // business-level failure (insufficient balance, transient errors, retry/dead-letter
            // routing) against the transaction itself. Reaching this catch block means something
            // unexpected broke — a programming error, the transaction row vanishing, etc. We
            // still acknowledge rather than let the container redeliver indefinitely and stall
            // the partition; this is logged at ERROR specifically so it surfaces in
            // monitoring/alerting rather than disappearing into a retry loop.
            log.error("Unexpected error processing transactionId={}: {}",
                    event.getTransactionId(), ex.getMessage(), ex);
        } finally {
            acknowledgment.acknowledge();
        }
    }
}
