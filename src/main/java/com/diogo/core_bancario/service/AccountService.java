package com.diogo.core_bancario.service;

import com.diogo.core_bancario.model.Account;
import com.diogo.core_bancario.repository.AccountRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

// Logic of the account
@Service
public class AccountService {
    private final AccountRepository repository;

    public AccountService(AccountRepository repository) {
        this.repository = repository;
    }

    public Account createAccount(String id,String ownerName){
        if(repository.existsById(id)){
            throw new IllegalArgumentException("Account ID alredy exists");
        }
        Account newAcount = new Account(id,ownerName,BigDecimal.valueOf(0));

        repository.save(newAcount);
        return newAcount;
    }

    public Account getAccount(String id){
        return repository.findById(id).orElseThrow(() ->
                new IllegalArgumentException("Account whit ID:" + id + " does not exists") //Because its Optimal "Optional<Account>"
        );
    }

    public Account deposit(String id, BigDecimal amount){
        int valid = amount.compareTo(BigDecimal.ZERO);
        if(valid <= 0){
            throw new IllegalArgumentException("Amount is lower or equals to zero");
        }

        Account account = getAccount(id);

        BigDecimal balance = account.getBalance();
        BigDecimal newBalance = balance.add(amount);

        account.setBalance(newBalance);
        repository.save(account); // Update DB
        return account;
    }

    public Account withdraw(String id, BigDecimal amount){
        int valid = amount.compareTo(BigDecimal.ZERO);
        if(valid <= 0){
            throw new IllegalArgumentException("Amount is lower or equals to zero");
        }

        Account account = getAccount(id);

        BigDecimal balance = account.getBalance();

        int validBalance = balance.compareTo(amount);
        if (validBalance < 0){
            throw new IllegalArgumentException("Amount is bigger then atual balance");
        }
        BigDecimal newBalance = balance.subtract(amount);

        account.setBalance(newBalance);
        repository.save(account); // Update DB
        return account;
    }

    public void transfer(String fromId,BigDecimal amount, String toId){
        getAccount(fromId);
        getAccount(toId);

        withdraw(fromId,amount);
        deposit(toId,amount);
    }
}
