package com.anish.banking.bank.ledger.account.controller;

import com.anish.banking.bank.auth.security.AuthenticatedUser;
import com.anish.banking.bank.ledger.account.dto.AccountResponse;
import com.anish.banking.bank.ledger.account.dto.BalanceResponse;
import com.anish.banking.bank.ledger.account.dto.CreateAccountRequest;
import com.anish.banking.bank.ledger.account.dto.MoneyRequest;
import com.anish.banking.bank.ledger.account.service.BalanceService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.net.URI;

@RestController
@RequestMapping("/api/accounts")
public class AccountController {

    private final BalanceService balanceService;

    public AccountController(BalanceService balanceService) {
        this.balanceService = balanceService;
    }

    @PostMapping
    public ResponseEntity<AccountResponse> create(
            @AuthenticationPrincipal AuthenticatedUser caller,
            @Valid @RequestBody CreateAccountRequest request
    ) {
        // The new account belongs to whoever is making the call -- ownership is never taken
        // from the request body, only from the authenticated token.
        AccountResponse body = balanceService.createAccount(request.ownerName(), request.currency(), caller.id());
        return ResponseEntity.created(URI.create("/api/accounts/" + body.id())).body(body);
    }

    @GetMapping("/{id}/balance")
    public BalanceResponse balance(@AuthenticationPrincipal AuthenticatedUser caller, @PathVariable Long id) {
        return balanceService.getBalance(id, caller.id());
    }

    @PostMapping("/{id}/deposit")
    public BalanceResponse deposit(
            @AuthenticationPrincipal AuthenticatedUser caller,
            @PathVariable Long id,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody MoneyRequest request
    ) {
        return balanceService.deposit(id, request.amount(), idempotencyKey, caller.id());
    }

    @PostMapping("/{id}/withdraw")
    public BalanceResponse withdraw(
            @AuthenticationPrincipal AuthenticatedUser caller,
            @PathVariable Long id,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody MoneyRequest request
    ) {
        return balanceService.withdraw(id, request.amount(), idempotencyKey, caller.id());
    }
}
