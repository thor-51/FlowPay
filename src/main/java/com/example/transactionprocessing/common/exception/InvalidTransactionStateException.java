package com.example.transactionprocessing.common.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * E.g. attempting to manually retry a transaction that is still PENDING/PROCESSING/SUCCESS.
 */
@ResponseStatus(HttpStatus.CONFLICT)
public class InvalidTransactionStateException extends RuntimeException {

    public InvalidTransactionStateException(String message) {
        super(message);
    }
}
