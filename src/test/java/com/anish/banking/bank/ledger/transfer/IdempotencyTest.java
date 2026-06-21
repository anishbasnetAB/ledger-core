package com.anish.banking.bank.ledger.transfer;

import com.anish.banking.bank.ledger.account.*;
import com.anish.banking.bank.ledger.ledger.EntryType;
import com.anish.banking.bank.ledger.ledger.LedgerEntryRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
class IdempotencyTest {

    @Autowired AccountRepository accounts;
    @Autowired LedgerEntryRepository ledger;
    @Autowired TransferService transferService;

    @Test
    void sameKeyReplaysAndMovesMoneyOnce() {
        Account alice = accounts.save(new Account("Alice", "CAD"));
        Account bob = accounts.save(new Account("Bob", "CAD"));

        Account aliceFunded = accounts.findById(alice.getId()).orElseThrow();
        aliceFunded.credit(new BigDecimal("500.00"));
        accounts.save(aliceFunded);

        CreateTransferRequest req = new CreateTransferRequest(
                alice.getId(), bob.getId(), new BigDecimal("100.00"));
        String key = "fixed-key-123";

        TransferResponse first = transferService.transfer(req, key);
        TransferResponse second = transferService.transfer(req, key);

        assertThat(first.transferId()).isEqualTo(second.transferId());
        assertThat(first.amount()).isEqualByComparingTo(second.amount());

        long bobCredits = ledger.countByAccountIdAndEntryType(bob.getId(), EntryType.CREDIT);
        assertThat(bobCredits).isEqualTo(1);

        Account freshBob = accounts.findById(bob.getId()).orElseThrow();
        assertThat(freshBob.getBalance()).isEqualByComparingTo("100.00");
    }

    @Test
    void sameKeyDifferentRequestIsRejected() {
        Account alice = accounts.save(new Account("Alice", "CAD"));
        Account bob = accounts.save(new Account("Bob", "CAD"));

        Account aliceFunded = accounts.findById(alice.getId()).orElseThrow();
        aliceFunded.credit(new BigDecimal("500.00"));
        accounts.save(aliceFunded);

        String key = "reused-key-456";
        transferService.transfer(
                new CreateTransferRequest(alice.getId(), bob.getId(), new BigDecimal("100.00")), key);

        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                        transferService.transfer(
                                new CreateTransferRequest(alice.getId(), bob.getId(), new BigDecimal("999.00")), key))
                .isInstanceOf(com.anish.banking.bank.ledger.idempotency.IdempotencyKeyConflictException.class);
    }

    @Test
    void sameKeyDifferentDestinationIsRejected() {
        Account alice = accounts.save(new Account("Alice", "CAD"));
        Account bob = accounts.save(new Account("Bob", "CAD"));
        Account carol = accounts.save(new Account("Carol", "CAD"));

        Account aliceFunded = accounts.findById(alice.getId()).orElseThrow();
        aliceFunded.credit(new BigDecimal("500.00"));
        accounts.save(aliceFunded);

        String key = "dest-key-789";
        transferService.transfer(
                new CreateTransferRequest(alice.getId(), bob.getId(), new BigDecimal("100.00")), key);

        // same key, same amount, same source, but a DIFFERENT destination -> must conflict
        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                        transferService.transfer(
                                new CreateTransferRequest(alice.getId(), carol.getId(), new BigDecimal("100.00")), key))
                .isInstanceOf(com.anish.banking.bank.ledger.idempotency.IdempotencyKeyConflictException.class);
    }
}