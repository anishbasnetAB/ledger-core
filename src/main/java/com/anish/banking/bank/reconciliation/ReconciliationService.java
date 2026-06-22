package com.anish.banking.bank.reconciliation;

import com.anish.banking.bank.ledger.account.Account;
import com.anish.banking.bank.ledger.account.AccountRepository;
import com.anish.banking.bank.ledger.ledger.LedgerEntryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
public class ReconciliationService {

    private static final Logger log = LoggerFactory.getLogger(ReconciliationService.class);

    private final AccountRepository accounts;
    private final LedgerEntryRepository ledger;
    private final ReconciliationBreakRepository breaks;

    public ReconciliationService(AccountRepository accounts,
                                 LedgerEntryRepository ledger,
                                 ReconciliationBreakRepository breaks) {
        this.accounts = accounts;
        this.ledger = ledger;
        this.breaks = breaks;
    }


    /**
     * Reconcile a single account against its ledger and update break state idempotently.
     * Detection + audit only — reconciliation records evidence and NEVER auto-corrects
     * a balance.
     *
     * Four-way logic over {matches?} x {open break exists?}:
     *   A. matches  + no open break  -> nothing to do.
     *   B. mismatch + no open break  -> open a new break.
     *   C. mismatch + open break     -> re-detect on the existing break (no duplicate row).
     *   D. matches  + open break     -> resolve the break.
     *
     * @return the account's OPEN break after this check (cases B and C); empty when the
     *         account is clean (A) or was just resolved (D).
     */
    @Transactional
    public Optional<ReconciliationBreak> checkAccount(Account account) {
        BigDecimal ledgerDerivedBalance = ledger.deriveBalance(account.getId());
        BigDecimal storedBalance = account.getBalance();

        // compareTo (not equals) so 200 and 200.00 are treated as equal.
        boolean matches = ledgerDerivedBalance.compareTo(storedBalance) == 0;

        Optional<ReconciliationBreak> openBreak =
                breaks.findByAccountIdAndStatus(account.getId(), ReconciliationBreakStatus.OPEN);

        if (matches) {
            if (openBreak.isPresent()) {
                // Case D: the account is back in sync — close the outstanding break.
                ReconciliationBreak resolved = openBreak.get();
                resolved.markResolved();
                log.info("Reconciliation break for account {} RESOLVED: stored balance back in sync at {}",
                        account.getId(), ledgerDerivedBalance);
            }
            // Case A: clean and nothing outstanding — no row touched.
            return Optional.empty();
        }

        if (openBreak.isPresent()) {
            // Case C: still drifting and already tracked — update the existing row, never
            // insert a duplicate (the partial unique index would reject it anyway).
            ReconciliationBreak existing = openBreak.get();
            existing.recordRedetection(ledgerDerivedBalance, storedBalance);
            log.warn("Reconciliation break for account {} RE-DETECTED: ledgerDerived={} stored={}",
                    account.getId(), ledgerDerivedBalance, storedBalance);
            return Optional.of(existing);
        }

        // Case B: newly drifting account with no open break — record a fresh one.
        ReconciliationBreak recorded = breaks.save(
                new ReconciliationBreak(account.getId(), ledgerDerivedBalance, storedBalance));
        log.warn("Reconciliation break for account {} OPENED: ledgerDerived={} stored={}",
                account.getId(), ledgerDerivedBalance, storedBalance);
        return Optional.of(recorded);
    }

    @Transactional
    public List<ReconciliationBreak> runReconciliation() {
        List<ReconciliationBreak> recorded = new ArrayList<>();

        for (Account account : accounts.findAll()) {
            checkAccount(account).ifPresent(recorded::add);
        }

        log.info("Reconciliation sweep complete: checked all accounts, {} break(s) recorded",
                recorded.size());

        return recorded;
    }
}
