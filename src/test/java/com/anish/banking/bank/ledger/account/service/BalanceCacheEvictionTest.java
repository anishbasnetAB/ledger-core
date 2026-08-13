package com.anish.banking.bank.ledger.account.service;

import com.anish.banking.bank.ledger.account.model.Account;
import com.anish.banking.bank.ledger.account.model.AccountType;
import com.anish.banking.bank.ledger.account.repository.AccountRepository;
import com.anish.banking.bank.ledger.ledger.model.EntryType;
import com.anish.banking.bank.ledger.ledger.model.LedgerEntry;
import com.anish.banking.bank.ledger.ledger.repository.LedgerEntryRepository;
import com.anish.banking.bank.ledger.transfer.dto.CreateTransferRequest;
import com.anish.banking.bank.ledger.transfer.service.TransferService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

// Proves the AFTER_COMMIT eviction wiring itself (not just "the code exists") — same reason
// TransferEventPublishingTest doesn't wrap its test methods in @Transactional: a real commit
// (or a real rollback) has to happen for AFTER_COMMIT semantics to mean anything.
@SpringBootTest
class BalanceCacheEvictionTest {

    @Autowired AccountRepository accounts;
    @Autowired BalanceService balanceService;
    @Autowired TransferService transferService;

    @MockitoBean StringRedisTemplate redis;
    @MockitoSpyBean LedgerEntryRepository ledgerSpy;

    @Test
    void depositEvictsBothCustomerAndSettlementCacheAfterCommit() {
        Account customer = accounts.save(new Account("Cache Deposit Test", "CAD"));
        Account settlement = accounts.findByAccountTypeAndCurrency(AccountType.SETTLEMENT, "CAD").orElseThrow();

        balanceService.deposit(customer.getId(), new BigDecimal("10.00"));

        verify(redis).delete("balance:" + customer.getId());
        verify(redis).delete("balance:" + settlement.getId());
    }

    @Test
    void withdrawEvictsBothCustomerAndSettlementCacheAfterCommit() {
        Account customer = accounts.save(new Account("Cache Withdraw Test", "CAD"));
        Account funded = accounts.findById(customer.getId()).orElseThrow();
        funded.credit(new BigDecimal("50.00"));
        accounts.save(funded);
        Account settlement = accounts.findByAccountTypeAndCurrency(AccountType.SETTLEMENT, "CAD").orElseThrow();

        balanceService.withdraw(customer.getId(), new BigDecimal("10.00"));

        verify(redis).delete("balance:" + customer.getId());
        verify(redis).delete("balance:" + settlement.getId());
    }

    @Test
    void transferEvictsBothSourceAndDestinationCacheAfterCommit() {
        Account source = accounts.save(new Account("Cache Transfer Source", "CAD"));
        Account funded = accounts.findById(source.getId()).orElseThrow();
        funded.credit(new BigDecimal("50.00"));
        accounts.save(funded);
        Account dest = accounts.save(new Account("Cache Transfer Dest", "CAD"));

        transferService.transfer(new CreateTransferRequest(source.getId(), dest.getId(), new BigDecimal("10.00")),
                UUID.randomUUID().toString());

        verify(redis).delete("balance:" + source.getId());
        verify(redis).delete("balance:" + dest.getId());
    }

    @Test
    void doesNotEvictCacheWhenDepositRollsBack() {
        Account customer = accounts.save(new Account("Cache Rollback Test", "CAD"));

        // same failure-injection technique as TransferAtomicityTest: force a mid-transaction
        // crash so the whole deposit rolls back
        doThrow(new RuntimeException("simulated failure"))
                .when(ledgerSpy).save(argThat(e -> e instanceof LedgerEntry le && le.getEntryType() == EntryType.DEBIT));

        assertThatThrownBy(() -> balanceService.deposit(customer.getId(), new BigDecimal("10.00")))
                .isInstanceOf(RuntimeException.class);

        verifyNoInteractions(redis);
    }

    @Test
    void doesNotEvictCacheWhenTransferRollsBack() {
        Account source = accounts.save(new Account("Cache Transfer Rollback Source", "CAD"));
        Account funded = accounts.findById(source.getId()).orElseThrow();
        funded.credit(new BigDecimal("50.00"));
        accounts.save(funded);
        Account dest = accounts.save(new Account("Cache Transfer Rollback Dest", "CAD"));

        doThrow(new RuntimeException("simulated failure"))
                .when(ledgerSpy).save(argThat(e -> e instanceof LedgerEntry le && le.getEntryType() == EntryType.CREDIT));

        assertThatThrownBy(() -> transferService.transfer(
                new CreateTransferRequest(source.getId(), dest.getId(), new BigDecimal("10.00")),
                UUID.randomUUID().toString()))
                .isInstanceOf(RuntimeException.class);

        verifyNoInteractions(redis);
    }
}
