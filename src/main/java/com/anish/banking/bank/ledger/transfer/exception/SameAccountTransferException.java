package com.anish.banking.bank.ledger.transfer.exception;

public class SameAccountTransferException extends RuntimeException {
    public SameAccountTransferException() { super("Source and destination must differ"); }
}
