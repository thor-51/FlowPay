package com.example.transactionprocessing.transaction.repository;

import com.example.transactionprocessing.transaction.entity.Transaction;
import com.example.transactionprocessing.transaction.entity.TransactionStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TransactionRepository extends JpaRepository<Transaction, UUID> {

    Optional<Transaction> findByTransactionReference(String transactionReference);

    Optional<Transaction> findByIdempotencyKey(String idempotencyKey);

    Page<Transaction> findByUserId(UUID userId, Pageable pageable);

    Page<Transaction> findByStatus(TransactionStatus status, Pageable pageable);

    Page<Transaction> findByStatusIn(List<TransactionStatus> statuses, Pageable pageable);

    long countByStatus(TransactionStatus status);

    /**
     * Transactions sitting in RETRYING status whose updatedAt is older than the given
     * threshold are considered "due" for another attempt under the exponential backoff
     * schedule. Used by the scheduled retry sweep in RetryService (Part 4).
     */
    @Query("select t from Transaction t where t.status = com.example.transactionprocessing.transaction.entity.TransactionStatus.RETRYING "
            + "and t.updatedAt <= :dueBefore")
    List<Transaction> findRetryableDue(@Param("dueBefore") java.time.Instant dueBefore);
}
