package com.example.transactionprocessing;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Entry point for the Real-Time Transaction Processing System.
 *
 * EnableJpaAuditing powers the automatic createdAt/updatedAt population on BaseEntity.
 * EnableScheduling is required by the retry scheduler (see transaction.service.RetryService,
 * added in Part 4) which periodically re-publishes transactions stuck in RETRYING status.
 */
@SpringBootApplication
@EnableJpaAuditing
@EnableScheduling
public class TransactionProcessingSystemApplication {

    public static void main(String[] args) {
        SpringApplication.run(TransactionProcessingSystemApplication.class, args);
    }
}
