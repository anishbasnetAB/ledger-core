package com.anish.banking.bank.auth.security;

import java.security.Principal;

/**
 * The caller's identity, resolved once in JwtAuthFilter from the token's claims and handed
 * to controllers via {@code @AuthenticationPrincipal} instead of re-parsing claims or hitting
 * the DB per request. Carries the numeric user id (needed for account-ownership checks) next
 * to the email that was already the JWT subject.
 *
 * Implements Principal, not just a plain record, so Authentication#getName() still resolves
 * to the email — RateLimitFilter's per-user rate-limit key depends on that.
 */
public record AuthenticatedUser(Long id, String email) implements Principal {

    @Override
    public String getName() {
        return email;
    }
}
