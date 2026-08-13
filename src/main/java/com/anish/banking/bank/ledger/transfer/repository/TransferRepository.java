package com.anish.banking.bank.ledger.transfer.repository;

import com.anish.banking.bank.ledger.transfer.model.Transfer;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TransferRepository extends JpaRepository<Transfer, Long> {}
