package com.anish.banking.bank.ledger.transfer.service;

import com.anish.banking.bank.ledger.ledger.model.EntryType;
import com.anish.banking.bank.ledger.ledger.model.LedgerEntry;
import com.anish.banking.bank.ledger.ledger.repository.LedgerEntryRepository;
import com.anish.banking.bank.ledger.transfer.dto.CreateTransferRequest;
import com.anish.banking.bank.ledger.transfer.event.TransferCompletedEvent;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

// No live broker in this test run (see application.properties) — KafkaTemplate is replaced
// with a mock so the assertions are about *whether* a send happened, not real delivery.
@SpringBootTest
class TransferEventPublishingTest {

    @Autowired TransferService transferService;
    @Autowired LedgerEntryRepository ledger;

    @MockitoBean KafkaTemplate<String, Object> kafkaTemplate;
    @MockitoSpyBean LedgerEntryRepository ledgerSpy;

    @Test
    void publishesEventAfterSuccessfulTransfer() {
        when(kafkaTemplate.send(anyString(), anyString(), any()))
                .thenReturn(CompletableFuture.completedFuture(null));

        var response = transferService.transfer(
                new CreateTransferRequest(1L, 2L, new BigDecimal("10.00")), UUID.randomUUID().toString());

        // fires synchronously: AFTER_COMMIT runs in the same thread, right after commit,
        // before transfer() returns — no need to wait/poll for it.
        verify(kafkaTemplate).send(
                eq("transfer.completed"),
                eq(response.transferId().toString()),
                argThat(event -> event instanceof TransferCompletedEvent e
                        && e.transferId().equals(response.transferId())
                        && e.amount().compareTo(new BigDecimal("10.00")) == 0));
    }

    @Test
    void doesNotPublishWhenTransferRollsBack() {
        // simulate a mid-transfer crash, same technique as TransferAtomicityTest
        doThrow(new RuntimeException("simulated failure after debit"))
                .when(ledgerSpy).save(argThat(e -> e instanceof LedgerEntry le && le.getEntryType() == EntryType.CREDIT));

        assertThatThrownBy(() -> transferService.transfer(
                new CreateTransferRequest(1L, 2L, new BigDecimal("10.00")), UUID.randomUUID().toString()))
                .isInstanceOf(RuntimeException.class);

        verifyNoInteractions(kafkaTemplate);
    }
}
