package com.example.transactionprocessing.controller;

import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.transactionprocessing.common.exception.GlobalExceptionHandler;
import com.example.transactionprocessing.common.exception.ResourceNotFoundException;
import com.example.transactionprocessing.security.CustomUserDetails;
import com.example.transactionprocessing.transaction.controller.TransactionController;
import com.example.transactionprocessing.transaction.dto.response.TransactionResponse;
import com.example.transactionprocessing.transaction.entity.Transaction;
import com.example.transactionprocessing.transaction.entity.TransactionStatus;
import com.example.transactionprocessing.transaction.mapper.TransactionMapper;
import com.example.transactionprocessing.transaction.service.CreateTransactionCommand;
import com.example.transactionprocessing.transaction.service.RetryService;
import com.example.transactionprocessing.transaction.service.TransactionService;
import com.example.transactionprocessing.user.entity.Role;
import com.example.transactionprocessing.user.entity.User;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * @WebMvcTest loads only the web layer for TransactionController, plus explicitly imported
 * infrastructure (GlobalExceptionHandler — needed so UnauthorizedAccountAccessException and
 * ResourceNotFoundException produce the correct ApiResponse-shaped body rather than a default
 * Spring error page; ControllerAdvice beans outside the tested controller's package are not
 * auto-detected in @WebMvcTest slices). The real JWT security filter chain is disabled
 * (addFilters = false): standing up JwtAuthenticationFilter and CustomUserDetailsService in a
 * narrow slice test adds real complexity without exercising anything this class is responsible
 * for. Instead, the authenticated principal is pushed directly onto SecurityContextHolder in
 * setUp() — exactly what JwtAuthenticationFilter would have done by the time the request
 * reaches this controller in production.
 *
 * Scope note: because SecurityConfig itself isn't loaded in this slice, @EnableMethodSecurity
 * (and therefore @PreAuthorize("hasRole('ADMIN')") on the retry endpoint) is NOT enforced here.
 * That authorization rule is intentionally not tested at this layer; covering it requires a full
 * @SpringBootTest with the real security configuration loaded.
 */
@WebMvcTest(controllers = TransactionController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
@ActiveProfiles("test")
class TransactionControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private TransactionService transactionService;

    @MockBean
    private RetryService retryService;

    @MockBean
    private TransactionMapper transactionMapper;

    private UUID userId;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        User user = User.builder()
                .id(userId)
                .name("Test User")
                .email("test@example.com")
                .passwordHash("irrelevant-for-this-test")
                .role(Role.USER)
                .build();
        CustomUserDetails currentUser = new CustomUserDetails(user);

        SecurityContextHolder.getContext()
                .setAuthentication(new UsernamePasswordAuthenticationToken(
                        currentUser, null, currentUser.getAuthorities()));
    }

    @AfterEach
    void tearDown() {
        // MDC-style thread-local cleanup: SecurityContext left behind here would leak into
        // unrelated tests run on the same thread in the same JVM.
        SecurityContextHolder.clearContext();
    }

    @Test
    void createTransaction_returns201WithMappedResponse() throws Exception {
        UUID sourceAccountId = UUID.randomUUID();
        UUID destinationAccountId = UUID.randomUUID();

        Transaction savedTransaction = Transaction.builder()
                .id(UUID.randomUUID())
                .transactionReference("TXN-abc")
                .sourceAccountId(sourceAccountId)
                .destinationAccountId(destinationAccountId)
                .amount(new BigDecimal("25.00"))
                .currency("USD")
                .status(TransactionStatus.PENDING)
                .retryCount(0)
                .build();

        TransactionResponse responseDto = TransactionResponse.builder()
                .id(savedTransaction.getId())
                .transactionReference("TXN-abc")
                .status(TransactionStatus.PENDING)
                .amount(new BigDecimal("25.00"))
                .currency("USD")
                .build();

        when(transactionService.createTransaction(any(CreateTransactionCommand.class)))
                .thenReturn(savedTransaction);
        when(transactionMapper.toResponse(savedTransaction)).thenReturn(responseDto);

        String requestBody =
                """
                {
                  "sourceAccountId": "%s",
                  "destinationAccountId": "%s",
                  "amount": 25.00,
                  "currency": "USD"
                }
                """.formatted(sourceAccountId, destinationAccountId);

        mockMvc.perform(post("/api/v1/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.transactionReference", is("TXN-abc")))
                .andExpect(jsonPath("$.data.status", is("PENDING")));
    }

    @Test
    void createTransaction_returns400_whenAmountIsZero() throws Exception {
        String requestBody =
                """
                {
                  "sourceAccountId": "%s",
                  "destinationAccountId": "%s",
                  "amount": 0,
                  "currency": "USD"
                }
                """.formatted(UUID.randomUUID(), UUID.randomUUID());

        mockMvc.perform(post("/api/v1/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success", is(false)));
    }

    @Test
    void createTransaction_returns400_whenCurrencyIsNotThreeLetters() throws Exception {
        String requestBody =
                """
                {
                  "sourceAccountId": "%s",
                  "destinationAccountId": "%s",
                  "amount": 10.00,
                  "currency": "US"
                }
                """.formatted(UUID.randomUUID(), UUID.randomUUID());

        mockMvc.perform(post("/api/v1/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success", is(false)));
    }

    @Test
    void createTransaction_returns400_whenBodyIsMissing() throws Exception {
        mockMvc.perform(post("/api/v1/transactions")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success", is(false)));
    }

    @Test
    void getById_returns200_whenCallerOwnsTheTransaction() throws Exception {
        UUID transactionId = UUID.randomUUID();
        Transaction transaction = Transaction.builder()
                .id(transactionId)
                .userId(userId)
                .transactionReference("TXN-mine")
                .status(TransactionStatus.SUCCESS)
                .build();
        TransactionResponse responseDto = TransactionResponse.builder()
                .id(transactionId)
                .transactionReference("TXN-mine")
                .status(TransactionStatus.SUCCESS)
                .build();

        when(transactionService.getById(transactionId)).thenReturn(transaction);
        when(transactionMapper.toResponse(transaction)).thenReturn(responseDto);

        mockMvc.perform(get("/api/v1/transactions/{id}", transactionId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.transactionReference", is("TXN-mine")));
    }

    @Test
    void getById_returns403_whenCallerDoesNotOwnTransactionAndIsNotAdmin() throws Exception {
        UUID transactionId = UUID.randomUUID();
        Transaction someoneElsesTransaction = Transaction.builder()
                .id(transactionId)
                .userId(UUID.randomUUID())   // deliberately not this test's userId
                .transactionReference("TXN-not-mine")
                .status(TransactionStatus.SUCCESS)
                .build();

        when(transactionService.getById(transactionId)).thenReturn(someoneElsesTransaction);

        mockMvc.perform(get("/api/v1/transactions/{id}", transactionId))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success", is(false)));
    }

    @Test
    void getByReference_returns404_whenTransactionDoesNotExist() throws Exception {
        when(transactionService.getByReference("TXN-missing"))
                .thenThrow(new ResourceNotFoundException("Transaction not found: TXN-missing"));

        mockMvc.perform(get("/api/v1/transactions/reference/{ref}", "TXN-missing"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success", is(false)))
                .andExpect(jsonPath("$.message").exists());
    }
}
