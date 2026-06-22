package com.example.transactionprocessing.transaction.dto.response;

import com.example.transactionprocessing.transaction.entity.TransactionStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class TransactionResponse {

    private UUID id;
    private String transactionReference;
    private UUID sourceAccountId;
    private UUID destinationAccountId;
    private BigDecimal amount;
    private String currency;
    private TransactionStatus status;
    private String failureReason;
    private Integer retryCount;
    private Instant processedAt;
    private Instant createdAt;
    private Instant updatedAt;
}
