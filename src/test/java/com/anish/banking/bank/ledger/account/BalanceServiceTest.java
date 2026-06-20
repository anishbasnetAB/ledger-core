package com.anish.banking.bank.ledger.account;

import com.anish.banking.bank.ledger.ledger.LedgerEntryRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BalanceServiceTest {

    @Mock AccountRepository accounts;
    @Mock LedgerEntryRepository ledger;
    @InjectMocks BalanceService service;

    @Test
    void returnsBalanceForExistingAccount() {
        Account account = mock(Account.class);
        when(account.getId()).thenReturn(1L);
        when(account.getCurrency()).thenReturn("CAD");
        when(accounts.findById(1L)).thenReturn(Optional.of(account));
        when(ledger.deriveBalance(1L)).thenReturn(new BigDecimal("750.00"));

        BalanceResponse response = service.getBalance(1L);

        assertThat(response.accountId()).isEqualTo(1L);
        assertThat(response.currency()).isEqualTo("CAD");
        assertThat(response.balance()).isEqualByComparingTo("750.00");
    }

    @Test
    void throwsWhenAccountMissing() {
        when(accounts.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getBalance(99L))
                .isInstanceOf(AccountNotFoundException.class);

        // never bothers deriving a balance for an account that doesn't exist
        verify(ledger, never()).deriveBalance(any());
    }
}