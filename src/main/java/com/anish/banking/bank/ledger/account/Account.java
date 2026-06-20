package com.anish.banking.bank.ledger.account;
import jakarta.persistence.*;
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

    @Column(name="currency",nullable = false, length = 3)
    @JdbcTypeCode(SqlTypes.CHAR)
    private String currency;

    @Column(name="created_at", insertable = false, updatable=false)
    public OffsetDateTime createdAt;

    protected Account() {}            // JPA

    public Account(String ownerName, String currency) {
        this.ownerName = ownerName;
        this.currency = currency;
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

    private OffsetDateTime getCreatedAt() {
        return createdAt;
    }

}
