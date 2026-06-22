package com.example.transactionprocessing.transaction.controller;

import com.example.transactionprocessing.common.exception.UnauthorizedAccountAccessException;
import com.example.transactionprocessing.common.response.ApiResponse;
import com.example.transactionprocessing.security.CustomUserDetails;
import com.example.transactionprocessing.security.SecurityUtils;
import com.example.transactionprocessing.transaction.dto.request.CreateTransactionRequest;
import com.example.transactionprocessing.transaction.dto.response.TransactionResponse;
import com.example.transactionprocessing.transaction.entity.Transaction;
import com.example.transactionprocessing.transaction.mapper.TransactionMapper;
import com.example.transactionprocessing.transaction.service.CreateTransactionCommand;
import com.example.transactionprocessing.transaction.service.RetryService;
import com.example.transactionprocessing.transaction.service.TransactionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/transactions")
@RequiredArgsConstructor
@Tag(name = "Transactions", description = "Create and inspect transfers")
public class TransactionController {

    private static final String ROLE_ADMIN = "ROLE_ADMIN";
    private static final String IDEMPOTENCY_KEY_HEADER = "Idempotency-Key";

    private final TransactionService transactionService;
    private final RetryService retryService;
    private final TransactionMapper transactionMapper;

    @Operation(
            summary = "Create a transfer",
            description = "Pass an Idempotency-Key header to safely retry client-side without double-processing")
    @PostMapping
    public ResponseEntity<ApiResponse<TransactionResponse>> createTransaction(
            @Valid @RequestBody CreateTransactionRequest request,
            @Parameter(description = "Client-generated key; identical keys return the original result instead of creating a duplicate transfer")
            @RequestHeader(value = IDEMPOTENCY_KEY_HEADER, required = false) String idempotencyKey) {

        CustomUserDetails currentUser = SecurityUtils.getCurrentUser();

        CreateTransactionCommand command = new CreateTransactionCommand(
                currentUser.getId(),
                request.getSourceAccountId(),
                request.getDestinationAccountId(),
                request.getAmount(),
                request.getCurrency(),
                idempotencyKey);

        Transaction transaction = transactionService.createTransaction(command);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(transactionMapper.toResponse(transaction)));
    }

    @Operation(summary = "Get a transaction by id", description = "Accessible by the owning user or an ADMIN")
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<TransactionResponse>> getById(@PathVariable UUID id) {
        Transaction transaction = transactionService.getById(id);
        assertOwnerOrAdmin(transaction);
        return ResponseEntity.ok(ApiResponse.success(transactionMapper.toResponse(transaction)));
    }

    @Operation(summary = "Get a transaction by its human-readable reference")
    @GetMapping("/reference/{transactionReference}")
    public ResponseEntity<ApiResponse<TransactionResponse>> getByReference(@PathVariable String transactionReference) {
        Transaction transaction = transactionService.getByReference(transactionReference);
        assertOwnerOrAdmin(transaction);
        return ResponseEntity.ok(ApiResponse.success(transactionMapper.toResponse(transaction)));
    }

    @Operation(
            summary = "List transactions",
            description = "A USER sees only their own transactions; an ADMIN sees every transaction")
    @GetMapping
    public ResponseEntity<ApiResponse<Page<TransactionResponse>>> list(
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {

        CustomUserDetails currentUser = SecurityUtils.getCurrentUser();
        Page<Transaction> page = isAdmin(currentUser)
                ? transactionService.getAll(pageable)
                : transactionService.getForUser(currentUser.getId(), pageable);

        return ResponseEntity.ok(ApiResponse.success(page.map(transactionMapper::toResponse)));
    }

    /**
     * Lives under /transactions (matching the brief's URL list) but is admin-only in practice —
     * the spec's security section reserves manual retry for ADMIN, even though this particular
     * endpoint path sits alongside the USER-facing transaction endpoints rather than under
     * /admin/**.
     */
    @Operation(summary = "Manually retry a FAILED or DEAD_LETTERED transaction (ADMIN only)")
    @PostMapping("/{id}/retry")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> retry(@PathVariable UUID id) {
        retryService.manualRetry(id);
        return ResponseEntity.accepted().body(ApiResponse.success(null, "Transaction re-queued for processing"));
    }

    private void assertOwnerOrAdmin(Transaction transaction) {
        CustomUserDetails currentUser = SecurityUtils.getCurrentUser();
        boolean isOwner = transaction.getUserId().equals(currentUser.getId());
        if (!isOwner && !isAdmin(currentUser)) {
            throw new UnauthorizedAccountAccessException("You do not have access to this transaction");
        }
    }

    private boolean isAdmin(CustomUserDetails user) {
        return user.getAuthorities().stream().anyMatch(authority -> authority.getAuthority().equals(ROLE_ADMIN));
    }
}
