package com.anish.banking.bank.ledger.transfer.service;

import com.anish.banking.bank.auth.model.User;
import com.anish.banking.bank.auth.repository.UserRepository;
import com.anish.banking.bank.ledger.account.model.Account;
import com.anish.banking.bank.ledger.account.repository.AccountRepository;
import com.anish.banking.bank.ledger.idempotency.exception.IdempotencyKeyConflictException;
import com.anish.banking.bank.ledger.ledger.model.EntryType;
import com.anish.banking.bank.ledger.ledger.repository.LedgerEntryRepository;
import com.anish.banking.bank.ledger.transfer.dto.CreateTransferRequest;
import com.anish.banking.bank.ledger.transfer.dto.TransferResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
class IdempotencyTest {

    @Autowired AccountRepository accounts;
    @Autowired UserRepository users;
    @Autowired LedgerEntryRepository ledger;
    @Autowired TransferService transferService;

    private Long newOwner() {
        return users.save(new User("owner-" + UUID.randomUUID() + "@test.local", "unused-hash")).getId();
    }

    @Test
    void sameKeyReplaysAndMovesMoneyOnce() {
        Long ownerId = newOwner();
        Account alice = accounts.save(new Account("Alice", "CAD", ownerId));
        Account bob = accounts.save(new Account("Bob", "CAD", ownerId));

        Account aliceFunded = accounts.findById(alice.getId()).orElseThrow();
        aliceFunded.credit(new BigDecimal("500.00"));
        accounts.save(aliceFunded);

        CreateTransferRequest req = new CreateTransferRequest(
                alice.getId(), bob.getId(), new BigDecimal("100.00"));
        String key = "fixed-key-123";

        TransferResponse first = transferService.transfer(req, key, ownerId);
        TransferResponse second = transferService.transfer(req, key, ownerId);

        assertThat(first.transferId()).isEqualTo(second.transferId());
        assertThat(first.amount()).isEqualByComparingTo(second.amount());

        long bobCredits = ledger.countByAccountIdAndEntryType(bob.getId(), EntryType.CREDIT);
        assertThat(bobCredits).isEqualTo(1);

        Account freshBob = accounts.findById(bob.getId()).orElseThrow();
        assertThat(freshBob.getBalance()).isEqualByComparingTo("100.00");
    }

    @Test
    void sameKeyDifferentRequestIsRejected() {
        Long ownerId = newOwner();
        Account alice = accounts.save(new Account("Alice", "CAD", ownerId));
        Account bob = accounts.save(new Account("Bob", "CAD", ownerId));

        Account aliceFunded = accounts.findById(alice.getId()).orElseThrow();
        aliceFunded.credit(new BigDecimal("500.00"));
        accounts.save(aliceFunded);

        String key = "reused-key-456";
        transferService.transfer(
                new CreateTransferRequest(alice.getId(), bob.getId(), new BigDecimal("100.00")), key, ownerId);

        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                        transferService.transfer(
                                new CreateTransferRequest(alice.getId(), bob.getId(), new BigDecimal("999.00")), key, ownerId))
                .isInstanceOf(IdempotencyKeyConflictException.class);
    }

    @Test
    void sameKeyDifferentDestinationIsRejected() {
        Long ownerId = newOwner();
        Account alice = accounts.save(new Account("Alice", "CAD", ownerId));
        Account bob = accounts.save(new Account("Bob", "CAD", ownerId));
        Account carol = accounts.save(new Account("Carol", "CAD", ownerId));

        Account aliceFunded = accounts.findById(alice.getId()).orElseThrow();
        aliceFunded.credit(new BigDecimal("500.00"));
        accounts.save(aliceFunded);

        String key = "dest-key-789";
        transferService.transfer(
                new CreateTransferRequest(alice.getId(), bob.getId(), new BigDecimal("100.00")), key, ownerId);

        // same key, same amount, same source, but a DIFFERENT destination -> must conflict
        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                        transferService.transfer(
                                new CreateTransferRequest(alice.getId(), carol.getId(), new BigDecimal("100.00")), key, ownerId))
                .isInstanceOf(IdempotencyKeyConflictException.class);
    }
}
