package com.anish.banking.bank.common;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * The single error shape returned by EVERY error path in the API.
 *
 * {@code timestamp, status, error, message, path} are always present. {@code fieldErrors}
 * is only populated for bean-validation failures and is otherwise omitted from the JSON
 * (via {@link JsonInclude}), so there is one consistent shape with one optional extension
 * rather than several competing shapes.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiError(
        OffsetDateTime timestamp,
        int status,
        String error,
        String message,
        String path,
        List<FieldViolation> fieldErrors) {

    /** One rejected field on a validation failure: which field, and why. */
    public record FieldViolation(String field, String message) {}
}
