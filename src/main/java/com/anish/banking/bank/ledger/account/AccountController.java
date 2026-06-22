package com.anish.banking.bank.ledger.account;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.net.URI;

@RestController
@RequestMapping("/api/accounts")
public class AccountController {

    private final BalanceService balanceService;

    public AccountController(BalanceService balanceService) {
        this.balanceService = balanceService;
    }

    @PostMapping
    public ResponseEntity<AccountResponse> create(@Valid @RequestBody CreateAccountRequest request) {
        AccountResponse body = balanceService.createAccount(request.ownerName(), request.currency());
        return ResponseEntity.created(URI.create("/api/accounts/" + body.id())).body(body);
    }

    @GetMapping("/{id}/balance")
    public BalanceResponse balance(@PathVariable Long id) {
        return balanceService.getBalance(id);
    }

    @PostMapping("/{id}/deposit")
    public BalanceResponse deposit(
            @PathVariable Long id,
            @Valid @RequestBody MoneyRequest request
    ) {
        return balanceService.deposit(id, request.amount());
    }

    @PostMapping("/{id}/withdraw")
    public BalanceResponse withdraw(
            @PathVariable Long id,
            @Valid @RequestBody MoneyRequest request
    ) {
        return balanceService.withdraw(id, request.amount());
    }

    public record CreateAccountRequest(
            @NotBlank(message = "ownerName must not be blank")
            @Size(max = 255, message = "ownerName is too long")
            String ownerName,

            @NotBlank(message = "currency must not be blank")
            @Pattern(regexp = "^[A-Za-z]{3}$", message = "currency must be a 3-letter ISO code")
            String currency
    ) {
    }

    public record MoneyRequest(
            @NotNull
            @DecimalMin(value = "0.01", message = "amount must be at least 0.01")
            @Digits(integer = 17, fraction = 2, message = "amount is too large or has too many decimal places")
            BigDecimal amount
    ) {
    }
}