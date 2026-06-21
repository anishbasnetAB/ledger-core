package com.anish.banking.bank.ledger.account;

import com.anish.banking.bank.ledger.ledger.LedgerEntry;
import com.anish.banking.bank.ledger.transfer.Transfer;
import com.anish.banking.bank.ledger.ledger.LedgerEntryRepository;
import com.anish.banking.bank.ledger.transfer.TransferRepository;
import com.anish.banking.bank.ledger.transfer.TransferStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Service
public class BalanceService {

    private final AccountRepository accounts;
    private final LedgerEntryRepository ledger;
    private final TransferRepository transfers;

    public BalanceService(AccountRepository accounts, LedgerEntryRepository ledger, TransferRepository transfers) {
        this.accounts = accounts;
        this.ledger = ledger;
        this.transfers = transfers;
    }

    @Transactional(readOnly = true)
    public BalanceResponse getBalance(Long accountId) {
        Account account = accounts.findById(accountId)
                .orElseThrow(() -> new AccountNotFoundException(accountId));

        return toBalanceResponse(account);
    }

    @Transactional
    public BalanceResponse deposit(Long accountId, BigDecimal amount) {
        Account customer = accounts.findById(accountId)
                .orElseThrow(() -> new AccountNotFoundException(accountId));
        Account settlement = accounts
                .findByAccountTypeAndCurrency(AccountType.SETTLEMENT, customer.getCurrency())
                .orElseThrow(() -> new IllegalStateException(
                        "No settlement account for currency " + customer.getCurrency()));

        // money moves settlement -> customer
        Transfer movement = transfers.save(
                new Transfer(settlement.getId(), customer.getId(), amount, TransferStatus.COMPLETED));

        customer.credit(amount);       // customer balance up
        settlement.debit(amount);      // settlement goes negative — allowed, it's SETTLEMENT

        ledger.save(LedgerEntry.credit(customer.getId(), amount, movement.getId()));
        ledger.save(LedgerEntry.debit(settlement.getId(), amount, movement.getId()));

        return toBalanceResponse(customer);
    }

    @Transactional
    public BalanceResponse withdraw(Long accountId, BigDecimal amount) {
        Account customer = accounts.findById(accountId)
                .orElseThrow(() -> new AccountNotFoundException(accountId));
        Account settlement = accounts
                .findByAccountTypeAndCurrency(AccountType.SETTLEMENT, customer.getCurrency())
                .orElseThrow(() -> new IllegalStateException(
                        "No settlement account for currency " + customer.getCurrency()));

        // money moves customer -> settlement
        Transfer movement = transfers.save(
                new Transfer(customer.getId(), settlement.getId(), amount, TransferStatus.COMPLETED));

        customer.debit(amount);        // customer balance down — floor STILL enforced (CUSTOMER)
        settlement.credit(amount);     // settlement balance up (less negative)

        ledger.save(LedgerEntry.debit(customer.getId(), amount, movement.getId()));
        ledger.save(LedgerEntry.credit(settlement.getId(), amount, movement.getId()));

        return toBalanceResponse(customer);
    }

    private BalanceResponse toBalanceResponse(Account account) {
        return new BalanceResponse(
                account.getId(),
                account.getCurrency(),
                account.getBalance()
        );
    }


}