package com.anish.banking.bank.common;

import com.anish.banking.bank.ledger.account.AccountNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import java.time.OffsetDateTime;
import com.anish.banking.bank.ledger.transfer.SameAccountTransferException;
import com.anish.banking.bank.ledger.transfer.InsufficientFundsException;
import com.anish.banking.bank.ledger.transfer.CurrencyMismatchException;
import com.anish.banking.bank.ledger.transfer.TransferNotFoundException;
import com.anish.banking.bank.ledger.idempotency.IdempotencyKeyConflictException;
import org.springframework.web.bind.MissingRequestHeaderException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    // --- shared helper: builds the ApiError body for any status ---
    private ResponseEntity<ApiError> status(HttpStatus status, String message) {
        ApiError body = new ApiError(
                OffsetDateTime.now(),
                status.value(),
                status.getReasonPhrase(),   // e.g. "Bad Request", "Not Found"
                message
        );
        return ResponseEntity.status(status).body(body);
    }

    @ExceptionHandler(AccountNotFoundException.class)
    public ResponseEntity<ApiError> handleNotFound(AccountNotFoundException ex) {
        return status(HttpStatus.NOT_FOUND, ex.getMessage());            // 404
    }

    @ExceptionHandler(SameAccountTransferException.class)
    public ResponseEntity<ApiError> handleSameAccount(SameAccountTransferException ex) {
        return status(HttpStatus.BAD_REQUEST, ex.getMessage());          // 400
    }

    @ExceptionHandler({InsufficientFundsException.class, CurrencyMismatchException.class})
    public ResponseEntity<ApiError> handleUnprocessable(RuntimeException ex) {
        return status(HttpStatus.UNPROCESSABLE_ENTITY, ex.getMessage()); // 422
    }

    @ExceptionHandler(TransferNotFoundException.class)
    public ResponseEntity<ApiError> handleTransferNotFound(TransferNotFoundException ex) {
        return status(HttpStatus.NOT_FOUND, ex.getMessage());            // 404
    }

    @ExceptionHandler(IdempotencyKeyConflictException.class)
    public ResponseEntity<ApiError> handleIdempotencyConflict(IdempotencyKeyConflictException ex) {
        return status(HttpStatus.CONFLICT, ex.getMessage());             // 409
    }

    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<ApiError> handleMissingHeader(MissingRequestHeaderException ex) {
        return status(HttpStatus.BAD_REQUEST,                            // 400
                "Missing required header: " + ex.getHeaderName());
    }
}