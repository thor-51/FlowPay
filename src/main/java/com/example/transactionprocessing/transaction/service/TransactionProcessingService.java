package com.example.transactionprocessing.transaction.service;

import com.example.transactionprocessing.account.entity.Account;
import com.example.transactionprocessing.account.service.AccountService;
import com.example.transactionprocessing.audit.service.AuditService;
import com.example.transactionprocessing.common.exception.InsufficientBalanceException;
import com.example.transactionprocessing.common.exception.ResourceNotFoundException;
import com.example.transactionprocessing.config.RetryProperties;
import com.example.transactionprocessing.metrics.TransactionMetrics;
import com.example.transactionprocessing.transaction.entity.Transaction;
import com.example.transactionprocessing.transaction.entity.TransactionStatus;
import com.example.transactionprocessing.transaction.producer.TransactionEventProducer;
import com.example.transactionprocessing.transaction.repository.TransactionRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The actual debit/credit critical section, invoked by TransactionEventConsumer for fresh
 * transactions and by RetryService for scheduled redeliveries — both paths converge here so
 * there is exactly one place that ever moves money.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TransactionProcessingService {

    private final TransactionRepository transactionRepository;
    private final AccountService accountService;
    private final AuditService auditService;
    private final TransactionEventProducer eventProducer;
    private final RetryProperties retryProperties;
    private final TransactionMetrics transactionMetrics;

    @Transactional
    public void process(UUID transactionId) {
        Transaction transaction = transactionRepository.findById(transactionId)
                .orElseThrow(() -> new ResourceNotFoundException("Transaction not found: " + transactionId));

        // Defends against a redelivered or duplicate Kafka message reprocessing a transaction
        // that has already moved past PENDING/RETRYING (e.g. consumer crashed after committing
        // the DB update but before the manual ack reached the broker). Without this guard a
        // SUCCESS transaction could be re-debited on redelivery.
        if (transaction.getStatus() != TransactionStatus.PENDING
                && transaction.getStatus() != TransactionStatus.RETRYING) {
            log.warn("Skipping transaction id={} reference={}: status={} is not eligible for processing",
                    transaction.getId(), transaction.getTransactionReference(), transaction.getStatus());
            return;
        }

        transaction.setStatus(TransactionStatus.PROCESSING);
        transaction = transactionRepository.save(transaction);
        auditService.record(transaction.getId(), "TRANSACTION_PROCESSING", "Consumer claimed transaction for processing");

        try {
            Map<UUID, Account> locked = accountService.lockAccountsInOrder(
                    transaction.getSourceAccountId(), transaction.getDestinationAccountId());
            Account source = locked.get(transaction.getSourceAccountId());
            Account destination = locked.get(transaction.getDestinationAccountId());

            accountService.debit(source, transaction.getAmount());
            accountService.credit(destination, transaction.getAmount());

            transaction.setStatus(TransactionStatus.SUCCESS);
            transaction.setProcessedAt(Instant.now());
            transaction.setFailureReason(null);
            transactionRepository.save(transaction);

            auditService.record(transaction.getId(), "TRANSACTION_SUCCESS", "Funds moved successfully",
                    Map.of(
                            "sourceAccount", source.getAccountNumber(),
                            "destinationAccount", destination.getAccountNumber(),
                            "amount", transaction.getAmount()));

            eventProducer.publishTransactionProcessed(transaction);

            transactionMetrics.incrementSuccess();
            transactionMetrics.recordProcessingDuration(Duration.between(transaction.getCreatedAt(), Instant.now()));

            log.info("Transaction id={} reference={} processed successfully",
                    transaction.getId(), transaction.getTransactionReference());

        } catch (InsufficientBalanceException ex) {
            handleNonRetryableFailure(transaction, ex.getMessage());
        } catch (Exception ex) {
            handleRetryableFailure(transaction, ex);
        }
    }

    private void handleNonRetryableFailure(Transaction transaction, String reason) {
        transaction.setStatus(TransactionStatus.FAILED);
        transaction.setFailureReason(reason);
        transactionRepository.save(transaction);

        auditService.record(transaction.getId(), "TRANSACTION_FAILED", reason);
        eventProducer.publishTransactionFailed(transaction, reason, false);

        transactionMetrics.incrementFailed();
        transactionMetrics.recordProcessingDuration(Duration.between(transaction.getCreatedAt(), Instant.now()));

        log.warn("Transaction id={} reference={} failed (non-retryable): {}",
                transaction.getId(), transaction.getTransactionReference(), reason);
    }

    private void handleRetryableFailure(Transaction transaction, Exception ex) {
        int newRetryCount = transaction.getRetryCount() + 1;
        transaction.setRetryCount(newRetryCount);
        transaction.setFailureReason(ex.getMessage());

        if (newRetryCount >= retryProperties.getMaxAttempts()) {
            transaction.setStatus(TransactionStatus.DEAD_LETTERED);
            transactionRepository.save(transaction);

            auditService.record(transaction.getId(), "TRANSACTION_DEAD_LETTERED",
                    "Exhausted " + newRetryCount + " attempt(s): " + ex.getMessage());
            eventProducer.publishTransactionFailed(transaction, ex.getMessage(), true);

            transactionMetrics.incrementDeadLettered();
            transactionMetrics.recordProcessingDuration(Duration.between(transaction.getCreatedAt(), Instant.now()));

            log.error("Transaction id={} reference={} dead-lettered after {} attempts: {}",
                    transaction.getId(), transaction.getTransactionReference(), newRetryCount, ex.getMessage(), ex);
        } else {
            transaction.setStatus(TransactionStatus.RETRYING);
            transactionRepository.save(transaction);

            auditService.record(transaction.getId(), "TRANSACTION_RETRY_SCHEDULED",
                    "Attempt " + newRetryCount + "/" + retryProperties.getMaxAttempts()
                            + " failed, scheduled for retry: " + ex.getMessage());
            eventProducer.publishTransactionFailed(transaction, ex.getMessage(), false);

            transactionMetrics.incrementRetryScheduled();

            log.warn("Transaction id={} reference={} scheduled for retry {}/{}: {}",
                    transaction.getId(), transaction.getTransactionReference(), newRetryCount,
                    retryProperties.getMaxAttempts(), ex.getMessage());
        }
    }
}
