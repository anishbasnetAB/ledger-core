package com.anish.banking.bank.ledger.account;

import com.anish.banking.bank.ledger.ledger.EntryType;
import com.anish.banking.bank.ledger.ledger.LedgerEntry;
import com.anish.banking.bank.ledger.ledger.LedgerEntryRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatcher;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BalanceServiceTest {

    @Mock
    private AccountRepository accounts;

    @Mock
    private LedgerEntryRepository ledger;

    @InjectMocks
    private BalanceService balanceService;

    @Test
    void returnsBalanceForExistingAccount() {
        Account account = new Account("Anish", "CAD");
        ReflectionTestUtils.setField(account, "id", 1L);
        account.credit(new BigDecimal("900.00"));

        when(accounts.findById(1L)).thenReturn(Optional.of(account));

        BalanceResponse response = balanceService.getBalance(1L);

        assertThat(response).isNotNull();
        assertThat(response.accountId()).isEqualTo(1L);
        assertThat(response.currency()).isEqualTo("CAD");
        assertThat(response.balance()).isEqualByComparingTo("900.00");

        verify(accounts).findById(1L);
        verifyNoInteractions(ledger);
    }

    @Test
    void throwsWhenAccountDoesNotExist() {
        when(accounts.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> balanceService.getBalance(99L))
                .isInstanceOf(AccountNotFoundException.class);

        verify(accounts).findById(99L);
        verifyNoInteractions(ledger);
    }

    @Test
    void depositCreditsAccountAndWritesLedgerEntry() {
        Account account = new Account("Anish", "CAD");
        ReflectionTestUtils.setField(account, "id", 1L);

        when(accounts.findById(1L)).thenReturn(Optional.of(account));

        BalanceResponse response = balanceService.deposit(1L, new BigDecimal("50.00"));

        assertThat(response.accountId()).isEqualTo(1L);
        assertThat(response.currency()).isEqualTo("CAD");
        assertThat(response.balance()).isEqualByComparingTo("50.00");

        verify(ledger).save(argThat(isLedgerEntry(
                1L,
                EntryType.CREDIT,
                new BigDecimal("50.00")
        )));
    }

    @Test
    void withdrawDebitsAccountAndWritesLedgerEntry() {
        Account account = new Account("Anish", "CAD");
        ReflectionTestUtils.setField(account, "id", 1L);
        account.credit(new BigDecimal("100.00"));

        when(accounts.findById(1L)).thenReturn(Optional.of(account));

        BalanceResponse response = balanceService.withdraw(1L, new BigDecimal("50.00"));

        assertThat(response.accountId()).isEqualTo(1L);
        assertThat(response.currency()).isEqualTo("CAD");
        assertThat(response.balance()).isEqualByComparingTo("50.00");

        verify(ledger).save(argThat(isLedgerEntry(
                1L,
                EntryType.DEBIT,
                new BigDecimal("50.00")
        )));
    }

    private ArgumentMatcher<LedgerEntry> isLedgerEntry(
            Long accountId,
            EntryType entryType,
            BigDecimal amount
    ) {
        return entry ->
                entry != null
                        && entry.getAccountId().equals(accountId)
                        && entry.getEntryType() == entryType
                        && entry.getAmount().compareTo(amount) == 0;
    }
}