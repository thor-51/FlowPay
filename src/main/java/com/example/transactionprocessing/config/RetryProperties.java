package com.example.transactionprocessing.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app.retry")
@Getter
@Setter
public class RetryProperties {

    private int maxAttempts;
    private long initialBackoffMs;
    private double backoffMultiplier;
    private long maxBackoffMs;
    private long sweepIntervalMs;
}
