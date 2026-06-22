package com.anish.banking.bank.ledger.account;

import java.math.BigDecimal;


public record AccountResponse(Long id, String ownerName, String currency, BigDecimal balance) {

    static AccountResponse from(Account account) {
        return new AccountResponse(
                account.getId(),
                account.getOwnerName(),
                account.getCurrency(),
                account.getBalance());
    }
}
