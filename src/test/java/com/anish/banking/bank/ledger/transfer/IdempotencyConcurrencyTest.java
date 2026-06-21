package com.anish.banking.bank.ledger.transfer;

import com.anish.banking.bank.ledger.account.*;
import com.anish.banking.bank.ledger.ledger.EntryType;
import com.anish.banking.bank.ledger.ledger.LedgerEntryRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class IdempotencyConcurrencyTest {

    @Autowired AccountRepository accounts;
    @Autowired LedgerEntryRepository ledger;
    @Autowired TransferService transferService;

    @Test
    void concurrentSameKeyRequestsMoveMoneyOnce() throws Exception {
        Account alice = accounts.save(new Account("Alice", "CAD"));
        Account bob = accounts.save(new Account("Bob", "CAD"));
        Account aliceFunded = accounts.findById(alice.getId()).orElseThrow();
        aliceFunded.credit(new BigDecimal("500.00"));
        accounts.save(aliceFunded);

        CreateTransferRequest req = new CreateTransferRequest(
                alice.getId(), bob.getId(), new BigDecimal("100.00"));
        // Unique per run: this test isn't @Transactional, so idempotency_keys rows persist
        // in Postgres. A fixed key would clash with a prior run's row whose hash was computed
        // from that run's (now-different) account ids, yielding a spurious conflict.
        String key = "race-key-" + java.util.UUID.randomUUID();

        int threadCount = 2;
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
                    transferService.transfer(req, key);
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

        long bobCredits = ledger.countByAccountIdAndEntryType(bob.getId(), EntryType.CREDIT);
        assertThat(bobCredits).isEqualTo(1);

        Account freshBob = accounts.findById(bob.getId()).orElseThrow();
        assertThat(freshBob.getBalance()).isEqualByComparingTo("100.00");

        Account freshAlice = accounts.findById(alice.getId()).orElseThrow();
        assertThat(freshAlice.getBalance()).isEqualByComparingTo("400.00");

        // exactly one racer won; the other hit the duplicate-key conflict and rolled back
        assertThat(successCount.get()).isEqualTo(1);
        assertThat(failureCount.get()).isEqualTo(1);
    }
}