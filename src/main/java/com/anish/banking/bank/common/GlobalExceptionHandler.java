package com.anish.banking.bank.common;

import com.anish.banking.bank.ledger.account.AccountNotFoundException;
import com.anish.banking.bank.ledger.idempotency.IdempotencyKeyConflictException;
import com.anish.banking.bank.ledger.transfer.CurrencyMismatchException;
import com.anish.banking.bank.ledger.transfer.InsufficientFundsException;
import com.anish.banking.bank.ledger.transfer.SameAccountTransferException;
import com.anish.banking.bank.ledger.transfer.TransferNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.time.OffsetDateTime;
import java.util.List;

@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    // --- single builder so every path produces an identical shape ---
    private ApiError body(HttpStatus status, String message, String path, List<ApiError.FieldViolation> fieldErrors) {
        return new ApiError(OffsetDateTime.now(), status.value(), status.getReasonPhrase(), message, path, fieldErrors);
    }

    private ResponseEntity<ApiError> respond(HttpStatus status, String message, HttpServletRequest request) {
        return ResponseEntity.status(status).body(body(status, message, request.getRequestURI(), null));
    }

    // ===================== domain exceptions =====================

    @ExceptionHandler({AccountNotFoundException.class, TransferNotFoundException.class})
    public ResponseEntity<ApiError> handleNotFound(RuntimeException ex, HttpServletRequest request) {
        return respond(HttpStatus.NOT_FOUND, ex.getMessage(), request);                 // 404
    }

    @ExceptionHandler(IdempotencyKeyConflictException.class)
    public ResponseEntity<ApiError> handleConflict(IdempotencyKeyConflictException ex, HttpServletRequest request) {
        return respond(HttpStatus.CONFLICT, ex.getMessage(), request);                  // 409
    }

    // Well-formed but violates a stateful rule (balance/currency known only after load).
    @ExceptionHandler({InsufficientFundsException.class, CurrencyMismatchException.class})
    public ResponseEntity<ApiError> handleUnprocessable(RuntimeException ex, HttpServletRequest request) {
        return respond(HttpStatus.UNPROCESSABLE_ENTITY, ex.getMessage(), request);      // 422
    }

    // source == destination is decidable from the payload alone -> malformed request.
    @ExceptionHandler(SameAccountTransferException.class)
    public ResponseEntity<ApiError> handleSameAccount(SameAccountTransferException ex, HttpServletRequest request) {
        return respond(HttpStatus.BAD_REQUEST, ex.getMessage(), request);               // 400
    }

    // Missing a required header, e.g. Idempotency-Key on POST /transfers. Subtype of
    // ServletRequestBindingException (which the base class claims), so this stricter
    // handler wins without ambiguity and gives a clearer message.
    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<ApiError> handleMissingHeader(MissingRequestHeaderException ex, HttpServletRequest request) {
        return respond(HttpStatus.BAD_REQUEST, "Missing required header: " + ex.getHeaderName(), request); // 400
    }

    // A path/query value can't bind to its target type, e.g. GET /accounts/abc/balance.
    // Subtype of TypeMismatchException (claimed by the base class) so this is unambiguous.
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiError> handleTypeMismatch(MethodArgumentTypeMismatchException ex, HttpServletRequest request) {
        return respond(HttpStatus.BAD_REQUEST, "Parameter '" + ex.getName() + "' has an invalid value", request); // 400
    }

    // ===================== catch-all =====================

    // SECURITY: never leak internals (stack trace, SQL, exception class) to the client.
    // Log the full detail server-side; return a generic message. For a money API a leaked
    // stack trace is an actual finding.
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleUnexpected(Exception ex, HttpServletRequest request) {
        log.error("Unhandled exception on {} {}", request.getMethod(), request.getRequestURI(), ex);
        return respond(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected internal error occurred", request); // 500
    }

    // ===================== Spring MVC framework exceptions =====================
    // Overridden so framework errors come back in the SAME ApiError shape.

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        List<ApiError.FieldViolation> violations = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> new ApiError.FieldViolation(fe.getField(), fe.getDefaultMessage()))
                .toList();
        ApiError error = body(HttpStatus.BAD_REQUEST, "Validation failed", path(request), violations);
        return handleExceptionInternal(ex, error, headers, HttpStatus.BAD_REQUEST, request);
    }

    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(
            HttpMessageNotReadableException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        // Do NOT echo Jackson's parser detail — keep it generic and safe.
        ApiError error = body(HttpStatus.BAD_REQUEST, "Malformed or unreadable request body", path(request), null);
        return handleExceptionInternal(ex, error, headers, HttpStatus.BAD_REQUEST, request);
    }

    // Funnel for every other framework exception (missing param, 405, unknown route, ...):
    // replace the default body with our ApiError shape, keeping the framework's status.
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(
            Exception ex, Object bodyObj, HttpHeaders headers, HttpStatusCode statusCode, WebRequest request) {
        if (bodyObj instanceof ApiError) {
            return super.handleExceptionInternal(ex, bodyObj, headers, statusCode, request);
        }
        HttpStatus status = HttpStatus.resolve(statusCode.value());
        if (status == null) {
            status = HttpStatus.INTERNAL_SERVER_ERROR;
        }
        ApiError error = body(status, status.getReasonPhrase(), path(request), null);
        return super.handleExceptionInternal(ex, error, headers, statusCode, request);
    }

    // WebRequest description is "uri=/accounts/1/balance"; strip the "uri=" prefix.
    private String path(WebRequest request) {
        String description = request.getDescription(false);
        return description.startsWith("uri=") ? description.substring(4) : description;
    }
}
