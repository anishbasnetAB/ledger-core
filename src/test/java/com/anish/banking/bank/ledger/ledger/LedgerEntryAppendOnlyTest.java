package com.anish.banking.bank.ledger.ledger;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Proves the append-only guarantee is enforced by the DATABASE (the
 * ledger_entry_append_only trigger), not merely by the absence of setters
 * in LedgerEntry. Raw UPDATE/DELETE issued straight at the table — bypassing
 * all application-level protection — must still fail.
 */
@SpringBootTest
@Transactional
class LedgerEntryAppendOnlyTest {

    @Autowired
    private LedgerEntryRepository ledger;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void ledgerEntryCannotBeUpdated() {
        LedgerEntry entry = ledger.saveAndFlush(
                LedgerEntry.credit(1L, new BigDecimal("1.00")));

        assertThatThrownBy(() ->
                jdbc.update("UPDATE ledger_entry SET amount = 999 WHERE id = ?", entry.getId()))
                .hasStackTraceContaining("append-only");
    }

    @Test
    void ledgerEntryCannotBeDeleted() {
        LedgerEntry entry = ledger.saveAndFlush(
                LedgerEntry.credit(1L, new BigDecimal("1.00")));

        assertThatThrownBy(() ->
                jdbc.update("DELETE FROM ledger_entry WHERE id = ?", entry.getId()))
                .hasStackTraceContaining("append-only");
    }
}