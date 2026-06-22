package com.example.transactionprocessing.transaction.service;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

/**
 * The controller layer (Part 5) maps CreateTransactionRequest + the authenticated principal +
 * the Idempotency-Key header into one of these before calling TransactionService, keeping the
 * service's public API independent of any particular transport-layer DTO shape.
 */
public record CreateTransactionCommand(
        UUID userId,
        UUID sourceAccountId,
        UUID destinationAccountId,
        BigDecimal amount,
        String currency,
        String idempotencyKey) {

    public CreateTransactionCommand {
        Objects.requireNonNull(userId, "userId is required");
        Objects.requireNonNull(sourceAccountId, "sourceAccountId is required");
        Objects.requireNonNull(destinationAccountId, "destinationAccountId is required");
        Objects.requireNonNull(amount, "amount is required");
        Objects.requireNonNull(currency, "currency is required");
        // idempotencyKey is intentionally nullable: the header is recommended, not mandatory.
    }
}
