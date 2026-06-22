package com.example.transactionprocessing.common.exception;

import com.example.transactionprocessing.common.response.ApiResponse;
import jakarta.validation.ConstraintViolationException;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * Single place every exception funnels through on its way back to the client, so every error
 * response shares the same ApiResponse envelope and an HTTP status that actually reflects what
 * went wrong, regardless of which layer the exception originated in.
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    /**
     * Every custom domain exception already carries a @ResponseStatus as a documented fallback
     * (see e.g. DuplicateResourceException's javadoc) so this single handler can read the status
     * back off the annotation instead of hardcoding it again per exception type — the status
     * lives in exactly one place, on the exception class itself.
     */
    @ExceptionHandler({
        DuplicateResourceException.class,
        ResourceNotFoundException.class,
        InvalidCredentialsException.class,
        InsufficientBalanceException.class,
        InvalidTransactionException.class,
        InvalidTransactionStateException.class,
        UnauthorizedAccountAccessException.class,
        IdempotencyInProgressException.class
    })
    public ResponseEntity<ApiResponse<Void>> handleDomainException(RuntimeException ex) {
        HttpStatus status = resolveStatus(ex);
        if (status.is5xxServerError()) {
            log.error("{}: {}", ex.getClass().getSimpleName(), ex.getMessage(), ex);
        } else {
            log.warn("{}: {}", ex.getClass().getSimpleName(), ex.getMessage());
        }
        return ResponseEntity.status(status).body(ApiResponse.error(ex.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .collect(Collectors.joining("; "));
        log.warn("Validation failed: {}", message);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiResponse.error(message));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleConstraintViolation(ConstraintViolationException ex) {
        String message = ex.getConstraintViolations().stream()
                .map(violation -> violation.getPropertyPath() + ": " + violation.getMessage())
                .collect(Collectors.joining("; "));
        log.warn("Constraint violation: {}", message);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiResponse.error(message));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> handleUnreadableBody(HttpMessageNotReadableException ex) {
        log.warn("Malformed request body: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error("Request body is missing or contains malformed JSON"));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponse<Void>> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        String message = "Invalid value for parameter '" + ex.getName() + "': " + ex.getValue();
        log.warn(message);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiResponse.error(message));
    }

    /**
     * Thrown by @PreAuthorize denials (e.g. a non-admin hitting the manual-retry endpoint) and
     * by any direct use of Spring Security's AccessDeniedException. Authentication failures
     * (missing/invalid token) never reach here — JwtAuthenticationEntryPoint (Part 3) handles
     * those earlier in the filter chain, before request dispatch.
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> handleAccessDenied(AccessDeniedException ex) {
        log.warn("Access denied: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ApiResponse.error("You do not have permission to perform this action"));
    }

    /**
     * Catch-all for anything not classified above. Deliberately does NOT forward ex.getMessage()
     * to the client here — only the custom domain exceptions handled above (whose messages are
     * written to be client-safe) get their text sent back. An unclassified exception could be
     * carrying a raw SQL error, a stack frame, or other internal detail, so the client gets a
     * generic message while the real detail goes to the logs for debugging.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnexpected(Exception ex) {
        log.error("Unhandled exception", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error("An unexpected error occurred. Please try again later."));
    }

    private HttpStatus resolveStatus(RuntimeException ex) {
        ResponseStatus annotation = ex.getClass().getAnnotation(ResponseStatus.class);
        return annotation != null ? annotation.value() : HttpStatus.INTERNAL_SERVER_ERROR;
    }
}
