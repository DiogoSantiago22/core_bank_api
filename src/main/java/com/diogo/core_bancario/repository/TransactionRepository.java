package com.diogo.core_bancario.repository;

import com.diogo.core_bancario.model.Transaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public interface TransactionRepository extends JpaRepository<Transaction, Long> {

    // Automatically generates the SQL query from this method name
    // SELECT * FROM transactions WHERE account_id = ? ORDER BY timestamp DESC
    List<Transaction> findByAccountIdOrderByTimestampDesc(String accountId);

    @Query("""
            SELECT COALESCE(SUM(t.amount), 0)
            FROM Transaction t
            WHERE t.account.id = :accountId
              AND t.transactionType IN (
                  com.diogo.core_bancario.model.TransactionType.WITHDRAW,
                  com.diogo.core_bancario.model.TransactionType.TRANSFER
              )
              AND t.timestamp >= :startOfDay
            """)
    BigDecimal sumDailyOutgoingAmount(
            @Param("accountId") String accountId,
            @Param("startOfDay") LocalDateTime startOfDay
    );
}