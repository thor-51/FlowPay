package com.example.transactionprocessing.transaction;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

import com.example.transactionprocessing.transaction.consumer.TransactionEventConsumer;
import com.example.transactionprocessing.transaction.event.TransactionCreatedEvent;
import com.example.transactionprocessing.transaction.service.TransactionProcessingService;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.test.context.ActiveProfiles;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.support.Acknowledgment;

@ActiveProfiles("test")
@ExtendWith(MockitoExtension.class)
class TransactionConsumerTest {

    @Mock
    private TransactionProcessingService transactionProcessingService;

    @Mock
    private Acknowledgment acknowledgment;

    private TransactionEventConsumer consumer;
    private TransactionCreatedEvent event;

    @BeforeEach
    void setUp() {
        consumer = new TransactionEventConsumer(transactionProcessingService);

        event = TransactionCreatedEvent.builder()
                .transactionId(UUID.randomUUID())
                .transactionReference("TXN-test")
                .sourceAccountId(UUID.randomUUID())
                .destinationAccountId(UUID.randomUUID())
                .amount(new BigDecimal("50.00"))
                .currency("USD")
                .idempotencyKey(null)
                .createdAt(Instant.now())
                .build();
    }

    @Test
    void onTransactionCreated_delegatesToProcessingServiceAndAcknowledges() {
        consumer.onTransactionCreated(event, 0, 0L, acknowledgment);

        verify(transactionProcessingService).process(event.getTransactionId());
        verify(acknowledgment).acknowledge();
    }

    @Test
    void onTransactionCreated_stillAcknowledges_whenProcessingThrowsUnexpectedException() {
        // TransactionProcessingService.process(...) already catches and records every
        // business-level failure against the transaction itself, so this simulates the rarer
        // case of something escaping that (e.g. a programming error). The consumer's contract is
        // to still acknowledge rather than let the container redeliver indefinitely and stall
        // the partition (see TransactionEventConsumer's class comment for the full reasoning).
        doThrow(new RuntimeException("boom")).when(transactionProcessingService).process(event.getTransactionId());

        consumer.onTransactionCreated(event, 0, 0L, acknowledgment);

        verify(acknowledgment).acknowledge();
    }

    @Test
    void onTransactionCreated_neverPropagatesExceptionsBackToTheListenerContainer() {
        doThrow(new RuntimeException("boom")).when(transactionProcessingService).process(event.getTransactionId());

        Assertions.assertDoesNotThrow(() -> consumer.onTransactionCreated(event, 0, 0L, acknowledgment));
    }
}
