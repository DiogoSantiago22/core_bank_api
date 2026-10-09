package com.diogo.core_bancario.model;

import java.math.BigDecimal;

public class Account {
    private String id;
    private String ownerName;
    private String ownerAge;
    private BigDecimal balance;

    public Account(String id, String ownerName, BigDecimal balance){
        this.id = id;
        this.ownerName = ownerName;
        this.balance = balance;
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
