package com.example.transactionprocessing.transaction.service;

import com.example.transactionprocessing.audit.service.AuditService;
import com.example.transactionprocessing.common.exception.InvalidTransactionStateException;
import com.example.transactionprocessing.common.exception.ResourceNotFoundException;
import com.example.transactionprocessing.config.RetryProperties;
import com.example.transactionprocessing.transaction.entity.Transaction;
import com.example.transactionprocessing.transaction.entity.TransactionStatus;
import com.example.transactionprocessing.transaction.producer.TransactionEventProducer;
import com.example.transactionprocessing.transaction.repository.TransactionRepository;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Two responsibilities:
 *  1. A scheduled sweep that re-publishes RETRYING transactions to transaction.created once
 *     their exponential backoff window has elapsed, so the consumer picks them up again.
 *  2. manualRetry(), called by the admin "retry a failed/dead-lettered transaction" endpoint
 *     (Part 5), which gives a FAILED or DEAD_LETTERED transaction a fresh retry budget.
 *
 * The sweep re-publishes through Kafka rather than calling TransactionProcessingService
 * directly, so a retried transaction goes through the exact same code path — and shows up in
 * the same topic — as a brand-new one; there's no special "internal" processing route to keep
 * in sync.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RetryService {

    private static final int SWEEP_PAGE_SIZE = 200;

    private final TransactionRepository transactionRepository;
    private final TransactionEventProducer eventProducer;
    private final AuditService auditService;
    private final RetryProperties retryProperties;

    @Scheduled(fixedDelayString = "${app.retry.sweep-interval-ms:5000}")
    public void sweepDueRetries() {
        Page<Transaction> retrying = transactionRepository.findByStatus(
                TransactionStatus.RETRYING, PageRequest.of(0, SWEEP_PAGE_SIZE, Sort.by("updatedAt").ascending()));

        if (retrying.isEmpty()) {
            return;
        }

        Instant now = Instant.now();
        int republished = 0;

        for (Transaction transaction : retrying) {
            Instant dueAt = transaction.getUpdatedAt().plusMillis(computeBackoffMs(transaction.getRetryCount()));
            if (!now.isBefore(dueAt)) {
                republish(transaction, "Retry sweep re-publishing attempt " + transaction.getRetryCount()
                        + "/" + retryProperties.getMaxAttempts() + " after exponential backoff");
                republished++;
            }
        }

        if (republished > 0) {
            log.info("Retry sweep republished {} of {} RETRYING transaction(s)",
                    republished, retrying.getNumberOfElements());
        }
    }

    @Transactional
    public void manualRetry(UUID transactionId) {
        Transaction transaction = transactionRepository.findById(transactionId)
                .orElseThrow(() -> new ResourceNotFoundException("Transaction not found: " + transactionId));

        TransactionStatus previousStatus = transaction.getStatus();
        if (previousStatus != TransactionStatus.FAILED && previousStatus != TransactionStatus.DEAD_LETTERED) {
            throw new InvalidTransactionStateException(
                    "Only FAILED or DEAD_LETTERED transactions can be manually retried (current status: "
                            + previousStatus + ")");
        }

        // A manual admin-triggered retry gets a clean retry budget rather than picking up where
        // the automatic retries left off — the assumption being a human looked at this and
        // decided it's worth a fresh attempt, not that the underlying transient issue is
        // necessarily still happening.
        transaction.setRetryCount(0);
        transaction.setStatus(TransactionStatus.RETRYING);
        transaction = transactionRepository.save(transaction);

        republish(transaction, "Admin manually triggered a retry from status " + previousStatus);

        log.info("Admin manual retry: transaction id={} reference={} reset from {} to RETRYING",
                transaction.getId(), transaction.getTransactionReference(), previousStatus);
    }

    private void republish(Transaction transaction, String auditMessage) {
        auditService.record(transaction.getId(), "TRANSACTION_RETRY_REPUBLISHED", auditMessage);
        eventProducer.publishTransactionCreated(transaction);
    }

    /**
     * initialBackoffMs * multiplier^(retryCount - 1), capped at maxBackoffMs. retryCount is
     * already >= 1 by the time a transaction reaches RETRYING (TransactionProcessingService
     * increments it before setting this status), so retryCount=1 uses the initial delay
     * unscaled and each subsequent failed attempt widens the window geometrically.
     */
    private long computeBackoffMs(int retryCount) {
        double raw = retryProperties.getInitialBackoffMs()
                * Math.pow(retryProperties.getBackoffMultiplier(), Math.max(0, retryCount - 1));
        return (long) Math.min(raw, retryProperties.getMaxBackoffMs());
    }
}
