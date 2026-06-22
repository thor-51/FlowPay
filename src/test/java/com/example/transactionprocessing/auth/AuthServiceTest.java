package com.example.transactionprocessing.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.transactionprocessing.auth.dto.AuthResponse;
import com.example.transactionprocessing.auth.dto.LoginRequest;
import com.example.transactionprocessing.auth.dto.RegisterRequest;
import com.example.transactionprocessing.auth.service.AuthService;
import com.example.transactionprocessing.common.exception.DuplicateResourceException;
import com.example.transactionprocessing.common.exception.InvalidCredentialsException;
import com.example.transactionprocessing.security.CustomUserDetails;
import com.example.transactionprocessing.security.CustomUserDetailsService;
import com.example.transactionprocessing.security.JwtTokenProvider;
import com.example.transactionprocessing.user.entity.Role;
import com.example.transactionprocessing.user.entity.User;
import com.example.transactionprocessing.user.repository.UserRepository;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.test.context.ActiveProfiles;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;

@ActiveProfiles("test")
@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtTokenProvider jwtTokenProvider;

    @Mock
    private AuthenticationManager authenticationManager;

    @Mock
    private CustomUserDetailsService userDetailsService;

    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(
                userRepository, passwordEncoder, jwtTokenProvider, authenticationManager, userDetailsService);
    }

    @Test
    void register_hashesPasswordAndAssignsUserRole() {
        RegisterRequest request = new RegisterRequest();
        request.setName("Ada Lovelace");
        // Mixed-case email on the way in; AuthService is expected to normalize to lowercase
        // before checking uniqueness and persisting, so two registrations differing only by
        // email casing are treated as the same account.
        request.setEmail("Ada@Example.com");
        request.setPassword("supersecret1");

        when(userRepository.existsByEmail("ada@example.com")).thenReturn(false);
        when(passwordEncoder.encode("supersecret1")).thenReturn("hashed-password");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User savedArg = invocation.getArgument(0);
            savedArg.setId(UUID.randomUUID());
            return savedArg;
        });
        when(jwtTokenProvider.generateAccessToken(any(CustomUserDetails.class))).thenReturn("access-token");
        when(jwtTokenProvider.generateRefreshToken(any(CustomUserDetails.class))).thenReturn("refresh-token");

        AuthResponse response = authService.register(request);

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        User savedUser = userCaptor.getValue();

        assertThat(savedUser.getEmail()).isEqualTo("ada@example.com");
        assertThat(savedUser.getPasswordHash()).isEqualTo("hashed-password");
        assertThat(savedUser.getRole()).isEqualTo(Role.USER);

        assertThat(response.getAccessToken()).isEqualTo("access-token");
        assertThat(response.getRefreshToken()).isEqualTo("refresh-token");
        assertThat(response.getTokenType()).isEqualTo("Bearer");
        assertThat(response.getEmail()).isEqualTo("ada@example.com");
        assertThat(response.getRole()).isEqualTo("USER");
    }

    @Test
    void register_throwsDuplicateResourceException_whenEmailAlreadyRegistered() {
        RegisterRequest request = new RegisterRequest();
        request.setName("Ada Lovelace");
        request.setEmail("ada@example.com");
        request.setPassword("supersecret1");

        when(userRepository.existsByEmail("ada@example.com")).thenReturn(true);

        assertThatThrownBy(() -> authService.register(request))
                .isInstanceOf(DuplicateResourceException.class)
                .hasMessageContaining("ada@example.com");
    }

    @Test
    void login_returnsTokenPair_whenCredentialsAreValid() {
        LoginRequest request = new LoginRequest();
        request.setEmail("ada@example.com");
        request.setPassword("supersecret1");

        User user = User.builder()
                .id(UUID.randomUUID())
                .name("Ada Lovelace")
                .email("ada@example.com")
                .passwordHash("hashed-password")
                .role(Role.USER)
                .build();
        CustomUserDetails userDetails = new CustomUserDetails(user);

        when(userDetailsService.loadUserByUsername("ada@example.com")).thenReturn(userDetails);
        when(jwtTokenProvider.generateAccessToken(userDetails)).thenReturn("access-token");
        when(jwtTokenProvider.generateRefreshToken(userDetails)).thenReturn("refresh-token");

        AuthResponse response = authService.login(request);

        // Credential verification itself is delegated to Spring Security's
        // AuthenticationManager (which under the hood uses CustomUserDetailsService +
        // PasswordEncoder) rather than reimplemented in AuthService, so the test only verifies
        // that delegation happened, not the password comparison itself.
        verify(authenticationManager).authenticate(any(UsernamePasswordAuthenticationToken.class));

        assertThat(response.getAccessToken()).isEqualTo("access-token");
        assertThat(response.getUserId()).isEqualTo(user.getId());
        assertThat(response.getRole()).isEqualTo("USER");
    }

    @Test
    void login_throwsInvalidCredentialsException_whenAuthenticationManagerRejectsCredentials() {
        LoginRequest request = new LoginRequest();
        request.setEmail("ada@example.com");
        request.setPassword("wrong-password");

        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenThrow(new BadCredentialsException("Bad credentials"));

        assertThatThrownBy(() -> authService.login(request)).isInstanceOf(InvalidCredentialsException.class);
    }
}
