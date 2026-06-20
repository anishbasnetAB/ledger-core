package com.anish.banking.bank.ledger.ledger;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.OffsetDateTime;


@Entity
@Table(name="ledger_entry")
public class LedgerEntry {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(name="account_id", nullable = false)
    private Long accountId;

    @Enumerated(EnumType.STRING)
    @Column(name = "entry_type", nullable = false)
    private EntryType entryType;

    @Column(name="amount",nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(name = "created_at", insertable = false, updatable = false)
    private OffsetDateTime createdAt;

    protected LedgerEntry() {}

    public LedgerEntry(Long accountId, EntryType entryType, BigDecimal amount) {
        this.accountId = accountId;
        this.entryType = entryType;
        this.amount = amount;
    }
}
