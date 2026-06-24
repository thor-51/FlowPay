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
import com.example.transactionprocessing.security.CustomUserDetailsService;
import com.example.transactionprocessing.security.JwtTokenProvider;
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
import java.lang.reflect.Field;
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

    @MockBean
    private JwtTokenProvider jwtTokenProvider;

    @MockBean
    private CustomUserDetailsService customUserDetailsService;

    private UUID userId;

    private void setId(Object entity, UUID id) throws Exception {
        Class<?> clazz = entity.getClass();
        while (clazz != null) {
            try {
                Field f = clazz.getDeclaredField("id");
                f.setAccessible(true);
                f.set(entity, id);
                return;
            } catch (NoSuchFieldException e) {
                clazz = clazz.getSuperclass();
            }
        }
        throw new NoSuchFieldException("id not found");
    }

    @BeforeEach
    void setUp() throws Exception {
        userId = UUID.randomUUID();
        User user = User.builder()
                .name("Test User")
                .email("test@example.com")
                .passwordHash("irrelevant")
                .role(Role.USER)
                .build();
        setId(user, userId);
        CustomUserDetails principal = new CustomUserDetails(user);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void createTransaction_returns201() throws Exception {
        UUID srcId = UUID.randomUUID();
        UUID dstId = UUID.randomUUID();
        Transaction tx = Transaction.builder()
                .transactionReference("TXN-abc")
                .sourceAccountId(srcId)
                .destinationAccountId(dstId)
                .amount(new BigDecimal("25.00"))
                .currency("USD")
                .status(TransactionStatus.PENDING)
                .retryCount(0)
                .version(0L)
                .build();
        TransactionResponse dto = TransactionResponse.builder()
                .transactionReference("TXN-abc")
                .status(TransactionStatus.PENDING)
                .amount(new BigDecimal("25.00"))
                .currency("USD")
                .build();
        when(transactionService.createTransaction(any(CreateTransactionCommand.class))).thenReturn(tx);
        when(transactionMapper.toResponse(tx)).thenReturn(dto);
        mockMvc.perform(post("/api/v1/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sourceAccountId\":\"" + srcId + "\",\"destinationAccountId\":\"" + dstId + "\",\"amount\":25.00,\"currency\":\"USD\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.transactionReference", is("TXN-abc")));
    }

    @Test
    void createTransaction_returns400_whenAmountIsZero() throws Exception {
        mockMvc.perform(post("/api/v1/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sourceAccountId\":\"" + UUID.randomUUID() + "\",\"destinationAccountId\":\"" + UUID.randomUUID() + "\",\"amount\":0,\"currency\":\"USD\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success", is(false)));
    }

    @Test
    void createTransaction_returns400_whenCurrencyInvalid() throws Exception {
        mockMvc.perform(post("/api/v1/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sourceAccountId\":\"" + UUID.randomUUID() + "\",\"destinationAccountId\":\"" + UUID.randomUUID() + "\",\"amount\":10.00,\"currency\":\"US\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success", is(false)));
    }

    @Test
    void createTransaction_returns400_whenBodyMissing() throws Exception {
        mockMvc.perform(post("/api/v1/transactions").contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success", is(false)));
    }

    @Test
    void getById_returns200_whenOwner() throws Exception {
        UUID txId = UUID.randomUUID();
        Transaction tx = Transaction.builder()
                .transactionReference("TXN-mine")
                .userId(userId)
                .status(TransactionStatus.SUCCESS)
                .version(0L)
                .build();
        setId(tx, txId);
        TransactionResponse dto = TransactionResponse.builder()
                .id(txId).transactionReference("TXN-mine").status(TransactionStatus.SUCCESS).build();
        when(transactionService.getById(txId)).thenReturn(tx);
        when(transactionMapper.toResponse(tx)).thenReturn(dto);
        mockMvc.perform(get("/api/v1/transactions/{id}", txId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.transactionReference", is("TXN-mine")));
    }

    @Test
    void getById_returns403_whenNotOwner() throws Exception {
        UUID txId = UUID.randomUUID();
        Transaction tx = Transaction.builder()
                .transactionReference("TXN-not-mine")
                .userId(UUID.randomUUID())
                .status(TransactionStatus.SUCCESS)
                .version(0L)
                .build();
        setId(tx, txId);
        when(transactionService.getById(txId)).thenReturn(tx);
        mockMvc.perform(get("/api/v1/transactions/{id}", txId))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success", is(false)));
    }

    @Test
    void getByReference_returns404_whenMissing() throws Exception {
        when(transactionService.getByReference("TXN-missing"))
                .thenThrow(new ResourceNotFoundException("Transaction not found: TXN-missing"));
        mockMvc.perform(get("/api/v1/transactions/reference/{ref}", "TXN-missing"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success", is(false)));
    }
}
