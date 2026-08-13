package com.anish.banking.bank.ledger.account.model;

import com.anish.banking.bank.ledger.transfer.exception.InsufficientFundsException;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import static org.assertj.core.api.Assertions.*;

class AccountDebitFloorTest {

    @Test
    void customerCannotOverdraw() {
        Account customer = new Account("Test Customer", "CAD"); // defaults to CUSTOMER
        customer.credit(new BigDecimal("100.00"));
        assertThatThrownBy(() -> customer.debit(new BigDecimal("150.00")))
                .isInstanceOf(InsufficientFundsException.class);
    }

    @Test
    void positiveAmountGuardStillFiresForEveryone() {
        Account customer = new Account("Test Customer", "CAD");
        assertThatThrownBy(() -> customer.debit(BigDecimal.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> customer.debit(new BigDecimal("-5.00")))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
