package com.anish.banking.bank.ledger.transfer.service;

import com.anish.banking.bank.ledger.account.exception.AccountNotFoundException;
import com.anish.banking.bank.ledger.account.model.Account;
import com.anish.banking.bank.ledger.account.repository.AccountRepository;
import com.anish.banking.bank.ledger.idempotency.RequestHasher;
import com.anish.banking.bank.ledger.idempotency.exception.IdempotencyKeyConflictException;
import com.anish.banking.bank.ledger.idempotency.model.IdempotencyKey;
import com.anish.banking.bank.ledger.idempotency.repository.IdempotencyKeyRepository;
import com.anish.banking.bank.ledger.ledger.model.LedgerEntry;
import com.anish.banking.bank.ledger.ledger.repository.LedgerEntryRepository;
import com.anish.banking.bank.ledger.transfer.dto.CreateTransferRequest;
import com.anish.banking.bank.ledger.transfer.dto.TransferResponse;
import com.anish.banking.bank.ledger.transfer.event.TransferCompletedEvent;
import com.anish.banking.bank.ledger.transfer.exception.CurrencyMismatchException;
import com.anish.banking.bank.ledger.transfer.exception.SameAccountTransferException;
import com.anish.banking.bank.ledger.transfer.exception.TransferNotFoundException;
import com.anish.banking.bank.ledger.transfer.model.Transfer;
import com.anish.banking.bank.ledger.transfer.model.TransferStatus;
import com.anish.banking.bank.ledger.transfer.repository.TransferRepository;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;

@Service
public class TransferService {
    private final AccountRepository accounts;
    private final LedgerEntryRepository ledger;
    private final TransferRepository transfers;
    private final IdempotencyKeyRepository idempotencyKeys;
    private final ObjectMapper objectMapper;
    private final ApplicationEventPublisher events;

    public TransferService(AccountRepository accounts, LedgerEntryRepository ledger,
                           TransferRepository transfers,
                           IdempotencyKeyRepository idempotencyKeys,
                           ObjectMapper objectMapper,
                           ApplicationEventPublisher events) {
        this.accounts = accounts;
        this.ledger = ledger;
        this.transfers = transfers;
        this.idempotencyKeys = idempotencyKeys;
        this.objectMapper = objectMapper;
        this.events = events;
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

        // Published now, inside the transaction, but only *delivered* to Kafka after commit
        // (see TransferEventPublisher) — publishEvent here just queues it with Spring; nothing
        // reaches the broker if this method rolls back after this point.
        events.publishEvent(new TransferCompletedEvent(transfer.getId(), source.getId(), dest.getId(),
                req.amount(), OffsetDateTime.now()));

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
