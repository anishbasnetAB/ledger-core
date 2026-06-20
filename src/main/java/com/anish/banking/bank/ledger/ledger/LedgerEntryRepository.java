package com.anish.banking.bank.ledger.ledger;

import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.math.BigDecimal;


//Repository for LedgerEntry records that provides standard JPA CRUD operations and a custom query to derive an account balance by adding credits and subtracting debits.

public interface LedgerEntryRepository extends JpaRepository<LedgerEntry, Long> {

    // Derives the balance by summing entries: credits add, debits subtract, with COALESCE so an empty ledger returns 0 instead of null.
    @Query(value = """
        SELECT COALESCE(SUM(CASE WHEN entry_type = 'CREDIT' THEN amount ELSE -amount END), 0)
        FROM ledger_entry WHERE account_id = :accountId
        """, nativeQuery = true)
    BigDecimal deriveBalance(@Param("accountId") Long accountId);
}