package com.anish.banking.bank.ledger.account;

import java.math.BigDecimal;

public record BalanceResponse(Long accountId, String currency, BigDecimal balance) {}