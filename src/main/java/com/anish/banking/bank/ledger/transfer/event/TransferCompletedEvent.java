package com.anish.banking.bank.ledger.transfer.event;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

// Doubles as both the internal Spring ApplicationEvent (published inside the transfer's
// transaction) and the Kafka message payload (serialized to JSON after commit) — one
// shape, no mapping step needed between the two.
public record TransferCompletedEvent(
        Long transferId, Long sourceAccountId, Long destinationAccountId,
        BigDecimal amount, OffsetDateTime occurredAt
) {}
