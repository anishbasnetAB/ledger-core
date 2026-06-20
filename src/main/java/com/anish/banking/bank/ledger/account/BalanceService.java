package com.anish.banking.bank.ledger.account;

import com.anish.banking.bank.ledger.ledger.LedgerEntryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;

@Service                                 // Marks this as a Spring-managed service bean.
public class BalanceService {
    private final AccountRepository accounts;        // Used to confirm the account exists.
    private final LedgerEntryRepository ledger;      // Used to derive the balance from entries.

    public BalanceService(AccountRepository accounts, LedgerEntryRepository ledger) {
        this.accounts = accounts;        // Constructor injection — dependencies are final and required.
        this.ledger = ledger;
    }

    @Transactional(readOnly = true)      // Read-only transaction: a consistent snapshot with no write overhead.
    public BalanceResponse getBalance(Long accountId) {
        Account a = accounts.findById(accountId)
                .orElseThrow(() -> new AccountNotFoundException(accountId)); // 404 if the account doesn't exist.
        BigDecimal balance = ledger.deriveBalance(accountId);               // Compute balance live from the ledger.
        return new BalanceResponse(a.getId(), a.getCurrency(), balance);    // Package the result for the API.
    }
}