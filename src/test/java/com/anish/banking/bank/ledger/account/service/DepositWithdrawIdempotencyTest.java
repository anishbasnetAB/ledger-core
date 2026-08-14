package com.anish.banking.bank.ledger.account.service;

import com.anish.banking.bank.auth.model.User;
import com.anish.banking.bank.auth.repository.UserRepository;
import com.anish.banking.bank.ledger.account.dto.BalanceResponse;
import com.anish.banking.bank.ledger.account.model.Account;
import com.anish.banking.bank.ledger.account.repository.AccountRepository;
import com.anish.banking.bank.ledger.idempotency.exception.IdempotencyKeyConflictException;
import com.anish.banking.bank.ledger.ledger.model.EntryType;
import com.anish.banking.bank.ledger.ledger.repository.LedgerEntryRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Same idempotency contract TransferService already has (IdempotencyTest /
 * IdempotencyConcurrencyTest), proven here for BalanceService.deposit()/withdraw() instead.
 */
@SpringBootTest
class DepositWithdrawIdempotencyTest {

    @Autowired AccountRepository accounts;
    @Autowired UserRepository users;
    @Autowired LedgerEntryRepository ledger;
    @Autowired BalanceService balanceService;

    private Long newOwner() {
        return users.save(new User("owner-" + UUID.randomUUID() + "@test.local", "unused-hash")).getId();
    }

    @Test
    @Transactional
    void sameKeyReplaysDepositAndMovesMoneyOnce() {
        Long ownerId = newOwner();
        Account account = accounts.save(new Account("Deposit Idempotency Test", "CAD", ownerId));
        String key = "deposit-key-123";

        BalanceResponse first = balanceService.deposit(account.getId(), new BigDecimal("100.00"), key, ownerId);
        BalanceResponse second = balanceService.deposit(account.getId(), new BigDecimal("100.00"), key, ownerId);

        assertThat(first).isEqualTo(second);

        long creditRows = ledger.countByAccountIdAndEntryType(account.getId(), EntryType.CREDIT);
        assertThat(creditRows).isEqualTo(1);

        Account fresh = accounts.findById(account.getId()).orElseThrow();
        assertThat(fresh.getBalance()).isEqualByComparingTo("100.00");
    }

    @Test
    @Transactional
    void sameKeyReplaysWithdrawAndMovesMoneyOnce() {
        Long ownerId = newOwner();
        Account account = accounts.save(new Account("Withdraw Idempotency Test", "CAD", ownerId));
        balanceService.deposit(account.getId(), new BigDecimal("500.00"), UUID.randomUUID().toString(), ownerId);

        String key = "withdraw-key-456";
        BalanceResponse first = balanceService.withdraw(account.getId(), new BigDecimal("100.00"), key, ownerId);
        BalanceResponse second = balanceService.withdraw(account.getId(), new BigDecimal("100.00"), key, ownerId);

        assertThat(first).isEqualTo(second);

        long debitRows = ledger.countByAccountIdAndEntryType(account.getId(), EntryType.DEBIT);
        assertThat(debitRows).isEqualTo(1);

        Account fresh = accounts.findById(account.getId()).orElseThrow();
        assertThat(fresh.getBalance()).isEqualByComparingTo("400.00");
    }

    @Test
    @Transactional
    void sameKeyDifferentAmountIsRejected() {
        Long ownerId = newOwner();
        Account account = accounts.save(new Account("Conflict Test", "CAD", ownerId));
        String key = "conflict-key-789";

        balanceService.deposit(account.getId(), new BigDecimal("100.00"), key, ownerId);

        assertThatThrownBy(() -> balanceService.deposit(account.getId(), new BigDecimal("999.00"), key, ownerId))
                .isInstanceOf(IdempotencyKeyConflictException.class);
    }

    // Not @Transactional (same reason as IdempotencyConcurrencyTest): each racing thread needs
    // its own real commit for the idempotency_key unique constraint to actually decide a winner.
    @Test
    void concurrentSameKeyDepositsMoveMoneyOnce() throws Exception {
        Long ownerId = newOwner();
        Account account = accounts.save(new Account("Concurrent Deposit Test", "CAD", ownerId));
        // unique per run, same reasoning as IdempotencyConcurrencyTest's race-key
        String key = "concurrent-deposit-key-" + UUID.randomUUID();

        int threadCount = 5;
        CountDownLatch readyLatch = new CountDownLatch(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);

        AtomicInteger successCount = new AtomicInteger();
        AtomicInteger failureCount = new AtomicInteger();

        var executor = Executors.newFixedThreadPool(threadCount);
        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    readyLatch.countDown();
                    startLatch.await();
                    balanceService.deposit(account.getId(), new BigDecimal("50.00"), key, ownerId);
                    successCount.incrementAndGet();
                } catch (Throwable ex) {
                    failureCount.incrementAndGet();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        assertThat(readyLatch.await(5, TimeUnit.SECONDS)).isTrue();
        startLatch.countDown();
        assertThat(doneLatch.await(10, TimeUnit.SECONDS)).isTrue();
        executor.shutdown();

        long creditRows = ledger.countByAccountIdAndEntryType(account.getId(), EntryType.CREDIT);
        assertThat(creditRows).isEqualTo(1);

        Account fresh = accounts.findById(account.getId()).orElseThrow();
        assertThat(fresh.getBalance()).isEqualByComparingTo("50.00");

        // exactly one racer won the unique idempotency_key insert; the rest hit the conflict
        // and rolled back (same shape as IdempotencyConcurrencyTest for transfers)
        assertThat(successCount.get()).isEqualTo(1);
        assertThat(failureCount.get()).isEqualTo(threadCount - 1);
    }
}
