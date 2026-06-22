package com.anish.banking.bank.ledger.account;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;



@WebMvcTest(AccountController.class)
class AccountControllerTest {

    @Autowired MockMvc mockMvc;

    @MockitoBean BalanceService balanceService;

    @Test
    void returnsBalanceAsJson() throws Exception {
        when(balanceService.getBalance(1L))
                .thenReturn(new BalanceResponse(1L, "CAD", new BigDecimal("1000.00")));

        mockMvc.perform(get("/api/accounts/1/balance"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountId").value(1))
                .andExpect(jsonPath("$.currency").value("CAD"))
                .andExpect(jsonPath("$.balance").value(1000.00));
    }

    @Test
    void returns404WhenAccountMissing() throws Exception {
        when(balanceService.getBalance(99L))
                .thenThrow(new AccountNotFoundException(99L));

        mockMvc.perform(get("/api/accounts/99/balance"))
                .andExpect(status().isNotFound());
    }

    @Test
    void createsAccountAndReturns201WithLocation() throws Exception {
        when(balanceService.createAccount("Alice", "CAD"))
                .thenReturn(new AccountResponse(7L, "Alice", "CAD", new BigDecimal("0.00")));

        mockMvc.perform(post("/api/accounts")
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
                        .contentType("application/json")
                        .content("{\"ownerName\":\"\",\"currency\":\"CAD\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsBadCurrencyWith400() throws Exception {
        mockMvc.perform(post("/api/accounts")
                        .contentType("application/json")
                        .content("{\"ownerName\":\"Alice\",\"currency\":\"CANADA\"}"))
                .andExpect(status().isBadRequest());
    }
}