package com.example.transactionprocessing.account.entity;

import com.example.transactionprocessing.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

/**
 * An account holds a balance for a user. Concurrent transfers touching the same account
 * are the classic race-condition hazard here, so this entity carries a JPA @Version column
 * for optimistic locking as a first line of defense. The transaction consumer (Part 4) layers
 * pessimistic row locks (SELECT ... FOR UPDATE, via AccountRepository#findByIdForUpdate) on top
 * for the actual debit/credit critical section, since two accounts must be locked together and
 * optimistic retries alone would thrash badly under concurrent load on a "hot" account.
 */
@Entity
@Table(name = "accounts")
@Getter
@Setter
@ToString
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class Account extends BaseEntity {

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "account_number", nullable = false, unique = true)
    private String accountNumber;

    @Column(name = "balance", nullable = false, precision = 19, scale = 4)
    private BigDecimal balance;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;
}
