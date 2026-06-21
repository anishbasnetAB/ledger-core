package com.anish.banking.bank.ledger.transfer;

import com.anish.banking.bank.ledger.account.*;
import com.anish.banking.bank.ledger.ledger.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;

@Service
public class TransferService {
    private final AccountRepository accounts;
    private final LedgerEntryRepository ledger;
    private final TransferRepository transfers;

    public TransferService(AccountRepository accounts, LedgerEntryRepository ledger, TransferRepository transfers) {
        this.accounts = accounts;
        this.ledger = ledger;
        this.transfers = transfers;
    }

    @Transactional
    public TransferResponse transfer(CreateTransferRequest req) {
        if (req.sourceAccountId().equals(req.destinationAccountId()))
            throw new SameAccountTransferException();

        Account source = accounts.findById(req.sourceAccountId())
                .orElseThrow(() -> new AccountNotFoundException(req.sourceAccountId()));
        Account dest = accounts.findById(req.destinationAccountId())
                .orElseThrow(() -> new AccountNotFoundException(req.destinationAccountId()));

        if (!source.getCurrency().equals(dest.getCurrency()))
            throw new CurrencyMismatchException(source.getCurrency(), dest.getCurrency());

        Transfer transfer = transfers.save(
                new Transfer(source.getId(), dest.getId(), req.amount(), TransferStatus.COMPLETED));

        source.debit(req.amount());     // floor enforced — source is a CUSTOMER
        dest.credit(req.amount());

        ledger.save(LedgerEntry.debit(source.getId(), req.amount(), transfer.getId()));
        ledger.save(LedgerEntry.credit(dest.getId(), req.amount(), transfer.getId()));

        return new TransferResponse(transfer.getId(), source.getId(), dest.getId(),
                req.amount(), transfer.getStatus().name());
    }

    @Transactional(readOnly = true)
    public TransferResponse getTransfer(Long id) {
        Transfer t = transfers.findById(id)
                .orElseThrow(() -> new TransferNotFoundException(id));
        return new TransferResponse(t.getId(), t.getSourceAccountId(),
                t.getDestinationAccountId(), t.getAmount(), t.getStatus().name());
    }


}