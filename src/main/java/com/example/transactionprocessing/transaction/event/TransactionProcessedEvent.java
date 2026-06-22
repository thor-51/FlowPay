package com.example.transactionprocessing.transaction.event;

import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * transactionReference is included alongside transactionId (not in the original spec's field
 * list) so downstream consumers of this topic can correlate against the human-readable
 * reference without a callback lookup.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransactionProcessedEvent {

    private UUID transactionId;
    private String transactionReference;
    private String status;
    private Instant processedAt;
}
