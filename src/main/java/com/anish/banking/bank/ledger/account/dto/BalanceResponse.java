package com.anish.banking.bank.ledger.account.dto;

import java.math.BigDecimal;

public record BalanceResponse(Long accountId, String currency, BigDecimal balance) {}
