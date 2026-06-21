package com.anish.banking.bank.ledger.account;

import com.anish.banking.bank.ledger.ledger.LedgerEntryRepository;
import com.anish.banking.bank.ledger.transfer.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
class DoubleEntryInvariantTest {

    @Autowired AccountRepository accounts;
    @Autowired LedgerEntryRepository ledger;
    @Autowired TransferRepository transfers;
    @Autowired BalanceService balanceService;
    @Autowired TransferService transferService;

    @Test
    void everyMovementNetsToZeroAndStoredBalanceMatchesDerived() {
        // --- arrange: two customer accounts + the settlement account ---
        Account alice = accounts.save(new Account("Alice", "CAD"));
        Account bob   = accounts.save(new Account("Bob", "CAD"));
        Account settlement = accounts
                .findByAccountTypeAndCurrency(AccountType.SETTLEMENT, "CAD")
                .orElseThrow();

        // capture movements that already exist, so we isolate only the ones THIS test creates
        Set<Long> preExistingMovementIds = transfers.findAll().stream()
                .map(Transfer::getId)
                .collect(Collectors.toSet());

        // --- act: a deposit, a withdrawal, a transfer ---
        balanceService.deposit(alice.getId(), new BigDecimal("200.00"));
        balanceService.withdraw(alice.getId(), new BigDecimal("50.00"));
        transferService.transfer(new CreateTransferRequest(
                alice.getId(), bob.getId(), new BigDecimal("75.00")),
                java.util.UUID.randomUUID().toString());

        // --- assert 1: exactly the three movements this test created, each nets to zero ---
        Set<Long> createdMovementIds = transfers.findAll().stream()
                .map(Transfer::getId)
                .filter(id -> !preExistingMovementIds.contains(id))
                .collect(Collectors.toSet());

        assertThat(createdMovementIds)
                .as("deposit + withdraw + transfer should create exactly 3 movements")
                .hasSize(3);

        createdMovementIds.forEach(id -> {
            BigDecimal net = ledger.sumSignedAmountByTransferId(id);
            assertThat(net)
                    .as("movement %s should net to zero", id)
                    .isEqualByComparingTo(BigDecimal.ZERO);
        });

        // --- assert 2: stored balance == derived balance, for EVERY account touched ---
        Account freshAlice = accounts.findById(alice.getId()).orElseThrow();
        Account freshBob = accounts.findById(bob.getId()).orElseThrow();
        Account freshSettlement = accounts.findById(settlement.getId()).orElseThrow();

        assertThat(ledger.deriveBalance(freshAlice.getId()))
                .as("Alice stored balance should match derived balance")
                .isEqualByComparingTo(freshAlice.getBalance());
        assertThat(ledger.deriveBalance(freshBob.getId()))
                .as("Bob stored balance should match derived balance")
                .isEqualByComparingTo(freshBob.getBalance());
        assertThat(ledger.deriveBalance(freshSettlement.getId()))
                .as("Settlement stored balance should match derived balance")
                .isEqualByComparingTo(freshSettlement.getBalance());

        // --- assert 3 (concrete arithmetic): alice's balance is right ---
        // alice: +200 deposit, -50 withdraw, -75 transfer-out = 75.00
        assertThat(freshAlice.getBalance())
                .as("Alice balance should be 75.00")
                .isEqualByComparingTo(new BigDecimal("75.00"));
    }
}