package com.example.transactionprocessing.transaction.controller;

import com.example.transactionprocessing.common.response.ApiResponse;
import com.example.transactionprocessing.transaction.dto.response.TransactionResponse;
import com.example.transactionprocessing.transaction.entity.Transaction;
import com.example.transactionprocessing.transaction.entity.TransactionStatus;
import com.example.transactionprocessing.transaction.mapper.TransactionMapper;
import com.example.transactionprocessing.transaction.service.TransactionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Everything here is also reachable through SecurityConfig's URL-level rule
 * (`/api/v1/admin/**` -> hasRole("ADMIN")); the class-level @PreAuthorize is additional
 * defense-in-depth so this still holds even if that URL pattern is ever loosened or this
 * controller is mounted under a different path during a refactor.
 */
@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin", description = "Administrative views over failed/dead-lettered transactions and system metrics")
public class AdminTransactionController {

    private final TransactionService transactionService;
    private final TransactionMapper transactionMapper;

    @Operation(summary = "List FAILED transactions")
    @GetMapping("/transactions/failed")
    public ResponseEntity<ApiResponse<Page<TransactionResponse>>> failed(
            @PageableDefault(size = 20, sort = "updatedAt", direction = Sort.Direction.DESC) Pageable pageable) {
        Page<Transaction> page = transactionService.getByStatus(TransactionStatus.FAILED, pageable);
        return ResponseEntity.ok(ApiResponse.success(page.map(transactionMapper::toResponse)));
    }

    @Operation(summary = "List DEAD_LETTERED transactions")
    @GetMapping("/transactions/dead-lettered")
    public ResponseEntity<ApiResponse<Page<TransactionResponse>>> deadLettered(
            @PageableDefault(size = 20, sort = "updatedAt", direction = Sort.Direction.DESC) Pageable pageable) {
        Page<Transaction> page = transactionService.getByStatus(TransactionStatus.DEAD_LETTERED, pageable);
        return ResponseEntity.ok(ApiResponse.success(page.map(transactionMapper::toResponse)));
    }

    @Operation(summary = "Aggregate transaction counts by status")
    @GetMapping("/metrics/summary")
    public ResponseEntity<ApiResponse<Map<TransactionStatus, Long>>> metricsSummary() {
        return ResponseEntity.ok(ApiResponse.success(transactionService.getStatusCounts()));
    }
}
