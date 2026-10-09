package com.diogo.core_bancario.controller;


import com.diogo.core_bancario.model.Account;
import com.diogo.core_bancario.service.AccountService;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;

@RestController
@RequestMapping("/api/accounts") // Base URL for all endpoints in this controller
public class AccountController {

    private final AccountService accountService;

    public AccountController(AccountService accountService){
        this.accountService = accountService;
    }

    @PostMapping // HTTP POST requests (create new data)
    public Account createAccount(@RequestParam String id, // Extracts value from URL query (f.e.: ?id=123)
                                 @RequestParam String ownerName){
        return accountService.createAccount(id,ownerName);
    }
    @GetMapping("/{id}") // HTTP GET requests (read data)
    public Account getAccount(@PathVariable("id") String id) // Extracts dynamic value from URL path (f.e: /123)
    {
        return accountService.getAccount(id);
    }

    @PutMapping("/{id}/deposit")
    public Account deposit(@PathVariable("id") String id, @RequestParam BigDecimal amount){
        return accountService.deposit(id,amount);
    }
    @PutMapping("/{id}/withdraw")
    public Account withdraw(@PathVariable("id") String id, @RequestParam BigDecimal amount){
        return accountService.withdraw(id,amount);
    }


}
