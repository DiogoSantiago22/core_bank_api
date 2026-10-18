package com.diogo.core_bancario.controller;


import com.diogo.core_bancario.dto.*;
import com.diogo.core_bancario.model.Account;
import com.diogo.core_bancario.model.Transaction;
import com.diogo.core_bancario.service.AccountService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;


@RestController
@RequestMapping("/api/accounts") // Base URL for all endpoints in this controller
public class AccountController {

    private final AccountService accountService;

    public AccountController(AccountService accountService){
        this.accountService = accountService;
    }

    @PostMapping // HTTP POST requests (create new data)
    public AccountResponseDTO createAccount(@Valid @RequestBody AccountCreateDTO accountCreateDTO){
        Account account = accountService.createAccount(accountCreateDTO.id(),accountCreateDTO.ownerName());
        return toAccountDTO(account);
    }

    @GetMapping("/{id}") // HTTP GET requests (read data)
    public AccountResponseDTO getAccount(@PathVariable("id") String id) // Extracts dynamic value from URL path (f.e: /123)
    {
        Account account = accountService.getAccount(id);
        return toAccountDTO(account);
    }

    @PutMapping("/{id}/deposit")
    public AccountResponseDTO deposit(@PathVariable("id") String id,  @Valid @RequestBody AmountRequestDTO amountRequestDTO){
        Account account = accountService.deposit(id,amountRequestDTO.amount());
        return toAccountDTO(account);
    }
    @PutMapping("/{id}/withdraw")
    public AccountResponseDTO withdraw(@PathVariable("id") String id, @Valid @RequestBody AmountRequestDTO amountRequestDTO){
        Account account = accountService.withdraw(id,amountRequestDTO.amount());
        return toAccountDTO(account);
    }

    @PostMapping("/transfer")
    public void transfer(@Valid @RequestBody TransferRequestDTO transferRequestDTO){
        accountService.transfer(transferRequestDTO.fromId(),transferRequestDTO.amount(),transferRequestDTO.toId());
    }

    @GetMapping("/{id}/transactions")
    public List<TransactionResponseDTO> getTransactions(@PathVariable("id") String id){
        List<Transaction> transactions = accountService.getTransactions(id);

        List<TransactionResponseDTO> responseList = new ArrayList<>();

        for (Transaction transaction : transactions) {
            TransactionResponseDTO dto = toTransactionDTO(transaction);
            responseList.add(dto);
        }

        return responseList;
    }


    private AccountResponseDTO toAccountDTO(Account account){
        return new AccountResponseDTO(account.getId(), account.getOwnerName(), account.getBalance());
    }

    private TransactionResponseDTO toTransactionDTO(Transaction transaction) {
        return new TransactionResponseDTO(transaction.getId(), transaction.getTransactionType(), transaction.getAmount(), transaction.getTimestamp());
    }
}
