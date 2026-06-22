package com.example.transactionprocessing.account.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class AccountResponse {

    private UUID id;
    private String accountNumber;
    private BigDecimal balance;
    private String currency;
    private Instant createdAt;
}
