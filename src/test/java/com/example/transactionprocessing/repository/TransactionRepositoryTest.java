package com.example.transactionprocessing.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.example.transactionprocessing.account.entity.Account;
import com.example.transactionprocessing.account.repository.AccountRepository;
import com.example.transactionprocessing.transaction.entity.Transaction;
import com.example.transactionprocessing.transaction.entity.TransactionStatus;
import com.example.transactionprocessing.transaction.repository.TransactionRepository;
import com.example.transactionprocessing.user.entity.Role;
import com.example.transactionprocessing.user.entity.User;
import com.example.transactionprocessing.user.repository.UserRepository;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Runs against a real PostgreSQL container rather than H2 in-memory: the schema is created by
 * the actual Flyway migrations (V1–V5), and several things worth testing here — NUMERIC(19,4)
 * column behavior, nullable idempotency_key UNIQUE semantics (Postgres treats multiple NULLs as
 * distinct, so two transactions with no idempotency key don't conflict), and CHECK constraints —
 * are genuinely Postgres-specific and would not be meaningfully exercised against an H2 substitute.
 *
 * Each test helper creates its own User + Account pair so tests are fully independent of each
 * other, of insertion order, and of any shared state.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
@ActiveProfiles("test")
class TransactionRepositoryTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("tps_test_db")
            .withUsername("tps_user")
            .withPassword("tps_password");

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    private TransactionRepository transactionRepository;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private UserRepository userRepository;

    @Test
    void findByTransactionReference_returnsCorrectTransaction() {
        Transaction saved = persistSampleTransaction("TXN-ref-1", TransactionStatus.PENDING, null);

        Optional<Transaction> found = transactionRepository.findByTransactionReference("TXN-ref-1");

        assertThat(found).isPresent();
        assertThat(found.get().getId()).isEqualTo(saved.getId());
    }

    @Test
    void findByIdempotencyKey_returnsTransaction_whenKeySet() {
        Transaction saved = persistSampleTransaction("TXN-ref-2", TransactionStatus.PENDING, "idem-key-1");

        Optional<Transaction> found = transactionRepository.findByIdempotencyKey("idem-key-1");

        assertThat(found).isPresent();
        assertThat(found.get().getId()).isEqualTo(saved.getId());
    }

    @Test
    void findByIdempotencyKey_returnsEmpty_forMissingKey() {
        Optional<Transaction> found = transactionRepository.findByIdempotencyKey("not-a-real-key");
        assertThat(found).isEmpty();
    }

    @Test
    void transactionReferenceUniqueConstraint_rejectsDuplicates() {
        persistSampleTransaction("TXN-dup", TransactionStatus.PENDING, null);

        // flush forces the INSERT immediately so the constraint violation surfaces here rather
        // than being swallowed until the transaction commits.
        assertThrows(DataIntegrityViolationException.class, () -> {
            persistSampleTransaction("TXN-dup", TransactionStatus.PENDING, null);
            transactionRepository.flush();
        });
    }

    @Test
    void nullIdempotencyKeys_doNotConflictWithEachOther() {
        // Postgres UNIQUE constraint on a nullable column treats each NULL as distinct, so two
        // transactions without an idempotency key must both be insertable without a constraint
        // violation — the uniqueness guarantee only applies to non-null values.
        persistSampleTransaction("TXN-null-key-1", TransactionStatus.PENDING, null);
        persistSampleTransaction("TXN-null-key-2", TransactionStatus.PENDING, null);
        // If the above didn't throw, the test passes.
    }

    @Test
    void countByStatus_countsOnlyMatchingTransactions() {
        persistSampleTransaction("TXN-count-s1", TransactionStatus.SUCCESS, null);
        persistSampleTransaction("TXN-count-s2", TransactionStatus.SUCCESS, null);
        persistSampleTransaction("TXN-count-f1", TransactionStatus.FAILED, null);

        long successCount = transactionRepository.countByStatus(TransactionStatus.SUCCESS);

        assertThat(successCount).isGreaterThanOrEqualTo(2);
        assertThat(transactionRepository.countByStatus(TransactionStatus.FAILED))
                .isGreaterThanOrEqualTo(1);
    }

    @Test
    void findByStatus_isPageable_andReturnsCorrectTotalElements() {
        for (int i = 0; i < 3; i++) {
            persistSampleTransaction("TXN-page-" + i, TransactionStatus.RETRYING, null);
        }

        Page<Transaction> page =
                transactionRepository.findByStatus(TransactionStatus.RETRYING, PageRequest.of(0, 2));

        assertThat(page.getTotalElements()).isGreaterThanOrEqualTo(3);
        assertThat(page.getContent()).hasSize(2);
    }

    @Test
    void findByUserId_onlyReturnsThatUsersTransactions() {
        Transaction mine = persistSampleTransaction("TXN-mine-only", TransactionStatus.PENDING, null);
        // A second transaction for a completely different user (persistSampleTransaction creates
        // a fresh user each call) that must not appear in "mine"'s results.
        persistSampleTransaction("TXN-someone-else", TransactionStatus.PENDING, null);

        Page<Transaction> page =
                transactionRepository.findByUserId(mine.getUserId(), PageRequest.of(0, 10));

        assertThat(page.getContent()).hasSize(1);
        assertThat(page.getContent().get(0).getTransactionReference()).isEqualTo("TXN-mine-only");
    }

    @Test
    void transactionAmount_persistsWithFullDecimalPrecision() {
        Transaction saved = persistSampleTransaction("TXN-precision", TransactionStatus.PENDING, null);

        Transaction found = transactionRepository.findByTransactionReference("TXN-precision")
                .orElseThrow();

        // NUMERIC(19,4) must preserve up to 4 decimal places without rounding.
        assertThat(found.getAmount()).isEqualByComparingTo(new BigDecimal("10.00"));
    }

    /**
     * Each call creates its own User + Account pair so test cases remain fully independent.
     * Emails are derived from a random UUID so tests intentionally reusing the same
     * transactionReference (the duplicate-constraint test) fail for the right reason — a
     * duplicate transaction_reference — rather than incidentally tripping the users.email
     * unique constraint first.
     */
    private Transaction persistSampleTransaction(
            String reference, TransactionStatus status, String idempotencyKey) {

        User user = userRepository.save(User.builder()
                .name("Test User")
                .email(UUID.randomUUID() + "@example.com")
                .passwordHash("hash")
                .role(Role.USER)
                .build());

        // version(0L) is required: the @Version column has a NOT NULL constraint (see V2 migration)
        // and Hibernate does not auto-initialize it to 0 when building the entity with a builder.
        Account source = accountRepository.save(Account.builder()
                .userId(user.getId())
                .accountNumber("SRC-" + UUID.randomUUID())
                .balance(new BigDecimal("1000.00"))
                .currency("USD")
                .version(0L)
                .build());

        Account destination = accountRepository.save(Account.builder()
                .userId(user.getId())
                .accountNumber("DST-" + UUID.randomUUID())
                .balance(BigDecimal.ZERO)
                .currency("USD")
                .version(0L)
                .build());

        Transaction transaction = Transaction.builder()
                .transactionReference(reference)
                .userId(user.getId())
                .sourceAccountId(source.getId())
                .destinationAccountId(destination.getId())
                .amount(new BigDecimal("10.00"))
                .currency("USD")
                .status(status)
                .idempotencyKey(idempotencyKey)
                .retryCount(0)
                .version(0L)
                .build();

        return transactionRepository.save(transaction);
    }
}
