package com.example.transactionprocessing.auth.service;

import com.example.transactionprocessing.auth.dto.AuthResponse;
import com.example.transactionprocessing.auth.dto.LoginRequest;
import com.example.transactionprocessing.auth.dto.RegisterRequest;
import com.example.transactionprocessing.common.exception.DuplicateResourceException;
import com.example.transactionprocessing.common.exception.InvalidCredentialsException;
import com.example.transactionprocessing.security.CustomUserDetails;
import com.example.transactionprocessing.security.CustomUserDetailsService;
import com.example.transactionprocessing.security.JwtTokenProvider;
import com.example.transactionprocessing.user.entity.Role;
import com.example.transactionprocessing.user.entity.User;
import com.example.transactionprocessing.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final AuthenticationManager authenticationManager;
    private final CustomUserDetailsService userDetailsService;

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        String normalizedEmail = request.getEmail().trim().toLowerCase();

        if (userRepository.existsByEmail(normalizedEmail)) {
            throw new DuplicateResourceException("An account with email " + normalizedEmail + " already exists");
        }

        User user = User.builder()
                .name(request.getName().trim())
                .email(normalizedEmail)
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .role(Role.USER)
                .build();

        user = userRepository.save(user);
        log.info("Registered new user id={} email={}", user.getId(), user.getEmail());

        return buildAuthResponse(new CustomUserDetails(user));
    }

    public AuthResponse login(LoginRequest request) {
        String normalizedEmail = request.getEmail().trim().toLowerCase();

        // Delegates credential checking to Spring Security's DaoAuthenticationProvider, which
        // uses CustomUserDetailsService + the configured BCryptPasswordEncoder under the hood.
        // This keeps password-comparison logic in one place instead of re-implementing it here.
        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(normalizedEmail, request.getPassword()));
        } catch (BadCredentialsException ex) {
            throw new InvalidCredentialsException("Invalid email or password");
        }

        CustomUserDetails userDetails = (CustomUserDetails) userDetailsService.loadUserByUsername(normalizedEmail);
        log.info("User logged in id={} email={}", userDetails.getId(), userDetails.getUsername());

        return buildAuthResponse(userDetails);
    }

    private AuthResponse buildAuthResponse(CustomUserDetails userDetails) {
        String accessToken = jwtTokenProvider.generateAccessToken(userDetails);
        String refreshToken = jwtTokenProvider.generateRefreshToken(userDetails);

        String role = userDetails.getAuthorities().stream()
                .findFirst()
                .map(authority -> authority.getAuthority().replace("ROLE_", ""))
                .orElse(Role.USER.name());

        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .tokenType("Bearer")
                .userId(userDetails.getId())
                .email(userDetails.getUsername())
                .role(role)
                .build();
    }
}
