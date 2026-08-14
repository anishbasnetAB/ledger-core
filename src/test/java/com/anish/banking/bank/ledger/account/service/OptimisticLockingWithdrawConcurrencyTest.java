package com.anish.banking.bank.ledger.account.service;

import com.anish.banking.bank.auth.model.User;
import com.anish.banking.bank.auth.repository.UserRepository;
import com.anish.banking.bank.ledger.account.dto.BalanceResponse;
import com.anish.banking.bank.ledger.account.model.Account;
import com.anish.banking.bank.ledger.account.repository.AccountRepository;
import com.anish.banking.bank.ledger.ledger.model.EntryType;
import com.anish.banking.bank.ledger.ledger.repository.LedgerEntryRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class OptimisticLockingWithdrawConcurrencyTest {

    @Autowired
    private AccountRepository accounts;

    @Autowired
    private UserRepository users;

    @Autowired
    private BalanceService balanceService;

    @Autowired
    private LedgerEntryRepository ledger;

    @Test
    void concurrentWithdrawalsDoNotOverdrawAccount() throws Exception {
        Long ownerId = users.save(new User("owner-" + UUID.randomUUID() + "@test.local", "unused-hash")).getId();
        Account account = accounts.save(new Account("Concurrency Test Account", "CAD", ownerId));
        Long accountId = account.getId();

        balanceService.deposit(accountId, new BigDecimal("500.00"), UUID.randomUUID().toString(), ownerId);

        int threadCount = 10;
        BigDecimal withdrawAmount = new BigDecimal("100.00");

        CountDownLatch readyLatch = new CountDownLatch(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);

        AtomicInteger successCount = new AtomicInteger();
        AtomicInteger failureCount = new AtomicInteger();

        Queue<Throwable> failures = new ConcurrentLinkedQueue<>();

        var executor = Executors.newFixedThreadPool(threadCount);

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    readyLatch.countDown();
                    startLatch.await();

                    // each thread is a distinct withdrawal, not a retry of the same one — its
                    // own idempotency key, same as any two unrelated real requests would get
                    balanceService.withdraw(accountId, withdrawAmount, UUID.randomUUID().toString(), ownerId);

                    successCount.incrementAndGet();
                } catch (Throwable ex) {
                    failureCount.incrementAndGet();
                    failures.add(ex);
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        assertThat(readyLatch.await(5, TimeUnit.SECONDS)).isTrue();

        startLatch.countDown();

        assertThat(doneLatch.await(10, TimeUnit.SECONDS)).isTrue();

        executor.shutdown();

        BalanceResponse finalBalance = balanceService.getBalance(accountId, ownerId);

        BigDecimal expectedBalance = new BigDecimal("500.00")
                .subtract(withdrawAmount.multiply(BigDecimal.valueOf(successCount.get())));

        assertThat(successCount.get()).isBetween(1, 5);
        assertThat(failureCount.get()).isEqualTo(threadCount - successCount.get());

        assertThat(finalBalance.balance())
                .isEqualByComparingTo(expectedBalance);

        assertThat(finalBalance.balance())
                .isGreaterThanOrEqualTo(BigDecimal.ZERO);

        long successfulDebitRows = ledger.countByAccountIdAndEntryType(accountId, EntryType.DEBIT);

        assertThat(successfulDebitRows)
                .isEqualTo(successCount.get());

        System.out.println("successCount = " + successCount.get());
        System.out.println("failureCount = " + failureCount.get());

        failures.forEach(ex ->
                System.out.println(ex.getClass().getName() + ": " + ex.getMessage())
        );
    }
}
