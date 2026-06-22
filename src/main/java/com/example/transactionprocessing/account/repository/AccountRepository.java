package com.example.transactionprocessing.account.repository;

import com.example.transactionprocessing.account.entity.Account;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AccountRepository extends JpaRepository<Account, UUID> {

    Optional<Account> findByAccountNumber(String accountNumber);

    List<Account> findByUserId(UUID userId);

    boolean existsByAccountNumber(String accountNumber);

    /**
     * Acquires a row-level pessimistic write lock (SELECT ... FOR UPDATE) on the account.
     * Used by the transaction consumer to serialize concurrent debit/credit operations against
     * the same account. Callers MUST always lock the two accounts of a transfer in a fixed,
     * deterministic order (e.g. by account id) to avoid deadlocks between two transfers that
     * touch the same pair of accounts in opposite directions.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from Account a where a.id = :id")
    Optional<Account> findByIdForUpdate(@Param("id") UUID id);
}
