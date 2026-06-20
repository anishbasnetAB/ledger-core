package com.anish.banking.bank.ledger.transfer;

import com.anish.banking.bank.ledger.ledger.LedgerEntryRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class TransferIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired LedgerEntryRepository ledger;

    @Test
    void transferMovesMoneyAndRecordsBalances() throws Exception {
        BigDecimal sourceBefore = ledger.deriveBalance(1L);
        BigDecimal destBefore   = ledger.deriveBalance(2L);

        mockMvc.perform(post("/transfers")
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
        BigDecimal sourceBefore = ledger.deriveBalance(1L);

        mockMvc.perform(post("/transfers")
                        .contentType("application/json")
                        .content("""
                    {"sourceAccountId":1,"destinationAccountId":2,"amount":99999.00}
                    """))
                .andExpect(status().isUnprocessableEntity());   // 422

        assertThat(ledger.deriveBalance(1L)).isEqualByComparingTo(sourceBefore);  // unchanged
    }
}