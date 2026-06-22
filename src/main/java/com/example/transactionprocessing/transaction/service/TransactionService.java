package com.example.transactionprocessing.transaction.service;

import com.example.transactionprocessing.account.entity.Account;
import com.example.transactionprocessing.account.repository.AccountRepository;
import com.example.transactionprocessing.audit.service.AuditService;
import com.example.transactionprocessing.common.exception.IdempotencyInProgressException;
import com.example.transactionprocessing.common.exception.InvalidTransactionException;
import com.example.transactionprocessing.common.exception.ResourceNotFoundException;
import com.example.transactionprocessing.common.exception.UnauthorizedAccountAccessException;
import com.example.transactionprocessing.metrics.TransactionMetrics;
import com.example.transactionprocessing.transaction.entity.Transaction;
import com.example.transactionprocessing.transaction.entity.TransactionStatus;
import com.example.transactionprocessing.transaction.producer.TransactionEventProducer;
import com.example.transactionprocessing.transaction.repository.TransactionRepository;
import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * Handles the synchronous half of a transfer (POST /api/v1/transactions): validation,
 * idempotency, persisting the PENDING row, and publishing TransactionCreatedEvent. The actual
 * money movement happens asynchronously in TransactionProcessingService once the Kafka consumer
 * picks the event up — this class never touches account balances.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TransactionService {

    private final TransactionRepository transactionRepository;
    private final AccountRepository accountRepository;
    private final IdempotencyService idempotencyService;
    private final TransactionEventProducer eventProducer;
    private final AuditService auditService;
    private final TransactionMetrics transactionMetrics;

    @Transactional
    public Transaction createTransaction(CreateTransactionCommand command) {

        // --- Idempotency fast path: an identical key we've already seen short-circuits
        // straight to the existing result, without re-running validation against (possibly by
        // now changed) account state. -------------------------------------------------------
        if (StringUtils.hasText(command.idempotencyKey())) {
            Optional<Transaction> existing = findByCachedIdempotencyKey(command.idempotencyKey());
            if (existing.isPresent()) {
                log.info("Idempotency key {} already processed, returning existing transaction {}",
                        command.idempotencyKey(), existing.get().getTransactionReference());
                return existing.get();
            }
        }

        validate(command);

        String transactionReference = "TXN-" + UUID.randomUUID();

        // --- Claim the idempotency key BEFORE persisting, closing the race window between two
        // near-simultaneous requests carrying the same key. Whoever loses the claim returns the
        // winner's result instead of creating a duplicate transfer. ------------------------
        if (StringUtils.hasText(command.idempotencyKey())) {
            boolean claimed = idempotencyService.tryClaim(command.idempotencyKey(), transactionReference);
            if (!claimed) {
                // Someone else holds this key. Either their transaction has already committed
                // (in which case we hand back their result) or they're still mid-request (the
                // Redis claim landed before their DB insert committed) — that second case isn't
                // an error in our own logic, it's a genuine "try again in a moment" for the
                // caller, so it gets its own exception/status rather than masquerading as a
                // generic 500.
                Transaction winner = findByCachedIdempotencyKey(command.idempotencyKey())
                        .orElseThrow(() -> new IdempotencyInProgressException(
                                "A request with idempotency key " + command.idempotencyKey()
                                        + " is already being processed; please retry shortly"));
                log.info("Lost idempotency claim race for key {}, returning concurrent winner {}",
                        command.idempotencyKey(), winner.getTransactionReference());
                return winner;
            }
        }

        Transaction transaction = Transaction.builder()
                .transactionReference(transactionReference)
                .userId(command.userId())
                .sourceAccountId(command.sourceAccountId())
                .destinationAccountId(command.destinationAccountId())
                .amount(command.amount())
                .currency(command.currency().toUpperCase())
                .status(TransactionStatus.PENDING)
                .idempotencyKey(command.idempotencyKey())
                .retryCount(0)
                .build();

        transaction = transactionRepository.save(transaction);

        auditService.record(
                transaction.getId(),
                "TRANSACTION_CREATED",
                "Transaction created and queued for asynchronous processing",
                Map.of(
                        "transactionReference", transaction.getTransactionReference(),
                        "amount", transaction.getAmount(),
                        "currency", transaction.getCurrency()));

        eventProducer.publishTransactionCreated(transaction);
        transactionMetrics.incrementCreated();

        log.info("Created transaction id={} reference={} amount={} {} status=PENDING",
                transaction.getId(), transaction.getTransactionReference(),
                transaction.getAmount(), transaction.getCurrency());

        return transaction;
    }

    @Transactional(readOnly = true)
    public Transaction getById(UUID id) {
        return transactionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Transaction not found: " + id));
    }

    @Transactional(readOnly = true)
    public Transaction getByReference(String transactionReference) {
        return transactionRepository.findByTransactionReference(transactionReference)
                .orElseThrow(() -> new ResourceNotFoundException("Transaction not found: " + transactionReference));
    }

    @Transactional(readOnly = true)
    public Page<Transaction> getForUser(UUID userId, Pageable pageable) {
        return transactionRepository.findByUserId(userId, pageable);
    }

    @Transactional(readOnly = true)
    public Page<Transaction> getAll(Pageable pageable) {
        return transactionRepository.findAll(pageable);
    }

    @Transactional(readOnly = true)
    public Page<Transaction> getByStatus(TransactionStatus status, Pageable pageable) {
        return transactionRepository.findByStatus(status, pageable);
    }

    @Transactional(readOnly = true)
    public Map<TransactionStatus, Long> getStatusCounts() {
        return Arrays.stream(TransactionStatus.values())
                .collect(Collectors.toMap(status -> status, transactionRepository::countByStatus));
    }

    private void validate(CreateTransactionCommand command) {
        if (command.sourceAccountId().equals(command.destinationAccountId())) {
            throw new InvalidTransactionException("Source and destination accounts must differ");
        }

        if (command.amount().signum() <= 0) {
            throw new InvalidTransactionException("Transaction amount must be positive");
        }

        Account sourceAccount = accountRepository.findById(command.sourceAccountId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Source account not found: " + command.sourceAccountId()));

        Account destinationAccount = accountRepository.findById(command.destinationAccountId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Destination account not found: " + command.destinationAccountId()));

        if (!sourceAccount.getUserId().equals(command.userId())) {
            throw new UnauthorizedAccountAccessException("You do not own the source account for this transaction");
        }

        if (!sourceAccount.getCurrency().equalsIgnoreCase(command.currency())
                || !destinationAccount.getCurrency().equalsIgnoreCase(command.currency())) {
            throw new InvalidTransactionException(
                    "Transaction currency must match both accounts' currency; cross-currency "
                            + "transfers are not supported in this version");
        }
    }

    private Optional<Transaction> findByCachedIdempotencyKey(String idempotencyKey) {
        return idempotencyService.getCachedTransactionReference(idempotencyKey)
                .flatMap(transactionRepository::findByTransactionReference);
    }
}
