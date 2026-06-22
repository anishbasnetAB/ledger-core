package com.anish.banking.bank.reconciliation;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ReconciliationBreakRepository extends JpaRepository<ReconciliationBreak, Long> {

    List<ReconciliationBreak> findByAccountId(Long accountId);

    // The operational invariant guarantees at most one OPEN break per account, so a
    // status-scoped lookup returns at most one row. This drives the four-way logic in
    // ReconciliationService#checkAccount (open / redetect / resolve / no-op).
    Optional<ReconciliationBreak> findByAccountIdAndStatus(Long accountId, ReconciliationBreakStatus status);

    List<ReconciliationBreak> findByStatus(ReconciliationBreakStatus status);
}
