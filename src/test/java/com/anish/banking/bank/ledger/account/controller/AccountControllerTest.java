package com.anish.banking.bank.ledger.account.controller;

import com.anish.banking.bank.auth.security.JwtService;
import com.anish.banking.bank.auth.security.SecurityConfig;
import com.anish.banking.bank.ledger.account.dto.AccountResponse;
import com.anish.banking.bank.ledger.account.dto.BalanceResponse;
import com.anish.banking.bank.ledger.account.exception.AccountNotFoundException;
import com.anish.banking.bank.ledger.account.service.BalanceService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;



// @WebMvcTest only auto-detects controllers/filters, not arbitrary @Service/@Component beans,
// so the real security chain needs an explicit @Import to load with its actual dependencies
// (JwtService needs nothing but config properties, already present via the test context).
@WebMvcTest(AccountController.class)
@Import({SecurityConfig.class, JwtService.class})
class AccountControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired JwtService jwtService;

    @MockitoBean BalanceService balanceService;
    // SecurityConfig now also wires RateLimitFilter, which needs a StringRedisTemplate —
    // this slice doesn't autoconfigure Redis, so it's mocked purely to satisfy that
    // constructor dependency. None of these tests exercise rate limiting.
    @MockitoBean StringRedisTemplate redis;

    // BalanceService is mocked in this slice, so it never actually enforces ownership here
    // (that's covered against the real service in AccountOwnershipTest) -- the caller id just
    // needs to be SOME authenticated id for the request to reach the controller at all, so
    // stubs match it with anyLong() rather than a specific value.
    private String bearerToken() {
        return "Bearer " + jwtService.generate(1L, "test@example.com", "USER");
    }

    @Test
    void returnsBalanceAsJson() throws Exception {
        when(balanceService.getBalance(eq(1L), anyLong()))
                .thenReturn(new BalanceResponse(1L, "CAD", new BigDecimal("1000.00")));

        mockMvc.perform(get("/api/accounts/1/balance").header("Authorization", bearerToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountId").value(1))
                .andExpect(jsonPath("$.currency").value("CAD"))
                .andExpect(jsonPath("$.balance").value(1000.00));
    }

    @Test
    void returns404WhenAccountMissing() throws Exception {
        when(balanceService.getBalance(eq(99L), anyLong()))
                .thenThrow(new AccountNotFoundException(99L));

        mockMvc.perform(get("/api/accounts/99/balance").header("Authorization", bearerToken()))
                .andExpect(status().isNotFound());
    }

    @Test
    void createsAccountAndReturns201WithLocation() throws Exception {
        when(balanceService.createAccount(eq("Alice"), eq("CAD"), anyLong()))
                .thenReturn(new AccountResponse(7L, "Alice", "CAD", new BigDecimal("0.00")));

        mockMvc.perform(post("/api/accounts")
                        .header("Authorization", bearerToken())
                        .contentType("application/json")
                        .content("{\"ownerName\":\"Alice\",\"currency\":\"CAD\"}"))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/accounts/7"))
                .andExpect(jsonPath("$.id").value(7))
                .andExpect(jsonPath("$.ownerName").value("Alice"))
                .andExpect(jsonPath("$.currency").value("CAD"))
                .andExpect(jsonPath("$.balance").value(0.00));
    }

    @Test
    void rejectsBlankOwnerNameWith400() throws Exception {
        mockMvc.perform(post("/api/accounts")
                        .header("Authorization", bearerToken())
                        .contentType("application/json")
                        .content("{\"ownerName\":\"\",\"currency\":\"CAD\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsBadCurrencyWith400() throws Exception {
        mockMvc.perform(post("/api/accounts")
                        .header("Authorization", bearerToken())
                        .contentType("application/json")
                        .content("{\"ownerName\":\"Alice\",\"currency\":\"CANADA\"}"))
                .andExpect(status().isBadRequest());
    }
}
