package com.anish.banking.bank.ledger.transfer;

import com.anish.banking.bank.ledger.ledger.EntryType;
import com.anish.banking.bank.ledger.ledger.LedgerEntry;
import com.anish.banking.bank.ledger.ledger.LedgerEntryRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doThrow;

@SpringBootTest
class TransferAtomicityTest {

    @Autowired TransferService transferService;
    @Autowired LedgerEntryRepository ledger;

    @MockitoSpyBean LedgerEntryRepository ledgerSpy;   // wraps the real repo; we override one call

    @Test
    void failureAfterDebitRollsBackEverything() {
        BigDecimal sourceBefore = ledger.deriveBalance(1L);
        BigDecimal destBefore   = ledger.deriveBalance(2L);

        // make the CREDIT save (the second entry) blow up, simulating a mid-transfer crash
        doThrow(new RuntimeException("simulated failure after debit"))
                .when(ledgerSpy).save(argThat(e ->
                        e instanceof LedgerEntry le && le.getEntryType() == EntryType.CREDIT));

        assertThatThrownBy(() ->
                transferService.transfer(new CreateTransferRequest(1L, 2L, new BigDecimal("100.00")),
                        java.util.UUID.randomUUID().toString()))
                .isInstanceOf(RuntimeException.class);

        // the whole transaction must have rolled back: balances exactly as before
        assertThat(ledger.deriveBalance(1L)).isEqualByComparingTo(sourceBefore);
        assertThat(ledger.deriveBalance(2L)).isEqualByComparingTo(destBefore);
    }
}