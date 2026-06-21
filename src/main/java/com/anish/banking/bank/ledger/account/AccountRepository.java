package com.anish.banking.bank.ledger.account;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AccountRepository extends JpaRepository<Account, Long> {

    Optional<Account> findByAccountTypeAndCurrency(AccountType accountType, String currency);
}