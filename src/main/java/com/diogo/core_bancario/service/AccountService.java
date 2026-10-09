package com.diogo.core_bancario.service;

import com.diogo.core_bancario.model.Account;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

// Logic of the account
@Service
public class AccountService {
    // For now im using this in RAM for "Data Base"
    private final Map<String, Account> repository = new HashMap<>();


    public Account createAccount(String id,String ownerName){
        if(repository.containsKey(id)){
            throw new IllegalArgumentException("Account ID alredy exists");
        }
        Account newAcount = new Account(id,ownerName,BigDecimal.valueOf(0));

        repository.put(id, newAcount);
        return newAcount;
    }

    public Account getAccount(String id){
        Account account = repository.get(id);
        if(account == null){
            throw new IllegalArgumentException("Account whit ID:" + id + " does not exists");
        }
        return account;
    }

}
