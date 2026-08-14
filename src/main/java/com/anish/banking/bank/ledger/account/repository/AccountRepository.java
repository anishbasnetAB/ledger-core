package com.anish.banking.bank.ledger.account.repository;

import com.anish.banking.bank.ledger.account.model.Account;
import com.anish.banking.bank.ledger.account.model.AccountType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AccountRepository extends JpaRepository<Account, Long> {

    Optional<Account> findByAccountTypeAndCurrency(AccountType accountType, String currency);

    // The ownership gate every account-scoped endpoint runs through: true only if the account
    // both exists AND belongs to this caller. Deliberately a single existence check, not a
    // findByIdAndOwnerUserId + null-check two-step — the whole point is that "doesn't exist"
    // and "exists but isn't yours" must be indistinguishable to the caller, so the code that
    // decides that shouldn't have two different paths to begin with.
    boolean existsByIdAndOwnerUserId(Long id, Long ownerUserId);

    // Reconciliation enumerates every account via the inherited findAll(). That is
    // fine at this project's scale and needs no extra method here.
    // PRODUCTION-SCALE NOTE: at millions of accounts, replace the all-at-once
    // findAll() with paginated/batched enumeration (e.g. keyset pagination over
    // account ids in chunks) so the reconciliation sweep never loads the whole
    // table into memory. See ReconciliationService#runReconciliation.
}
