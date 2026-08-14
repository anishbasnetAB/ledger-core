package com.anish.banking.bank.ledger.account.model;

import com.anish.banking.bank.ledger.transfer.exception.InsufficientFundsException;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;


@Entity
@Table(name="account")
public class Account {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name="owner_name", nullable = false)
    private String ownerName;

    @Column(name="balance",nullable = false, precision = 19, scale = 2)
    private BigDecimal balance;

    @Enumerated(EnumType.STRING)
    @Column(name = "account_type", nullable = false)
    private AccountType accountType;

    @Column(name="currency",nullable = false, length = 3)
    @JdbcTypeCode(SqlTypes.CHAR)
    private String currency;

    // The user allowed to act on this account (view balance, deposit, withdraw, transfer out).
    // Every account has exactly one — see V13__add_owner_to_account.sql for how the pre-auth
    // seed/settlement accounts got backfilled. Not a JPA @ManyToOne on purpose: nothing here
    // needs to navigate to the User entity, just compare an id, so a plain FK column avoids
    // pulling auth's User into the ledger package for no reason.
    @Column(name = "owner_user_id", nullable = false)
    private Long ownerUserId;

    @Column(name="created_at", insertable = false, updatable=false)
    private OffsetDateTime createdAt;

    @Version
    private Long version;


    protected Account() {}            // JPA

    public Account(String ownerName, String currency, Long ownerUserId) {
        this.ownerName = ownerName;
        this.currency = currency;
        this.ownerUserId = ownerUserId;
        this.balance = BigDecimal.ZERO.setScale(2);
        this.accountType = AccountType.CUSTOMER;
    }

    public Long getId() {
        return id;
    }

    public String getOwnerName() {
        return ownerName;
    }

    public String getCurrency() {
        return currency;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public BigDecimal getBalance() {
        return balance;
    }

    public Long getVersion() {
        return version;
    }

    public Long getOwnerUserId() {
        return ownerUserId;
    }

    public void credit(BigDecimal amount) {
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Credit amount must be positive");
        }
        this.balance = this.balance.add(amount).setScale(2);
    }

    public void debit(BigDecimal amount) {
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Debit amount must be positive");
        }
        if (accountType == AccountType.CUSTOMER && this.balance.compareTo(amount) < 0) {
            throw new InsufficientFundsException(this.id);
        }
        this.balance = this.balance.subtract(amount).setScale(2);
    }



}
