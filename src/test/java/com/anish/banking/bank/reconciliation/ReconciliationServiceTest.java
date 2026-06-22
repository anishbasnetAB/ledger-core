package com.anish.banking.bank.reconciliation;

import com.anish.banking.bank.ledger.account.Account;
import com.anish.banking.bank.ledger.account.AccountRepository;
import com.anish.banking.bank.ledger.account.AccountType;
import com.anish.banking.bank.ledger.account.BalanceService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
class ReconciliationServiceTest {

    @Autowired AccountRepository accounts;
    @Autowired BalanceService balanceService;
    @Autowired ReconciliationService reconciliationService;
    @Autowired ReconciliationBreakRepository breaks;
    @Autowired JdbcTemplate jdbc;

    @PersistenceContext EntityManager em;

    @Test
    void reconciliationDetectsCorruptedAccountAndLeavesCleanAccountsAlone() {
        // --- arrange: two customer accounts + the settlement account ---
        Account alice = accounts.save(new Account("Recon Alice", "CAD"));
        Account bob = accounts.save(new Account("Recon Bob", "CAD"));
        Account settlement = accounts
                .findByAccountTypeAndCurrency(AccountType.SETTLEMENT, "CAD")
                .orElseThrow();

        Long aliceId = alice.getId();
        Long bobId = bob.getId();
        Long settlementId = settlement.getId();

        // fund both through the real service so stored == derived for each
        balanceService.deposit(aliceId, new BigDecimal("200.00"));
        balanceService.deposit(bobId, new BigDecimal("100.00"));

        // Push our consistent writes to the DB, then evict them from the persistence
        // context. Without the clear(), reconciliation's findAll() would hand back
        // the cached managed entities (uncorrupted) instead of re-reading the rows.
        em.flush();
        em.clear();

        // --- corrupt ONE account's stored balance directly, bypassing all
        //     application invariants (entity methods, optimistic locking, services) ---
        BigDecimal corruptedStoredBalance = new BigDecimal("999.99");
        jdbc.update("UPDATE account SET balance = ? WHERE id = ?", corruptedStoredBalance, aliceId);

        // --- act: run reconciliation on demand; do NOT wait for the schedule ---
        reconciliationService.runReconciliation();

        // --- assert: the corrupted account produced exactly one break, with the
        //     correct account id, ledger-derived balance, and stored balance ---
        List<ReconciliationBreak> aliceBreaks = breaks.findByAccountId(aliceId);
        assertThat(aliceBreaks)
                .as("corrupted account should produce exactly one break")
                .hasSize(1);

        ReconciliationBreak aliceBreak = aliceBreaks.get(0);
        assertThat(aliceBreak.getAccountId())
                .as("break carries the corrupted account's id")
                .isEqualTo(aliceId);
        assertThat(aliceBreak.getLedgerDerivedBalance())
                .as("ledger-derived balance = the funded amount (source of truth)")
                .isEqualByComparingTo("200.00");
        assertThat(aliceBreak.getStoredBalance())
                .as("stored balance = the corrupted cached value")
                .isEqualByComparingTo(corruptedStoredBalance);

        // --- assert: a freshly detected break is OPEN, has a last-seen timestamp,
        //     and is not yet resolved ---
        assertThat(aliceBreak.getStatus())
                .as("a freshly detected break is OPEN")
                .isEqualTo(ReconciliationBreakStatus.OPEN);
        assertThat(aliceBreak.getLastDetectedAt())
                .as("last-detected timestamp is recorded on detection")
                .isNotNull();
        assertThat(aliceBreak.getResolvedAt())
                .as("an open break has no resolution timestamp")
                .isNull();

        // --- assert: the clean customer account produced NO break ---
        assertThat(breaks.findByAccountId(bobId))
                .as("consistent account should not produce a break")
                .isEmpty();

        // --- assert: settlement was part of the sweep and reconciled cleanly.
        //     We never filter settlement out; it stays consistent, so no break. ---
        assertThat(breaks.findByAccountId(settlementId))
                .as("settlement is included in the sweep and reconciles cleanly")
                .isEmpty();
    }

    @Test
    void reconciliationBreakIsRedetectedWithoutDuplicateAndResolvedWhenBalancesMatch() {
        // --- arrange: a customer account that will drift, a clean one, + settlement ---
        Account alice = accounts.save(new Account("Lifecycle Alice", "CAD"));
        Account clean = accounts.save(new Account("Lifecycle Clean", "CAD"));
        Account settlement = accounts
                .findByAccountTypeAndCurrency(AccountType.SETTLEMENT, "CAD")
                .orElseThrow();

        Long aliceId = alice.getId();
        Long cleanId = clean.getId();
        Long settlementId = settlement.getId();

        // fund both through the real service so stored == derived for each
        balanceService.deposit(aliceId, new BigDecimal("200.00"));
        balanceService.deposit(cleanId, new BigDecimal("100.00"));

        // push consistent writes, then drop them from the persistence context so each
        // sweep re-reads rows from the DB instead of cached (uncorrupted) entities.
        em.flush();
        em.clear();

        // --- corrupt alice's stored balance directly, bypassing every app invariant ---
        BigDecimal corrupted = new BigDecimal("999.99");
        jdbc.update("UPDATE account SET balance = ? WHERE id = ?", corrupted, aliceId);

        // === run 1: first detection -> exactly one OPEN break opens ===
        reconciliationService.runReconciliation();
        em.flush();
        em.clear();

        List<ReconciliationBreak> afterFirstRun = breaks.findByAccountId(aliceId);
        assertThat(afterFirstRun)
                .as("first sweep opens exactly one break for the drifting account")
                .hasSize(1);

        ReconciliationBreak firstBreak = afterFirstRun.get(0);
        assertThat(firstBreak.getStatus()).isEqualTo(ReconciliationBreakStatus.OPEN);
        assertThat(firstBreak.getLedgerDerivedBalance()).isEqualByComparingTo("200.00");
        assertThat(firstBreak.getStoredBalance()).isEqualByComparingTo(corrupted);
        assertThat(firstBreak.getDetectedAt()).as("first-seen timestamp recorded").isNotNull();
        assertThat(firstBreak.getLastDetectedAt()).as("last-seen timestamp recorded").isNotNull();
        assertThat(firstBreak.getResolvedAt()).as("still open -> not resolved").isNull();

        Long firstBreakId = firstBreak.getId();
        OffsetDateTime firstLastDetected = firstBreak.getLastDetectedAt();

        // === run 2: account still drifting -> re-detect on the SAME row, no duplicate ===
        reconciliationService.runReconciliation();
        em.flush();
        em.clear();

        List<ReconciliationBreak> afterSecondRun = breaks.findByAccountId(aliceId);
        assertThat(afterSecondRun)
                .as("re-running the sweep must NOT insert a second break")
                .hasSize(1);

        ReconciliationBreak secondBreak = afterSecondRun.get(0);
        assertThat(secondBreak.getId())
                .as("the same physical break row is updated in place")
                .isEqualTo(firstBreakId);
        assertThat(secondBreak.getStatus())
                .as("still drifting -> still OPEN")
                .isEqualTo(ReconciliationBreakStatus.OPEN);
        assertThat(secondBreak.getLastDetectedAt())
                .as("last-detected strictly advances on re-detection")
                .isNotNull()
                .isAfter(firstLastDetected);
        assertThat(breaks.findByAccountIdAndStatus(aliceId, ReconciliationBreakStatus.OPEN))
                .as("exactly one OPEN break for the account")
                .isPresent();

        // --- repair the row for the test only: stored balance back to ledger-derived ---
        jdbc.update("UPDATE account SET balance = ? WHERE id = ?", new BigDecimal("200.00"), aliceId);
        em.clear();

        // === run 3: account reconciles cleanly -> the break resolves ===
        reconciliationService.runReconciliation();
        em.flush();
        em.clear();

        List<ReconciliationBreak> afterRepairRun = breaks.findByAccountId(aliceId);
        assertThat(afterRepairRun)
                .as("resolution updates the existing row, it does not add one")
                .hasSize(1);

        ReconciliationBreak resolvedBreak = afterRepairRun.get(0);
        assertThat(resolvedBreak.getId())
                .as("same physical row, now closed")
                .isEqualTo(firstBreakId);
        assertThat(resolvedBreak.getStatus())
                .as("clean again -> RESOLVED")
                .isEqualTo(ReconciliationBreakStatus.RESOLVED);
        assertThat(resolvedBreak.getResolvedAt())
                .as("resolution timestamp is set")
                .isNotNull();
        assertThat(breaks.findByAccountIdAndStatus(aliceId, ReconciliationBreakStatus.OPEN))
                .as("no OPEN break remains for the account")
                .isEmpty();

        // --- the clean customer account never produced a break across all three runs ---
        assertThat(breaks.findByAccountId(cleanId))
                .as("consistent account should not produce a break")
                .isEmpty();

        // --- settlement is part of the sweep and reconciles cleanly (never filtered out) ---
        assertThat(breaks.findByAccountId(settlementId))
                .as("settlement is included in the sweep and reconciles cleanly")
                .isEmpty();
    }
}
