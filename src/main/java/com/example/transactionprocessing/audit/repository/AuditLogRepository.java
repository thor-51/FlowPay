package com.example.transactionprocessing.audit.repository;

import com.example.transactionprocessing.audit.entity.AuditLog;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuditLogRepository extends JpaRepository<AuditLog, UUID> {

    List<AuditLog> findByTransactionIdOrderByCreatedAtAsc(UUID transactionId);
}
