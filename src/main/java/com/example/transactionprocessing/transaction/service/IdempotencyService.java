package com.example.transactionprocessing.transaction.service;

import java.time.Duration;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

/**
 * Backs the Idempotency-Key header contract: the first request with a given key creates the
 * transaction and "owns" the key; every subsequent request with the same key — whether a genuine
 * retry by a flaky client or two requests racing each other — gets the original transaction's
 * reference back instead of creating a duplicate transfer.
 *
 * Redis is the fast path. The transactions.idempotency_key UNIQUE constraint in Postgres (see
 * V3 migration) is the backstop: if Redis is unavailable or a key is evicted before its TTL,
 * TransactionService's insert would still fail rather than silently double-process a transfer.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class IdempotencyService {

    private final RedisTemplate<String, String> redisTemplate;

    @Value("${app.idempotency.key-prefix}")
    private String keyPrefix;

    @Value("${app.idempotency.ttl-hours}")
    private long ttlHours;

    public Optional<String> getCachedTransactionReference(String idempotencyKey) {
        String value = redisTemplate.opsForValue().get(buildKey(idempotencyKey));
        return Optional.ofNullable(value);
    }

    /**
     * Atomically claims the idempotency key using Redis's SETNX semantics. Returns true if this
     * caller won the race and should proceed to create the transaction; false if another
     * concurrent request already claimed the key first, in which case the caller should look up
     * and return that request's result instead.
     */
    public boolean tryClaim(String idempotencyKey, String transactionReference) {
        Boolean claimed = redisTemplate
                .opsForValue()
                .setIfAbsent(buildKey(idempotencyKey), transactionReference, Duration.ofHours(ttlHours));
        boolean result = Boolean.TRUE.equals(claimed);
        log.debug("Idempotency claim attempt key={} claimed={}", idempotencyKey, result);
        return result;
    }

    private String buildKey(String idempotencyKey) {
        return keyPrefix + idempotencyKey;
    }
}
