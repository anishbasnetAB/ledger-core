package com.anish.banking.bank.ledger.account;

import com.anish.banking.bank.ledger.ledger.EntryType;
import com.anish.banking.bank.ledger.ledger.LedgerEntryRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.util.Queue;
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
    private BalanceService balanceService;

    @Autowired
    private LedgerEntryRepository ledger;

    @Test
    void concurrentWithdrawalsDoNotOverdrawAccount() throws Exception {
        Account account = accounts.save(new Account("Concurrency Test Account", "CAD"));
        Long accountId = account.getId();

        balanceService.deposit(accountId, new BigDecimal("500.00"));

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

                    balanceService.withdraw(accountId, withdrawAmount);

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

        BalanceResponse finalBalance = balanceService.getBalance(accountId);

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