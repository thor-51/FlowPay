package com.example.transactionprocessing;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Entry point for the Real-Time Transaction Processing System.
 *
 * JPA auditing (createdAt/updatedAt on BaseEntity) is configured separately in
 * config.JpaAuditingConfig, rather than here, so that @WebMvcTest slices don't
 * try to wire up auditing infrastructure that depends on an EntityManagerFactory
 * the slice never loads.
 * EnableScheduling is required by the retry scheduler (see transaction.service.RetryService,
 * added in Part 4) which periodically re-publishes transactions stuck in RETRYING status.
 */
@SpringBootApplication
@EnableScheduling
public class TransactionProcessingSystemApplication {

    public static void main(String[] args) {
        SpringApplication.run(TransactionProcessingSystemApplication.class, args);
    }
}