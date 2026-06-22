package com.example.transactionprocessing.common.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * The @ResponseStatus here is a fallback so this exception maps to a sane HTTP status even
 * before GlobalExceptionHandler (Part 5) is wired up; once that @ControllerAdvice exists it
 * takes precedence and adds a structured error body + error code on top of this status.
 */
@ResponseStatus(HttpStatus.CONFLICT)
public class DuplicateResourceException extends RuntimeException {

    public DuplicateResourceException(String message) {
        super(message);
    }
}
