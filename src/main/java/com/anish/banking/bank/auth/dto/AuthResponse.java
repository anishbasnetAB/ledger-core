package com.anish.banking.bank.auth.dto;

public record AuthResponse(String token, String email, String role) {
}
