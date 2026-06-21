package com.anish.banking.bank.ledger.ledger;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;

// Repository for LedgerEntry records that provides standard JPA CRUD operations
// and custom queries for reconciliation/testing.
public interface LedgerEntryRepository extends JpaRepository<LedgerEntry, Long> {

    // Derives the balance by summing entries:
    // CREDIT adds, DEBIT subtracts.
    // COALESCE returns 0 instead of null when an account has no ledger entries.
    @Query(value = """
        SELECT COALESCE(SUM(CASE WHEN entry_type = 'CREDIT' THEN amount ELSE -amount END), 0)
        FROM ledger_entry
        WHERE account_id = :accountId
        """, nativeQuery = true)
    BigDecimal deriveBalance(@Param("accountId") Long accountId);

    @Query(value = """
    SELECT COALESCE(SUM(CASE WHEN entry_type = 'CREDIT' THEN amount ELSE -amount END), 0)
    FROM ledger_entry
    WHERE transfer_id = :transferId
    """, nativeQuery = true)
    BigDecimal sumSignedAmountByTransferId(@Param("transferId") Long transferId);

    long countByAccountIdAndEntryType(Long accountId, EntryType entryType);
}