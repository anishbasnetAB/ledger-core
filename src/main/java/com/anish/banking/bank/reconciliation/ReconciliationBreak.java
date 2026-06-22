package com.anish.banking.bank.reconciliation;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Entity
@Table(name = "reconciliation_breaks")
public class ReconciliationBreak {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "account_id", nullable = false)
    private Long accountId;

    /** Balance recomputed from ledger_entry — the authoritative value. */
    @Column(name = "ledger_derived_balance", nullable = false, precision = 19, scale = 2)
    private BigDecimal ledgerDerivedBalance;

    /** The cached account.balance as observed at the most recent detection. */
    @Column(name = "stored_balance", nullable = false, precision = 19, scale = 2)
    private BigDecimal storedBalance;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ReconciliationBreakStatus status;

    /** First time this drift was seen — DB-defaulted on insert, never changes. */
    @Column(name = "detected_at", insertable = false, updatable = false)
    private OffsetDateTime detectedAt;

    /** Most recent time a sweep re-observed this still-open break. */
    @Column(name = "last_detected_at", nullable = false)
    private OffsetDateTime lastDetectedAt;

    /** When the account reconciled cleanly again; null while OPEN. */
    @Column(name = "resolved_at")
    private OffsetDateTime resolvedAt;

    protected ReconciliationBreak() {
        // JPA
    }

    public ReconciliationBreak(Long accountId, BigDecimal ledgerDerivedBalance, BigDecimal storedBalance) {
        this.accountId = accountId;
        this.ledgerDerivedBalance = ledgerDerivedBalance;
        this.storedBalance = storedBalance;
        this.status = ReconciliationBreakStatus.OPEN;
        // detected_at is filled by the DB default; last_detected_at has no default,
        // so seed it here to first-seen == last-seen at birth.
        this.lastDetectedAt = OffsetDateTime.now();
    }

    /**
     * A later sweep finds this account still drifting. Refresh the latest observed
     * balances and the last-seen timestamp while keeping first-seen and OPEN status.
     * Lifecycle changes live on the entity, mirroring how Account.credit()/debit()
     * own balance changes.
     */
    public void recordRedetection(BigDecimal ledgerDerivedBalance, BigDecimal storedBalance) {
        this.ledgerDerivedBalance = ledgerDerivedBalance;
        this.storedBalance = storedBalance;
        this.lastDetectedAt = OffsetDateTime.now();
    }

    /**
     * The account reconciles cleanly again — close this break. RESOLVED is terminal:
     * a future mismatch creates a new OPEN break rather than reopening this one.
     */
    public void markResolved() {
        this.status = ReconciliationBreakStatus.RESOLVED;
        this.resolvedAt = OffsetDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public Long getAccountId() {
        return accountId;
    }

    public BigDecimal getLedgerDerivedBalance() {
        return ledgerDerivedBalance;
    }

    public BigDecimal getStoredBalance() {
        return storedBalance;
    }

    public ReconciliationBreakStatus getStatus() {
        return status;
    }

    public OffsetDateTime getDetectedAt() {
        return detectedAt;
    }

    public OffsetDateTime getLastDetectedAt() {
        return lastDetectedAt;
    }

    public OffsetDateTime getResolvedAt() {
        return resolvedAt;
    }
}
