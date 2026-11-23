package com.diogo.core_bancario.model;

import jakarta.persistence.*;

import java.math.BigDecimal;
@Entity //Create Table
public class Account {
    @Id //PK
    private String id;
    private String ownerName;
    private BigDecimal balance;
    @Column(unique = true)
    private String email;
    private String password;

    @Enumerated(EnumType.STRING) // To save "ADMIN" and not "0" f.e.
    private Role role;

    @Enumerated(EnumType.STRING)
    private State state;


    public Account(String id, String ownerName, BigDecimal balance,String email,String password, Role role, State state){
        this.id = id;
        this.ownerName = ownerName;
        this.balance = balance;
        this.email = email;
        this.password = password;
        this.role = role;
        this.state = state;
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
    public State getState(){return state;}


    // Setters:
    public void setBalance(BigDecimal balance){
        this.balance = balance;
    }
    public void setPassword(String password){this.password = password;}
    public void setEmail(String email){this.email = email;}
    public void setRole(Role role){this.role = role;}
    public void setState(State state){this.state = state;}

}
