package com.anish.banking.bank.ledger.ledger.repository;

import com.anish.banking.bank.auth.model.User;
import com.anish.banking.bank.ledger.account.model.Account;
import com.anish.banking.bank.ledger.ledger.model.EntryType;
import com.anish.banking.bank.ledger.ledger.model.LedgerEntry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class LedgerEntryRepositoryTest {

    @Autowired LedgerEntryRepository ledger;
    @Autowired TestEntityManager em;

    // account.owner_user_id is a real FK to users(id), so every account needs an owner row
    // to point at -- this test doesn't care who, just that one exists.
    private Long newOwner() {
        return em.persist(new User("owner-" + UUID.randomUUID() + "@test.local", "unused-hash")).getId();
    }

    private Long newAccount() {
        return em.persist(new Account("Test Owner", "CAD", newOwner())).getId();
    }

    @Test
    void sumsCreditsMinusDebits() {
        Long acct = newAccount();
        em.persist(new LedgerEntry(acct, EntryType.CREDIT, new BigDecimal("1000.00")));
        em.persist(new LedgerEntry(acct, EntryType.DEBIT,  new BigDecimal("250.00")));
        em.flush();

        assertThat(ledger.deriveBalance(acct)).isEqualByComparingTo("750.00");
    }

    @Test
    void emptyAccountReturnsZero() {
        Long acct = newAccount();
        em.flush();
        assertThat(ledger.deriveBalance(acct)).isEqualByComparingTo("0.00");
    }

    @Test
    void onlyCountsTheGivenAccount() {
        Long a1 = newAccount();
        Long a2 = newAccount();
        em.persist(new LedgerEntry(a1, EntryType.CREDIT, new BigDecimal("500.00")));
        em.persist(new LedgerEntry(a2, EntryType.CREDIT, new BigDecimal("999.00")));
        em.flush();

        assertThat(ledger.deriveBalance(a1)).isEqualByComparingTo("500.00");
    }
}
