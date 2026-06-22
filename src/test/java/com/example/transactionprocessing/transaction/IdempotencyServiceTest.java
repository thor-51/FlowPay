package com.example.transactionprocessing.transaction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.transactionprocessing.transaction.service.IdempotencyService;
import java.time.Duration;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.test.context.ActiveProfiles;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

@ActiveProfiles("test")
@ExtendWith(MockitoExtension.class)
class IdempotencyServiceTest {

    @Mock
    private RedisTemplate<String, String> redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    private IdempotencyService idempotencyService;

    @BeforeEach
    void setUp() {
        idempotencyService = new IdempotencyService(redisTemplate);
        // key-prefix/ttl-hours are normally bound via @Value from application.yml; with no
        // Spring context in a pure Mockito unit test, they're set directly to match the
        // configured defaults (app.idempotency.key-prefix / app.idempotency.ttl-hours).
        ReflectionTestUtils.setField(idempotencyService, "keyPrefix", "idempotency:");
        ReflectionTestUtils.setField(idempotencyService, "ttlHours", 24L);

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    @Test
    void getCachedTransactionReference_returnsEmpty_whenKeyNotPresent() {
        when(valueOperations.get("idempotency:abc-123")).thenReturn(null);

        Optional<String> result = idempotencyService.getCachedTransactionReference("abc-123");

        assertThat(result).isEmpty();
    }

    @Test
    void getCachedTransactionReference_returnsCachedReference_whenKeyPresent() {
        when(valueOperations.get("idempotency:abc-123")).thenReturn("TXN-existing");

        Optional<String> result = idempotencyService.getCachedTransactionReference("abc-123");

        assertThat(result).contains("TXN-existing");
    }

    @Test
    void tryClaim_returnsTrue_whenKeyWasNotAlreadySet() {
        when(valueOperations.setIfAbsent(eq("idempotency:abc-123"), eq("TXN-new"), any(Duration.class)))
                .thenReturn(true);

        boolean claimed = idempotencyService.tryClaim("abc-123", "TXN-new");

        assertThat(claimed).isTrue();
    }

    @Test
    void tryClaim_returnsFalse_whenAnotherRequestAlreadyClaimedTheKey() {
        // This is the "lost the race" branch TransactionService relies on: a concurrent request
        // claimed the same idempotency key microseconds earlier.
        when(valueOperations.setIfAbsent(eq("idempotency:abc-123"), eq("TXN-new"), any(Duration.class)))
                .thenReturn(false);

        boolean claimed = idempotencyService.tryClaim("abc-123", "TXN-new");

        assertThat(claimed).isFalse();
    }

    @Test
    void tryClaim_usesConfiguredKeyPrefixAndTtl() {
        when(valueOperations.setIfAbsent(any(), any(), any())).thenReturn(true);

        idempotencyService.tryClaim("xyz", "TXN-xyz");

        verify(valueOperations).setIfAbsent("idempotency:xyz", "TXN-xyz", Duration.ofHours(24));
    }
}
