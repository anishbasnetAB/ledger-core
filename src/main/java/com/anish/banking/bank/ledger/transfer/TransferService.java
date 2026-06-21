package com.anish.banking.bank.ledger.transfer;

import com.anish.banking.bank.ledger.account.*;
import com.anish.banking.bank.ledger.idempotency.*;
import com.anish.banking.bank.ledger.ledger.*;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TransferService {
    private final AccountRepository accounts;
    private final LedgerEntryRepository ledger;
    private final TransferRepository transfers;
    private final IdempotencyKeyRepository idempotencyKeys;
    private final ObjectMapper objectMapper;

    public TransferService(AccountRepository accounts, LedgerEntryRepository ledger,
                           TransferRepository transfers,
                           IdempotencyKeyRepository idempotencyKeys,
                           ObjectMapper objectMapper) {
        this.accounts = accounts;
        this.ledger = ledger;
        this.transfers = transfers;
        this.idempotencyKeys = idempotencyKeys;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public TransferResponse transfer(CreateTransferRequest req, String idempotencyKey) {
        String incomingHash = RequestHasher.hash(req);

        var existing = idempotencyKeys.findByIdempotencyKey(idempotencyKey);
        if (existing.isPresent()) {
            IdempotencyKey stored = existing.get();
            if (!stored.getRequestHash().equals(incomingHash)) {
                throw new IdempotencyKeyConflictException(idempotencyKey);
            }
            return deserialize(stored.getResponseBody());
        }

        TransferResponse response = doTransfer(req);

        idempotencyKeys.save(new IdempotencyKey(
                idempotencyKey, incomingHash, serialize(response), 201));

        return response;
    }

    private TransferResponse doTransfer(CreateTransferRequest req) {
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

        source.debit(req.amount());
        dest.credit(req.amount());

        ledger.save(LedgerEntry.debit(source.getId(), req.amount(), transfer.getId()));
        ledger.save(LedgerEntry.credit(dest.getId(), req.amount(), transfer.getId()));

        return new TransferResponse(transfer.getId(), source.getId(), dest.getId(),
                req.amount(), transfer.getStatus().name());
    }

    private String serialize(TransferResponse response) {
        try {
            return objectMapper.writeValueAsString(response);
        } catch (JacksonException e) {
            throw new IllegalStateException("Failed to serialize TransferResponse", e);
        }
    }

    private TransferResponse deserialize(String body) {
        try {
            return objectMapper.readValue(body, TransferResponse.class);
        } catch (JacksonException e) {
            throw new IllegalStateException("Failed to deserialize stored response", e);
        }
    }

    @Transactional(readOnly = true)
    public TransferResponse getTransfer(Long id) {
        Transfer t = transfers.findById(id)
                .orElseThrow(() -> new TransferNotFoundException(id));
        return new TransferResponse(t.getId(), t.getSourceAccountId(),
                t.getDestinationAccountId(), t.getAmount(), t.getStatus().name());
    }
}