package com.diogo.core_bancario.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;

import java.math.BigDecimal;
@Entity //Create Table
public class Account {
    @Id //PK
    private String id;
    private String ownerName;
    private String ownerAge;
    private BigDecimal balance;

    public Account(String id, String ownerName, BigDecimal balance){
        this.id = id;
        this.ownerName = ownerName;
        this.balance = balance;
    }
    // should have [public, protected] no-arg constructor
    public Account() {
    }

    // Getters:
    public String getId(){
        return id;
    }
    public String getOwnerName(){
        return ownerName;
    }
    public BigDecimal getBalance(){
        return balance;
    }

    // Setters:
    public void setBalance(BigDecimal balance){
        this.balance = balance;
    }
}
