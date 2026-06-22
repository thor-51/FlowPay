package com.example.transactionprocessing.common.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Not retryable: more attempts will not conjure funds into the account. Caught specifically by
 * TransactionProcessingService to route straight to FAILED instead of burning the retry budget.
 */
@ResponseStatus(HttpStatus.UNPROCESSABLE_ENTITY)
public class InsufficientBalanceException extends RuntimeException {

    public InsufficientBalanceException(String message) {
        super(message);
    }
}
