package com.anish.banking.bank.ledger.account;

import com.anish.banking.bank.ledger.ledger.LedgerEntry;
import com.anish.banking.bank.ledger.ledger.LedgerEntryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Service
public class BalanceService {

    private final AccountRepository accounts;
    private final LedgerEntryRepository ledger;

    public BalanceService(AccountRepository accounts, LedgerEntryRepository ledger) {
        this.accounts = accounts;
        this.ledger = ledger;
    }

    @Transactional(readOnly = true)
    public BalanceResponse getBalance(Long accountId) {
        Account account = accounts.findById(accountId)
                .orElseThrow(() -> new AccountNotFoundException(accountId));

        return toBalanceResponse(account);
    }

    @Transactional
    public BalanceResponse deposit(Long accountId, BigDecimal amount) {
        Account account = accounts.findById(accountId)
                .orElseThrow(() -> new AccountNotFoundException(accountId));

        account.credit(amount);

        ledger.save(
                LedgerEntry.credit(account.getId(), amount)
        );

        return toBalanceResponse(account);
    }

    @Transactional
    public BalanceResponse withdraw(Long accountId, BigDecimal amount) {
        Account account = accounts.findById(accountId)
                .orElseThrow(() -> new AccountNotFoundException(accountId));

        account.debit(amount);

        ledger.save(
                LedgerEntry.debit(account.getId(), amount)
        );

        return toBalanceResponse(account);
    }

    private BalanceResponse toBalanceResponse(Account account) {
        return new BalanceResponse(
                account.getId(),
                account.getCurrency(),
                account.getBalance()
        );
    }
}