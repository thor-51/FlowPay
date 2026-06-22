package com.example.transactionprocessing.transaction.entity;

/**
 * Transaction lifecycle states.
 *
 * PENDING       -> created by the API, awaiting Kafka consumer pickup
 * PROCESSING    -> consumer has claimed it and is running the debit/credit logic
 * SUCCESS       -> terminal: funds moved successfully
 * FAILED        -> terminal: failed and retries exhausted, but NOT dead-lettered
 *                  (used for failures that are not worth retrying, e.g. insufficient funds)
 * RETRYING      -> a transient failure occurred and the transaction is scheduled for another attempt
 * DEAD_LETTERED -> terminal: exhausted max retry attempts on a retryable error, published to
 *                  the transaction.dead-letter topic for manual/admin intervention
 */
public enum TransactionStatus {
    PENDING,
    PROCESSING,
    SUCCESS,
    FAILED,
    RETRYING,
    DEAD_LETTERED
}
