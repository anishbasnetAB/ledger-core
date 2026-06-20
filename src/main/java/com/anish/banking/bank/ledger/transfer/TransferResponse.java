package com.anish.banking.bank.ledger.transfer;

import java.math.BigDecimal;

public record TransferResponse(
        Long transferId, Long sourceAccountId, Long destinationAccountId,
        BigDecimal amount, String status
) {}