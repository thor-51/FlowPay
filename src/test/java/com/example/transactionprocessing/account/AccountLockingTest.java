package com.example.transactionprocessing.account;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.transactionprocessing.account.entity.Account;
import com.example.transactionprocessing.account.repository.AccountRepository;
import com.example.transactionprocessing.account.service.AccountService;
import com.example.transactionprocessing.common.exception.InsufficientBalanceException;
import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.test.context.ActiveProfiles;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ActiveProfiles("test")
@ExtendWith(MockitoExtension.class)
class AccountLockingTest {

    @Mock
    private AccountRepository accountRepository;

    private AccountService accountService;

    @BeforeEach
    void setUp() {
        accountService = new AccountService(accountRepository);
    }

    @Test
    void debit_reducesBalance_whenSufficientFunds() {
        Account account = sampleAccount(UUID.randomUUID(), new BigDecimal("100.00"));
        when(accountRepository.save(account)).thenReturn(account);

        accountService.debit(account, new BigDecimal("40.00"));

        assertThat(account.getBalance()).isEqualByComparingTo("60.00");
        verify(accountRepository).save(account);
    }

    @Test
    void debit_throwsInsufficientBalanceException_andDoesNotSave_whenAmountExceedsBalance() {
        Account account = sampleAccount(UUID.randomUUID(), new BigDecimal("10.00"));

        assertThatThrownBy(() -> accountService.debit(account, new BigDecimal("50.00")))
                .isInstanceOf(InsufficientBalanceException.class);

        // The balance check happens before any mutation, so a rejected debit must leave the
        // in-memory entity (and therefore the database, once the surrounding transaction
        // completes) completely untouched.
        assertThat(account.getBalance()).isEqualByComparingTo("10.00");
        verify(accountRepository, never()).save(any());
    }

    @Test
    void debit_allowsDrainingBalanceToExactlyZero() {
        Account account = sampleAccount(UUID.randomUUID(), new BigDecimal("25.00"));
        when(accountRepository.save(account)).thenReturn(account);

        accountService.debit(account, new BigDecimal("25.00"));

        assertThat(account.getBalance()).isEqualByComparingTo("0.00");
        verify(accountRepository).save(account);
    }

    @Test
    void credit_increasesBalance() {
        Account account = sampleAccount(UUID.randomUUID(), new BigDecimal("10.00"));
        when(accountRepository.save(account)).thenReturn(account);

        accountService.credit(account, new BigDecimal("15.00"));

        assertThat(account.getBalance()).isEqualByComparingTo("25.00");
        verify(accountRepository).save(account);
    }

    @Test
    void lockAccountsInOrder_alwaysAcquiresLocksInAscendingIdOrder_regardlessOfArgumentOrder() {
        UUID lowerId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID higherId = UUID.fromString("00000000-0000-0000-0000-000000000002");

        Account lowerAccount = sampleAccount(lowerId, BigDecimal.ZERO);
        Account higherAccount = sampleAccount(higherId, BigDecimal.ZERO);

        when(accountRepository.findByIdForUpdate(lowerId)).thenReturn(Optional.of(lowerAccount));
        when(accountRepository.findByIdForUpdate(higherId)).thenReturn(Optional.of(higherAccount));

        // Pass the higher id first, lower id second: the deadlock-prevention contract is that
        // lock ACQUISITION order must be ascending-by-id regardless of which account happens to
        // be the "source" vs "destination" of this particular transfer. Two transfers racing in
        // opposite directions between the same account pair would otherwise each take their own
        // "source first" lock order and deadlock against each other.
        Map<UUID, Account> locked = accountService.lockAccountsInOrder(higherId, lowerId);

        InOrder inOrder = Mockito.inOrder(accountRepository);
        inOrder.verify(accountRepository).findByIdForUpdate(lowerId);
        inOrder.verify(accountRepository).findByIdForUpdate(higherId);

        assertThat(locked).containsEntry(lowerId, lowerAccount);
        assertThat(locked).containsEntry(higherId, higherAccount);
    }

    @Test
    void lockAccountsInOrder_sameAscendingOrder_whenArgumentsAreAlreadyAscending() {
        UUID lowerId = UUID.fromString("00000000-0000-0000-0000-000000000003");
        UUID higherId = UUID.fromString("00000000-0000-0000-0000-000000000004");

        when(accountRepository.findByIdForUpdate(lowerId))
                .thenReturn(Optional.of(sampleAccount(lowerId, BigDecimal.ZERO)));
        when(accountRepository.findByIdForUpdate(higherId))
                .thenReturn(Optional.of(sampleAccount(higherId, BigDecimal.ZERO)));

        accountService.lockAccountsInOrder(lowerId, higherId);

        InOrder inOrder = Mockito.inOrder(accountRepository);
        inOrder.verify(accountRepository).findByIdForUpdate(lowerId);
        inOrder.verify(accountRepository).findByIdForUpdate(higherId);
    }

    private Account sampleAccount(UUID id, BigDecimal balance) {
        return Account.builder()
                .id(id)
                .userId(UUID.randomUUID())
                .accountNumber("ACC-" + id)
                .balance(balance)
                .currency("USD")
                .build();
    }
}
