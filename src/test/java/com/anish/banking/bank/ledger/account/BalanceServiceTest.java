package com.anish.banking.bank.ledger.account;

import com.anish.banking.bank.ledger.ledger.LedgerEntryRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the read-only, single-account paths of BalanceService.
 *
 * Deposit and withdraw are intentionally NOT unit-tested here. Since Phase 4 they
 * coordinate three repositories and two account mutations (customer + settlement)
 * across a single transaction — integration behavior whose correctness is proven
 * end-to-end, against a real database and the seeded settlement account, by
 * DoubleEntryInvariantTest. Mocking that coordination would mostly restate the
 * implementation rather than verify it.
 */
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
}