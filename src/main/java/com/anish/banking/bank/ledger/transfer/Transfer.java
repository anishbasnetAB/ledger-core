package com.anish.banking.bank.ledger.transfer;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Entity
@Table(name = "transfer")
public class Transfer {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "source_account_id", nullable = false)
    private Long sourceAccountId;

    @Column(name = "destination_account_id", nullable = false)
    private Long destinationAccountId;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    private TransferStatus status;

    @Column(name = "created_at", insertable = false, updatable = false)
    private OffsetDateTime createdAt;

    protected Transfer() {}

    public Transfer(Long sourceAccountId, Long destinationAccountId, BigDecimal amount, TransferStatus status) {
        this.sourceAccountId = sourceAccountId;
        this.destinationAccountId = destinationAccountId;
        this.amount = amount;
        this.status = status;
    }

    public Long getId() {
        return id;
    }

    public Long getSourceAccountId() {
        return sourceAccountId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public Long getDestinationAccountId() {
        return destinationAccountId;
    }

    public TransferStatus getStatus() {
        return status;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }
}