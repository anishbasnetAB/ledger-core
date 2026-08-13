package com.anish.banking.bank.ledger.transfer.exception;

public class TransferNotFoundException extends RuntimeException {
    public TransferNotFoundException(Long id) {
        super("Transfer not found: " + id);
    }
}
