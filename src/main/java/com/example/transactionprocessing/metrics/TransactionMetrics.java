package com.example.transactionprocessing.metrics;

import com.example.transactionprocessing.transaction.entity.TransactionStatus;
import com.example.transactionprocessing.transaction.repository.TransactionRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Duration;
import org.springframework.stereotype.Component;

/**
 * Thin typed wrapper around Micrometer so the rest of the codebase (TransactionService,
 * TransactionProcessingService) records business metrics through a small API instead of poking
 * MeterRegistry directly in multiple places. Everything registered here surfaces automatically
 * at GET /actuator/prometheus per Micrometer's standard naming convention — dots become
 * underscores and a _total/_seconds suffix is appended for counters/timers respectively, so
 * e.g. "transaction.created" becomes "transaction_created_total" in scrape output.
 */
@Component
public class TransactionMetrics {

    private final Counter createdCounter;
    private final Counter successCounter;
    private final Counter failedCounter;
    private final Counter deadLetteredCounter;
    private final Counter retryScheduledCounter;
    private final Timer processingTimer;

    public TransactionMetrics(MeterRegistry meterRegistry, TransactionRepository transactionRepository) {
        this.createdCounter = Counter.builder("transaction.created")
                .description("Total transactions created via POST /api/v1/transactions")
                .register(meterRegistry);

        // A single "transaction.outcome" counter tagged by status, rather than three
        // differently-named counters, is the idiomatic Micrometer/Prometheus pattern here: it
        // lets a Grafana panel sum/group by the "status" label instead of needing one query per
        // metric name, and avoids a metric-name explosion as more outcomes get added later.
        this.successCounter = Counter.builder("transaction.outcome")
                .description("Terminal transaction outcomes by status")
                .tag("status", "success")
                .register(meterRegistry);

        this.failedCounter = Counter.builder("transaction.outcome")
                .description("Terminal transaction outcomes by status")
                .tag("status", "failed")
                .register(meterRegistry);

        this.deadLetteredCounter = Counter.builder("transaction.outcome")
                .description("Terminal transaction outcomes by status")
                .tag("status", "dead_lettered")
                .register(meterRegistry);

        this.retryScheduledCounter = Counter.builder("transaction.retry.scheduled")
                .description("Total retry attempts scheduled after a transient processing failure")
                .register(meterRegistry);

        this.processingTimer = Timer.builder("transaction.processing.duration")
                .description("Elapsed time from transaction creation (PENDING) to a terminal "
                        + "outcome (SUCCESS, FAILED, or DEAD_LETTERED) — includes Kafka queue "
                        + "time, any retry/backoff cycles, and the debit/credit critical section")
                .publishPercentileHistogram()
                .register(meterRegistry);

        // Live gauges, one per lifecycle status, computed from the database on every Prometheus
        // scrape rather than incremented/decremented by application code — so they reflect
        // ground truth and can never drift, including across app restarts or if this service is
        // ever scaled to multiple instances all sharing the same database.
        for (TransactionStatus status : TransactionStatus.values()) {
            Gauge.builder("transaction.status.count", transactionRepository,
                            repo -> repo.countByStatus(status))
                    .description("Current number of transactions in this status right now")
                    .tag("status", status.name().toLowerCase())
                    .register(meterRegistry);
        }
    }

    public void incrementCreated() {
        createdCounter.increment();
    }

    public void incrementSuccess() {
        successCounter.increment();
    }

    public void incrementFailed() {
        failedCounter.increment();
    }

    public void incrementDeadLettered() {
        deadLetteredCounter.increment();
    }

    public void incrementRetryScheduled() {
        retryScheduledCounter.increment();
    }

    public void recordProcessingDuration(Duration duration) {
        processingTimer.record(duration);
    }
}
