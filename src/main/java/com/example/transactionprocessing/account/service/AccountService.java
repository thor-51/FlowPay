package com.example.transactionprocessing.account.service;

import com.example.transactionprocessing.account.entity.Account;
import com.example.transactionprocessing.account.repository.AccountRepository;
import com.example.transactionprocessing.common.exception.InsufficientBalanceException;
import com.example.transactionprocessing.common.exception.ResourceNotFoundException;
import java.math.BigDecimal;
import java.security.SecureRandom;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class AccountService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final AccountRepository accountRepository;

    @Transactional
    public Account createAccount(UUID userId, String currency, BigDecimal initialBalance) {
        Account account = Account.builder()
                .userId(userId)
                .accountNumber(generateUniqueAccountNumber())
                .balance(initialBalance != null ? initialBalance : BigDecimal.ZERO)
                .currency(currency.toUpperCase())
                .build();

        account = accountRepository.save(account);
        log.info("Created account id={} number={} userId={} currency={}",
                account.getId(), account.getAccountNumber(), userId, account.getCurrency());
        return account;
    }

    @Transactional(readOnly = true)
    public List<Account> getAccountsForUser(UUID userId) {
        return accountRepository.findByUserId(userId);
    }

    @Transactional(readOnly = true)
    public Account getById(UUID accountId) {
        return accountRepository.findById(accountId)
                .orElseThrow(() -> new ResourceNotFoundException("Account not found: " + accountId));
    }

    /**
     * Locks both accounts of a transfer with SELECT ... FOR UPDATE, always in ascending UUID
     * order regardless of which is source/destination. Two transfers racing in opposite
     * directions between the same pair of accounts (A->B and B->A) would deadlock if each took
     * its own "source first" lock order; a fixed global order makes that impossible.
     *
     * Returns a map keyed by account id so the caller can pull out source/destination by id
     * without caring which happened to be locked first.
     */
    @Transactional
    public Map<UUID, Account> lockAccountsInOrder(UUID accountId1, UUID accountId2) {
        List<UUID> ordered = Stream.of(accountId1, accountId2).sorted().toList();

        Map<UUID, Account> locked = new LinkedHashMap<>();
        for (UUID id : ordered) {
            Account account = accountRepository.findByIdForUpdate(id)
                    .orElseThrow(() -> new ResourceNotFoundException("Account not found: " + id));
            locked.put(id, account);
        }
        return locked;
    }

    /**
     * Debits the account. Caller MUST already hold the pessimistic lock on this account (via
     * lockAccountsInOrder) — this method only validates and mutates, it does not itself acquire
     * any lock, so calling it on an unlocked Account defeats the whole point.
     */
    @Transactional
    public void debit(Account account, BigDecimal amount) {
        BigDecimal newBalance = account.getBalance().subtract(amount);
        if (newBalance.compareTo(BigDecimal.ZERO) < 0) {
            throw new InsufficientBalanceException(
                    "Account " + account.getAccountNumber() + " has insufficient balance: available="
                            + account.getBalance() + ", requested=" + amount);
        }
        account.setBalance(newBalance);
        accountRepository.save(account);
    }

    @Transactional
    public void credit(Account account, BigDecimal amount) {
        account.setBalance(account.getBalance().add(amount));
        accountRepository.save(account);
    }

    private String generateUniqueAccountNumber() {
        String candidate;
        do {
            // 12-digit numeric account number. Collisions are astronomically unlikely (1 in 9 *
            // 10^11 per draw) but the existence check makes the rare collision a retry instead
            // of a unique-constraint violation bubbling up to the caller.
            candidate = String.valueOf(100_000_000_000L + Math.abs(RANDOM.nextLong()) % 900_000_000_000L);
        } while (accountRepository.existsByAccountNumber(candidate));
        return candidate;
    }
}
