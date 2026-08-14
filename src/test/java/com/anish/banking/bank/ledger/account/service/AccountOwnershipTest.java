package com.anish.banking.bank.ledger.account.service;

import com.anish.banking.bank.auth.model.User;
import com.anish.banking.bank.auth.repository.UserRepository;
import com.anish.banking.bank.ledger.account.exception.AccountAccessDeniedException;
import com.anish.banking.bank.ledger.account.model.Account;
import com.anish.banking.bank.ledger.account.repository.AccountRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The IDOR fix: enforced in BalanceService itself (see requireOwnership there), not just in
 * AccountController -- these tests call the service directly, the same way a second, careless
 * entry point would, and prove the check still holds.
 */
@SpringBootTest
@Transactional
class AccountOwnershipTest {

    @Autowired AccountRepository accounts;
    @Autowired UserRepository users;
    @Autowired BalanceService balanceService;

    private Long newUser(String label) {
        return users.save(new User(label + "-" + UUID.randomUUID() + "@test.local", "unused-hash")).getId();
    }

    @Test
    void userCannotViewAnotherUsersAccountBalance() {
        Long userA = newUser("user-a");
        Long userB = newUser("user-b");
        Account bobsAccount = accounts.save(new Account("Bob", "CAD", userB));

        assertThatThrownBy(() -> balanceService.getBalance(bobsAccount.getId(), userA))
                .isInstanceOf(AccountAccessDeniedException.class);

        // the owner themself is unaffected
        assertThat(balanceService.getBalance(bobsAccount.getId(), userB).accountId())
                .isEqualTo(bobsAccount.getId());
    }

    @Test
    void userCannotDepositIntoAnotherUsersAccount() {
        Long userA = newUser("user-a");
        Long userB = newUser("user-b");
        Account bobsAccount = accounts.save(new Account("Bob", "CAD", userB));

        assertThatThrownBy(() -> balanceService.deposit(
                bobsAccount.getId(), new BigDecimal("50.00"), UUID.randomUUID().toString(), userA))
                .isInstanceOf(AccountAccessDeniedException.class);

        // nothing moved
        assertThat(accounts.findById(bobsAccount.getId()).orElseThrow().getBalance())
                .isEqualByComparingTo("0.00");
    }

    @Test
    void userCannotWithdrawFromAnotherUsersAccount() {
        Long userA = newUser("user-a");
        Long userB = newUser("user-b");
        Account bobsAccount = accounts.save(new Account("Bob", "CAD", userB));
        balanceService.deposit(bobsAccount.getId(), new BigDecimal("200.00"), UUID.randomUUID().toString(), userB);

        assertThatThrownBy(() -> balanceService.withdraw(
                bobsAccount.getId(), new BigDecimal("50.00"), UUID.randomUUID().toString(), userA))
                .isInstanceOf(AccountAccessDeniedException.class);

        // untouched -- still the amount Bob himself deposited
        assertThat(accounts.findById(bobsAccount.getId()).orElseThrow().getBalance())
                .isEqualByComparingTo("200.00");
    }

    @Test
    void nonexistentAccountAndSomeoneElsesAccountLookIdentical() {
        Long userA = newUser("user-a");
        Long userB = newUser("user-b");
        Account bobsAccount = accounts.save(new Account("Bob", "CAD", userB));

        // same exception, same message, whether the id belongs to someone else or to nobody
        // at all -- that's the anti-enumeration point of the fix, not an incidental detail.
        assertThatThrownBy(() -> balanceService.getBalance(bobsAccount.getId(), userA))
                .isInstanceOf(AccountAccessDeniedException.class)
                .hasMessage(new AccountAccessDeniedException().getMessage());

        assertThatThrownBy(() -> balanceService.getBalance(-1L, userA))
                .isInstanceOf(AccountAccessDeniedException.class)
                .hasMessage(new AccountAccessDeniedException().getMessage());
    }
}
