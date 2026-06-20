package com.anish.banking.bank.ledger.transfer;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;

public record CreateTransferRequest(
        @NotNull Long sourceAccountId,
        @NotNull Long destinationAccountId,
        @NotNull @Positive BigDecimal amount
) {}