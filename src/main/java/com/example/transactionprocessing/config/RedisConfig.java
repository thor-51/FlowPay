package com.example.transactionprocessing.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * Plain String/String RedisTemplate is sufficient here: IdempotencyService (Part 4) stores
 * "idempotency:{key}" -> a small JSON payload (transaction reference + status) it serializes
 * itself via Jackson, rather than relying on a generic-object Redis serializer. Keeping the
 * template typed to String avoids class-metadata serialization pitfalls across app restarts
 * and keeps the values human-readable with redis-cli during local debugging.
 */
@Configuration
public class RedisConfig {

    @Bean
    public RedisTemplate<String, String> redisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, String> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);
        template.setKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(new StringRedisSerializer());
        template.setHashKeySerializer(new StringRedisSerializer());
        template.setHashValueSerializer(new StringRedisSerializer());
        template.afterPropertiesSet();
        return template;
    }
}
