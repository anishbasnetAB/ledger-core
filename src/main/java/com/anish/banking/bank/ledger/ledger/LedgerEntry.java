package com.anish.banking.bank.ledger.ledger;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Entity
@Table(name = "ledger_entry")
public class LedgerEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "account_id", nullable = false)
    private Long accountId;

    @Enumerated(EnumType.STRING)
    @Column(name = "entry_type", nullable = false)
    private EntryType entryType;

    @Column(name = "amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(name = "created_at", insertable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "transfer_id")
    private Long transferId;

    protected LedgerEntry() {
        // JPA
    }

    public LedgerEntry(Long accountId, EntryType entryType, BigDecimal amount) {
        this.accountId = accountId;
        this.entryType = entryType;
        this.amount = amount;
        this.transferId = null;
    }

    public LedgerEntry(Long accountId, EntryType entryType, BigDecimal amount, Long transferId) {
        this.accountId = accountId;
        this.entryType = entryType;
        this.amount = amount;
        this.transferId = transferId;
    }

    public static LedgerEntry credit(Long accountId, BigDecimal amount) {
        return new LedgerEntry(accountId, EntryType.CREDIT, amount);
    }

    public static LedgerEntry debit(Long accountId, BigDecimal amount) {
        return new LedgerEntry(accountId, EntryType.DEBIT, amount);
    }

    public static LedgerEntry credit(Long accountId, BigDecimal amount, Long transferId) {
        return new LedgerEntry(accountId, EntryType.CREDIT, amount, transferId);
    }

    public static LedgerEntry debit(Long accountId, BigDecimal amount, Long transferId) {
        return new LedgerEntry(accountId, EntryType.DEBIT, amount, transferId);
    }

    public Long getId() {
        return id;
    }

    public Long getAccountId() {
        return accountId;
    }

    public EntryType getEntryType() {
        return entryType;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public Long getTransferId() {
        return transferId;
    }
}