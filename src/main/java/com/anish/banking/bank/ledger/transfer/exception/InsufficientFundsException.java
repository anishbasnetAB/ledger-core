package com.anish.banking.bank.ledger.transfer.exception;

public class InsufficientFundsException extends RuntimeException {
    public InsufficientFundsException(Long id) {
        super("Insufficient funds in account " + id);
    }
}
