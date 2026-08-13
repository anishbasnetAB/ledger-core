package com.anish.banking.bank.ledger.account.repository;

import com.anish.banking.bank.ledger.account.model.Account;
import com.anish.banking.bank.ledger.account.model.AccountType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AccountRepository extends JpaRepository<Account, Long> {

    Optional<Account> findByAccountTypeAndCurrency(AccountType accountType, String currency);

    // Reconciliation enumerates every account via the inherited findAll(). That is
    // fine at this project's scale and needs no extra method here.
    // PRODUCTION-SCALE NOTE: at millions of accounts, replace the all-at-once
    // findAll() with paginated/batched enumeration (e.g. keyset pagination over
    // account ids in chunks) so the reconciliation sweep never loads the whole
    // table into memory. See ReconciliationService#runReconciliation.
}
