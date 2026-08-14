package com.anish.banking.bank.ledger.transfer.service;

import com.anish.banking.bank.auth.model.User;
import com.anish.banking.bank.auth.repository.UserRepository;
import com.anish.banking.bank.ledger.account.exception.AccountAccessDeniedException;
import com.anish.banking.bank.ledger.account.model.Account;
import com.anish.banking.bank.ledger.account.repository.AccountRepository;
import com.anish.banking.bank.ledger.transfer.dto.CreateTransferRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The direction-sensitive half of the IDOR fix: a caller may send money TO an account they
 * don't own (that's just... paying someone), but may never move money FROM one they don't own.
 * Enforced in TransferService, ahead of the idempotency check -- see requireSourceOwnership use
 * in transfer().
 */
@SpringBootTest
@Transactional
class TransferOwnershipTest {

    @Autowired AccountRepository accounts;
    @Autowired UserRepository users;
    @Autowired TransferService transferService;

    private Long newUser(String label) {
        return users.save(new User(label + "-" + UUID.randomUUID() + "@test.local", "unused-hash")).getId();
    }

    @Test
    void userCannotTransferFromAnotherUsersAccount() {
        Long userA = newUser("user-a");
        Long userB = newUser("user-b");
        Account bobsAccount = accounts.save(new Account("Bob", "CAD", userB));
        Account aliceDestination = accounts.save(new Account("Alice", "CAD", userA));

        CreateTransferRequest req = new CreateTransferRequest(
                bobsAccount.getId(), aliceDestination.getId(), new BigDecimal("50.00"));

        // userA tries to move money OUT of an account that isn't theirs
        assertThatThrownBy(() -> transferService.transfer(req, UUID.randomUUID().toString(), userA))
                .isInstanceOf(AccountAccessDeniedException.class);

        assertThat(accounts.findById(bobsAccount.getId()).orElseThrow().getBalance())
                .isEqualByComparingTo("0.00");
        assertThat(accounts.findById(aliceDestination.getId()).orElseThrow().getBalance())
                .isEqualByComparingTo("0.00");
    }

    @Test
    void userCanTransferToAnotherUsersAccount() {
        Long userA = newUser("user-a");
        Long userB = newUser("user-b");
        Account aliceSource = accounts.save(new Account("Alice", "CAD", userA));
        Account bobsDestination = accounts.save(new Account("Bob", "CAD", userB));

        Account funded = accounts.findById(aliceSource.getId()).orElseThrow();
        funded.credit(new BigDecimal("100.00"));
        accounts.save(funded);

        // userA moves money INTO bob's account -- allowed, this is just paying someone
        var response = transferService.transfer(
                new CreateTransferRequest(aliceSource.getId(), bobsDestination.getId(), new BigDecimal("30.00")),
                UUID.randomUUID().toString(), userA);

        assertThat(response.status()).isEqualTo("COMPLETED");
        assertThat(accounts.findById(bobsDestination.getId()).orElseThrow().getBalance())
                .isEqualByComparingTo("30.00");
    }
}
