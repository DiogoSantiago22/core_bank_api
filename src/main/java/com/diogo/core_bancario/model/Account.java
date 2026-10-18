package com.diogo.core_bancario.model;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;

import java.math.BigDecimal;
@Entity //Create Table
public class Account {
    @Id //PK
    private String id;
    private String ownerName;
    private BigDecimal balance;
    private String email;
    private String password;

    @Enumerated(EnumType.STRING) // To save "ADMIN" and not "0" f.e.
    private Role role;

    public Account(String id, String ownerName, BigDecimal balance,String email,String password, Role role){
        this.id = id;
        this.ownerName = ownerName;
        this.balance = balance;
        this.email = email;
        this.password = password;
        this.role = role;
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
    public String getEmail(){return email;}
    public String getPassword(){return password;}
    public Role getRole(){return role;}


    // Setters:
    public void setBalance(BigDecimal balance){
        this.balance = balance;
    }
    public void setPassword(String password){this.password = password;}
    public void setEmail(String email){this.email = email;}
    public void setRole(Role role){this.role = role;}

}
