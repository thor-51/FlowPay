package com.example.transactionprocessing.transaction.event;

import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * deadLettered distinguishes "retryable failure, will try again" (published to
 * transaction.failed) from "exhausted retries, terminal" (published to
 * transaction.dead-letter) — both use this same event shape, just routed to different topics
 * by TransactionEventProducer based on this flag.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransactionFailedEvent {

    private UUID transactionId;
    private String transactionReference;
    private String reason;
    private int retryCount;
    private Instant failedAt;
    private boolean deadLettered;
}
