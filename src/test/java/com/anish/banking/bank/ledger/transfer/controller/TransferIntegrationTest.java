package com.anish.banking.bank.ledger.transfer.controller;

import com.anish.banking.bank.auth.security.JwtService;
import com.anish.banking.bank.ledger.account.repository.AccountRepository;
import com.anish.banking.bank.ledger.account.service.BalanceService;
import com.anish.banking.bank.ledger.ledger.repository.LedgerEntryRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional   // roll back per test: this hits the shared DB, otherwise each run permanently drains account 1
class TransferIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired LedgerEntryRepository ledger;
    @Autowired BalanceService balanceService;
    @Autowired AccountRepository accounts;
    @Autowired JwtService jwtService;

    // Endpoints require a valid JWT now; mint one directly rather than round-tripping
    // through /api/auth/register for every test. Account 1 is the seeded demo account, so the
    // token has to belong to whoever actually owns it -- otherwise every call here would just
    // 403 on ownership before ever reaching the transfer logic this test is about.
    private String bearerTokenForAccount1Owner() {
        Long ownerId = accounts.findById(1L).orElseThrow().getOwnerUserId();
        return "Bearer " + jwtService.generate(ownerId, "test@example.com", "USER");
    }

    @Test
    void transferMovesMoneyAndRecordsBalances() throws Exception {
        String bearer = bearerTokenForAccount1Owner();
        Long ownerId = accounts.findById(1L).orElseThrow().getOwnerUserId();

        // Self-fund the source so the test doesn't depend on accumulated seed balance (rolled back after).
        balanceService.deposit(1L, new BigDecimal("500.00"), java.util.UUID.randomUUID().toString(), ownerId);

        BigDecimal sourceBefore = ledger.deriveBalance(1L);
        BigDecimal destBefore   = ledger.deriveBalance(2L);

        mockMvc.perform(post("/api/transfers")
                        .header("Authorization", bearer)
                        .header("Idempotency-Key", java.util.UUID.randomUUID().toString())
                        .contentType("application/json")
                        .content("""
                    {"sourceAccountId":1,"destinationAccountId":2,"amount":100.00}
                    """))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.amount").value(100.00));

        assertThat(ledger.deriveBalance(1L)).isEqualByComparingTo(sourceBefore.subtract(new BigDecimal("100.00")));
        assertThat(ledger.deriveBalance(2L)).isEqualByComparingTo(destBefore.add(new BigDecimal("100.00")));
    }

    @Test
    void overTransferIsRejectedAndBalancesUntouched() throws Exception {
        String bearer = bearerTokenForAccount1Owner();
        BigDecimal sourceBefore = ledger.deriveBalance(1L);

        mockMvc.perform(post("/api/transfers")
                        .header("Authorization", bearer)
                        .header("Idempotency-Key", java.util.UUID.randomUUID().toString())
                        .contentType("application/json")
                        .content("""
                    {"sourceAccountId":1,"destinationAccountId":2,"amount":99999.00}
                    """))
                .andExpect(status().isUnprocessableEntity());   // 422

        assertThat(ledger.deriveBalance(1L)).isEqualByComparingTo(sourceBefore);  // unchanged
    }
}
