package com.example.transactionprocessing.security;

import com.example.transactionprocessing.common.response.ApiResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

/**
 * Handles authorization failures rejected by URL-pattern rules in SecurityConfig (e.g.
 * `.requestMatchers("/api/v1/admin/**").hasRole("ADMIN")`), which are enforced by Spring
 * Security's filter chain before the request ever reaches DispatcherServlet. Method-level
 * @PreAuthorize denials happen during controller dispatch instead, and are caught by
 * GlobalExceptionHandler's AccessDeniedException handler — the two together cover both layers,
 * both producing the same ApiResponse-shaped 403 body.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class JwtAccessDeniedHandler implements AccessDeniedHandler {

    private final ObjectMapper objectMapper;

    @Override
    public void handle(
            HttpServletRequest request, HttpServletResponse response, AccessDeniedException accessDeniedException)
            throws IOException, ServletException {

        log.warn("Access denied for {} {}: {}",
                request.getMethod(), request.getRequestURI(), accessDeniedException.getMessage());

        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);

        ApiResponse<Void> body = ApiResponse.error("You do not have permission to perform this action");
        objectMapper.writeValue(response.getOutputStream(), body);
    }
}
