package com.diogo.core_bancario.model;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name="transactions")
public class Transaction
{
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne //Many transaction belong to One Account
    @JoinColumn(name = "account_id", nullable = false)
    private Account account;
    private BigDecimal amount;
    @Enumerated(EnumType.STRING)

    private TransactionType transactionType;
    private LocalDateTime timestamp;

    public Transaction(Long id, Account account,BigDecimal amount, TransactionType transactionType,LocalDateTime timestamp){
        this.id = id;
        this.account= account;
        this.amount = amount;
        this.transactionType = transactionType;
        this.timestamp = LocalDateTime.now();
    }

    public Transaction() {

    }

    public Long getId(){
        return id;
    }
    public Account getAccount(){
        return account;
    }
    public BigDecimal getAmount(){
        return amount;
    }
    public LocalDateTime getTimestamp(){
        return timestamp;
    }
    public TransactionType getTransactionType(){
        return transactionType;
    }


}
