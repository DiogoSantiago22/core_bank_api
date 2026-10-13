package com.diogo.core_bancario.repository;

import com.diogo.core_bancario.model.Transaction;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface TransactionRepository extends JpaRepository<Transaction, Long> {

    // Automatically generates the SQL query from this method name
    // SELECT * FROM transactions WHERE account_id = ? ORDER BY timestamp DESC
    List<Transaction> findByAccountIdOrderByTimestampDesc(String accountId);
}