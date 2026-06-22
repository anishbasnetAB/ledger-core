package com.anish.banking.bank.reconciliation;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record ReconciliationBreakResponse(
        Long id,
        Long accountId,
        BigDecimal ledgerDerivedBalance,
        BigDecimal storedBalance,
        ReconciliationBreakStatus status,
        OffsetDateTime detectedAt,
        OffsetDateTime lastDetectedAt,
        OffsetDateTime resolvedAt) {

    public static ReconciliationBreakResponse from(ReconciliationBreak entity) {
        return new ReconciliationBreakResponse(
                entity.getId(),
                entity.getAccountId(),
                entity.getLedgerDerivedBalance(),
                entity.getStoredBalance(),
                entity.getStatus(),
                entity.getDetectedAt(),
                entity.getLastDetectedAt(),
                entity.getResolvedAt());
    }
}
