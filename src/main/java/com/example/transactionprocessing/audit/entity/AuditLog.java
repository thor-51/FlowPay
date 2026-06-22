package com.example.transactionprocessing.audit.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.hibernate.annotations.CreationTimestamp;

/**
 * Append-only audit trail for transaction lifecycle events. Unlike User/Account/Transaction,
 * this does not extend BaseEntity: it has no updatedAt because audit rows are immutable once
 * written (a CHECK against updates is a reasonable production hardening step, omitted here for
 * brevity but noted in the README).
 *
 * `metadata` is stored as a TEXT column holding a serialized JSON string rather than native
 * Postgres JSONB. JSONB would be a marginal win here (no DB-side querying of metadata is
 * required) and avoids pulling in an extra Hibernate user-type dependency just to write/read
 * JSON strings correctly through JDBC.
 */
@Entity
@Table(name = "audit_logs")
@Getter
@Setter
@ToString
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "transaction_id")
    private UUID transactionId;

    @Column(name = "event_type", nullable = false, length = 50)
    private String eventType;

    @Column(name = "message", nullable = false, length = 1000)
    private String message;

    @Column(name = "metadata", columnDefinition = "TEXT")
    private String metadata;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
