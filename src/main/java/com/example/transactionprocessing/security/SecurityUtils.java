package com.example.transactionprocessing.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Small convenience wrapper so controllers (Part 5) don't each have to know how to dig the
 * principal out of the SecurityContext. Used, for example, to scope GET /api/v1/transactions
 * to the calling user's own transactions.
 */
public final class SecurityUtils {

    private SecurityUtils() {
    }

    public static CustomUserDetails getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof CustomUserDetails principal)) {
            throw new IllegalStateException("No authenticated user found in security context");
        }
        return principal;
    }
}
