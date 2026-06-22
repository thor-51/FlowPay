package com.example.transactionprocessing.account.controller;

import com.example.transactionprocessing.account.dto.AccountResponse;
import com.example.transactionprocessing.account.dto.CreateAccountRequest;
import com.example.transactionprocessing.account.entity.Account;
import com.example.transactionprocessing.account.mapper.AccountMapper;
import com.example.transactionprocessing.account.service.AccountService;
import com.example.transactionprocessing.common.exception.UnauthorizedAccountAccessException;
import com.example.transactionprocessing.common.response.ApiResponse;
import com.example.transactionprocessing.security.CustomUserDetails;
import com.example.transactionprocessing.security.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/accounts")
@RequiredArgsConstructor
@Tag(name = "Accounts", description = "Create and inspect accounts")
public class AccountController {

    private static final String ROLE_ADMIN = "ROLE_ADMIN";

    private final AccountService accountService;
    private final AccountMapper accountMapper;

    @Operation(summary = "Create an account for the authenticated user")
    @PostMapping
    public ResponseEntity<ApiResponse<AccountResponse>> createAccount(@Valid @RequestBody CreateAccountRequest request) {
        CustomUserDetails currentUser = SecurityUtils.getCurrentUser();
        Account account = accountService.createAccount(currentUser.getId(), request.getCurrency(), request.getInitialBalance());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(accountMapper.toResponse(account)));
    }

    @Operation(summary = "List the authenticated user's accounts")
    @GetMapping
    public ResponseEntity<ApiResponse<List<AccountResponse>>> getMyAccounts() {
        CustomUserDetails currentUser = SecurityUtils.getCurrentUser();
        List<AccountResponse> accounts = accountService.getAccountsForUser(currentUser.getId()).stream()
                .map(accountMapper::toResponse)
                .toList();
        return ResponseEntity.ok(ApiResponse.success(accounts));
    }

    @Operation(summary = "Get a single account by id", description = "Accessible by the owning user or an ADMIN")
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<AccountResponse>> getAccount(@PathVariable UUID id) {
        CustomUserDetails currentUser = SecurityUtils.getCurrentUser();
        Account account = accountService.getById(id);

        boolean isOwner = account.getUserId().equals(currentUser.getId());
        boolean isAdmin = currentUser.getAuthorities().stream()
                .anyMatch(authority -> authority.getAuthority().equals(ROLE_ADMIN));

        if (!isOwner && !isAdmin) {
            throw new UnauthorizedAccountAccessException("You do not have access to this account");
        }

        return ResponseEntity.ok(ApiResponse.success(accountMapper.toResponse(account)));
    }
}
