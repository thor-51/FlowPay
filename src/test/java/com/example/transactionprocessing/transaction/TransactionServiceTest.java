package com.example.transactionprocessing.transaction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
import com.example.transactionprocessing.transaction.service.CreateTransactionCommand;
import com.example.transactionprocessing.transaction.service.IdempotencyService;
import com.example.transactionprocessing.transaction.service.TransactionService;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.test.context.ActiveProfiles;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ActiveProfiles("test")
@ExtendWith(MockitoExtension.class)
class TransactionServiceTest {

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private IdempotencyService idempotencyService;

    @Mock
    private TransactionEventProducer eventProducer;

    @Mock
    private AuditService auditService;

    @Mock
    private TransactionMetrics transactionMetrics;

    private TransactionService transactionService;

    private UUID userId;
    private UUID sourceAccountId;
    private UUID destinationAccountId;
    private Account sourceAccount;
    private Account destinationAccount;

    @BeforeEach
    void setUp() {
        transactionService = new TransactionService(
                transactionRepository, accountRepository, idempotencyService, eventProducer,
                auditService, transactionMetrics);

        userId = UUID.randomUUID();
        sourceAccountId = UUID.randomUUID();
        destinationAccountId = UUID.randomUUID();

        sourceAccount = Account.builder()
                .id(sourceAccountId)
                .userId(userId)
                .accountNumber("100000000001")
                .balance(new BigDecimal("500.00"))
                .currency("USD")
                .build();

        destinationAccount = Account.builder()
                .id(destinationAccountId)
                .userId(UUID.randomUUID())
                .accountNumber("100000000002")
                .balance(BigDecimal.ZERO)
                .currency("USD")
                .build();
    }

    @Test
    void createTransaction_persistsPendingTransactionAndPublishesEvent_whenNoIdempotencyKey() {
        CreateTransactionCommand command = new CreateTransactionCommand(
                userId, sourceAccountId, destinationAccountId, new BigDecimal("100.00"), "USD", null);

        when(accountRepository.findById(sourceAccountId)).thenReturn(Optional.of(sourceAccount));
        when(accountRepository.findById(destinationAccountId)).thenReturn(Optional.of(destinationAccount));
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(inv -> inv.getArgument(0));

        Transaction result = transactionService.createTransaction(command);

        assertThat(result.getStatus()).isEqualTo(TransactionStatus.PENDING);
        assertThat(result.getSourceAccountId()).isEqualTo(sourceAccountId);
        assertThat(result.getTransactionReference()).startsWith("TXN-");
        verify(eventProducer).publishTransactionCreated(result);
        verify(transactionMetrics).incrementCreated();
        verify(idempotencyService, never()).tryClaim(any(), any());
    }

    @Test
    void createTransaction_throwsInvalidTransactionException_whenSourceEqualsDestination() {
        CreateTransactionCommand command = new CreateTransactionCommand(
                userId, sourceAccountId, sourceAccountId, new BigDecimal("10.00"), "USD", null);

        assertThatThrownBy(() -> transactionService.createTransaction(command))
                .isInstanceOf(InvalidTransactionException.class)
                .hasMessageContaining("must differ");

        verify(transactionRepository, never()).save(any());
    }

    @Test
    void createTransaction_throwsResourceNotFoundException_whenSourceAccountMissing() {
        CreateTransactionCommand command = new CreateTransactionCommand(
                userId, sourceAccountId, destinationAccountId, new BigDecimal("10.00"), "USD", null);

        when(accountRepository.findById(sourceAccountId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> transactionService.createTransaction(command))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void createTransaction_throwsUnauthorizedAccountAccessException_whenCallerDoesNotOwnSourceAccount() {
        UUID someoneElsesId = UUID.randomUUID();
        CreateTransactionCommand command = new CreateTransactionCommand(
                someoneElsesId, sourceAccountId, destinationAccountId, new BigDecimal("10.00"), "USD", null);

        when(accountRepository.findById(sourceAccountId)).thenReturn(Optional.of(sourceAccount));
        when(accountRepository.findById(destinationAccountId)).thenReturn(Optional.of(destinationAccount));

        assertThatThrownBy(() -> transactionService.createTransaction(command))
                .isInstanceOf(UnauthorizedAccountAccessException.class);
    }

    @Test
    void createTransaction_throwsInvalidTransactionException_whenCurrencyDoesNotMatchBothAccounts() {
        CreateTransactionCommand command = new CreateTransactionCommand(
                userId, sourceAccountId, destinationAccountId, new BigDecimal("10.00"), "EUR", null);

        when(accountRepository.findById(sourceAccountId)).thenReturn(Optional.of(sourceAccount));
        when(accountRepository.findById(destinationAccountId)).thenReturn(Optional.of(destinationAccount));

        assertThatThrownBy(() -> transactionService.createTransaction(command))
                .isInstanceOf(InvalidTransactionException.class)
                .hasMessageContaining("currency");
    }

    @Test
    void createTransaction_returnsExistingTransaction_whenIdempotencyKeyAlreadyCached() {
        String idempotencyKey = "client-key-123";
        Transaction existing = Transaction.builder()
                .id(UUID.randomUUID())
                .transactionReference("TXN-existing")
                .status(TransactionStatus.SUCCESS)
                .build();

        CreateTransactionCommand command = new CreateTransactionCommand(
                userId, sourceAccountId, destinationAccountId, new BigDecimal("10.00"), "USD", idempotencyKey);

        when(idempotencyService.getCachedTransactionReference(idempotencyKey))
                .thenReturn(Optional.of("TXN-existing"));
        when(transactionRepository.findByTransactionReference("TXN-existing")).thenReturn(Optional.of(existing));

        Transaction result = transactionService.createTransaction(command);

        assertThat(result).isSameAs(existing);
        // The whole point of the fast path is to skip re-validating account state for a request
        // we've already handled, so account lookups should never happen here.
        verify(accountRepository, never()).findById(any());
        verify(transactionRepository, never()).save(any());
        verify(transactionMetrics, never()).incrementCreated();
    }

    @Test
    void createTransaction_returnsWinner_whenIdempotencyClaimRaceIsLostButWinnerAlreadyCommitted() {
        String idempotencyKey = "client-key-456";
        Transaction winner = Transaction.builder()
                .id(UUID.randomUUID())
                .transactionReference("TXN-winner")
                .status(TransactionStatus.PENDING)
                .build();

        CreateTransactionCommand command = new CreateTransactionCommand(
                userId, sourceAccountId, destinationAccountId, new BigDecimal("10.00"), "USD", idempotencyKey);

        when(idempotencyService.getCachedTransactionReference(idempotencyKey))
                .thenReturn(Optional.empty())              // fast-path check: nothing cached yet
                .thenReturn(Optional.of("TXN-winner"));     // re-checked after losing the claim
        when(accountRepository.findById(sourceAccountId)).thenReturn(Optional.of(sourceAccount));
        when(accountRepository.findById(destinationAccountId)).thenReturn(Optional.of(destinationAccount));
        when(idempotencyService.tryClaim(eq(idempotencyKey), any())).thenReturn(false);
        when(transactionRepository.findByTransactionReference("TXN-winner")).thenReturn(Optional.of(winner));

        Transaction result = transactionService.createTransaction(command);

        assertThat(result).isSameAs(winner);
        verify(transactionRepository, never()).save(any());
    }

    @Test
    void createTransaction_throwsIdempotencyInProgress_whenClaimLostAndWinnerNotYetCommitted() {
        String idempotencyKey = "client-key-789";

        CreateTransactionCommand command = new CreateTransactionCommand(
                userId, sourceAccountId, destinationAccountId, new BigDecimal("10.00"), "USD", idempotencyKey);

        when(idempotencyService.getCachedTransactionReference(idempotencyKey))
                .thenReturn(Optional.empty())   // fast-path: nothing cached
                .thenReturn(Optional.empty());  // still nothing committed even after losing the claim
        when(accountRepository.findById(sourceAccountId)).thenReturn(Optional.of(sourceAccount));
        when(accountRepository.findById(destinationAccountId)).thenReturn(Optional.of(destinationAccount));
        when(idempotencyService.tryClaim(eq(idempotencyKey), any())).thenReturn(false);

        assertThatThrownBy(() -> transactionService.createTransaction(command))
                .isInstanceOf(IdempotencyInProgressException.class);
    }
}
