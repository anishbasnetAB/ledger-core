package com.anish.banking.bank.ledger.account.dto;

import com.anish.banking.bank.ledger.account.model.Account;
import java.math.BigDecimal;


public record AccountResponse(Long id, String ownerName, String currency, BigDecimal balance) {

    public static AccountResponse from(Account account) {
        return new AccountResponse(
                account.getId(),
                account.getOwnerName(),
                account.getCurrency(),
                account.getBalance());
    }
}
