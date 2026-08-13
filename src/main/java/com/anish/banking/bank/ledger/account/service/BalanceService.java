package com.anish.banking.bank.ledger.account.service;

import com.anish.banking.bank.ledger.account.dto.AccountResponse;
import com.anish.banking.bank.ledger.account.dto.BalanceResponse;
import com.anish.banking.bank.ledger.account.exception.AccountNotFoundException;
import com.anish.banking.bank.ledger.account.model.Account;
import com.anish.banking.bank.ledger.account.model.AccountType;
import com.anish.banking.bank.ledger.account.repository.AccountRepository;
import com.anish.banking.bank.ledger.ledger.model.LedgerEntry;
import com.anish.banking.bank.ledger.ledger.repository.LedgerEntryRepository;
import com.anish.banking.bank.ledger.transfer.event.TransferCompletedEvent;
import com.anish.banking.bank.ledger.transfer.model.Transfer;
import com.anish.banking.bank.ledger.transfer.model.TransferStatus;
import com.anish.banking.bank.ledger.transfer.repository.TransferRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.Duration;

@Service
public class BalanceService {

    private static final Logger log = LoggerFactory.getLogger(BalanceService.class);

    // Safety-net TTL, not a correctness mechanism: the stored balance column (what this
    // caches) is itself continuously re-verified against the ledger by the scheduled
    // reconciliation job, so a cache entry can never be wrong for longer than this either
    // way — a fixed value, not worth promoting to a per-environment property.
    private static final Duration BALANCE_CACHE_TTL = Duration.ofSeconds(45);

    private final AccountRepository accounts;
    private final LedgerEntryRepository ledger;
    private final TransferRepository transfers;
    private final ApplicationEventPublisher events;
    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;

    public BalanceService(AccountRepository accounts, LedgerEntryRepository ledger, TransferRepository transfers,
                           ApplicationEventPublisher events, StringRedisTemplate redis, ObjectMapper objectMapper) {
        this.accounts = accounts;
        this.ledger = ledger;
        this.transfers = transfers;
        this.events = events;
        this.redis = redis;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public AccountResponse createAccount(String ownerName, String currency) {
        // Owner-supplied strings are normalized once, here, before they hit the ledger:
        // trim the name, force the ISO currency to upper-case so "cad" and "CAD" never
        // create two distinct currency buckets. The Account constructor seeds balance 0.00
        // and type CUSTOMER.
        Account account = accounts.save(new Account(ownerName.trim(), currency.toUpperCase()));
        return AccountResponse.from(account);
    }

    @Transactional(readOnly = true)
    public BalanceResponse getBalance(Long accountId) {
        BalanceResponse cached = readFromCache(accountId);
        if (cached != null) {
            return cached;
        }

        Account account = accounts.findById(accountId)
                .orElseThrow(() -> new AccountNotFoundException(accountId));
        BalanceResponse response = toBalanceResponse(account);

        writeToCache(accountId, response);
        return response;
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

        events.publishEvent(new BalanceChangedEvent(customer.getId()));
        events.publishEvent(new BalanceChangedEvent(settlement.getId()));

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

        events.publishEvent(new BalanceChangedEvent(customer.getId()));
        events.publishEvent(new BalanceChangedEvent(settlement.getId()));

        return toBalanceResponse(customer);
    }

    // AFTER_COMMIT, same reasoning and same mechanism as TransferEventPublisher's Kafka
    // publish: evicting before commit could drop the cache for a change that then rolls
    // back, leaving the NEXT read to repopulate it from a balance that was never real.
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onBalanceChanged(BalanceChangedEvent event) {
        evictCache(event.accountId());
    }

    // Transfers change two accounts too, but via TransferService, not this class — that
    // service already publishes TransferCompletedEvent after its own commit (for Kafka),
    // so eviction here just piggybacks on the same event instead of inventing a second one.
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onTransferCompleted(TransferCompletedEvent event) {
        evictCache(event.sourceAccountId());
        evictCache(event.destinationAccountId());
    }

    private BalanceResponse readFromCache(Long accountId) {
        try {
            String cached = redis.opsForValue().get(cacheKey(accountId));
            return cached == null ? null : objectMapper.readValue(cached, BalanceResponse.class);
        } catch (Exception ex) {
            // Redis is a read-through cache here, not a source of truth — any problem reading
            // it (down, timeout, corrupt entry) just means falling back to Postgres below,
            // never an error surfaced to the caller.
            log.warn("Balance cache read failed for account {}, falling back to DB", accountId, ex);
            return null;
        }
    }

    private void writeToCache(Long accountId, BalanceResponse response) {
        try {
            redis.opsForValue().set(cacheKey(accountId), objectMapper.writeValueAsString(response), BALANCE_CACHE_TTL);
        } catch (Exception ex) {
            log.warn("Balance cache write failed for account {}", accountId, ex);
        }
    }

    private void evictCache(Long accountId) {
        try {
            redis.delete(cacheKey(accountId));
        } catch (Exception ex) {
            // Never let a Redis outage fail or roll back a money-movement request that has
            // already committed. Worst case: a stale cached balance for up to
            // BALANCE_CACHE_TTL, and the value it went stale from is itself independently
            // re-checked against the ledger by the scheduled reconciliation job regardless
            // of anything cached here.
            log.error("Failed to evict balance cache for account {}", accountId, ex);
        }
    }

    private String cacheKey(Long accountId) {
        return "balance:" + accountId;
    }

    private BalanceResponse toBalanceResponse(Account account) {
        return new BalanceResponse(
                account.getId(),
                account.getCurrency(),
                account.getBalance()
        );
    }

    // Internal only — never leaves this class. One account's balance changed and its cache
    // entry needs evicting; deposit/withdraw touch two accounts (customer + settlement), so
    // each publishes one of these per account rather than one event carrying both ids.
    record BalanceChangedEvent(Long accountId) {}
}
