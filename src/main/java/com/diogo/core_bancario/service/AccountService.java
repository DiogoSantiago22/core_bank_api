package com.diogo.core_bancario.service;

import com.diogo.core_bancario.model.Account;
import com.diogo.core_bancario.model.Transaction;
import com.diogo.core_bancario.model.TransactionType;
import com.diogo.core_bancario.repository.AccountRepository;
import com.diogo.core_bancario.repository.TransactionRepository;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;

// Logic of the account
@Service
public class AccountService {
    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;

    public AccountService(AccountRepository repository, TransactionRepository transactionRepository) {
        this.accountRepository = repository;
        this.transactionRepository = transactionRepository;
    }

    public Account createAccount(String id,String ownerName){
        if(accountRepository.existsById(id)){
            throw new IllegalArgumentException("Account ID alredy exists");
        }
        Account newAcount = new Account(id,ownerName,BigDecimal.valueOf(0));

        accountRepository.save(newAcount);
        return newAcount;
    }

    public Account getAccount(String id){
        return accountRepository.findById(id).orElseThrow(() ->
                new IllegalArgumentException("Account whit ID:" + id + " does not exists") //Because its Optimal "Optional<Account>"
        );
    }

    @Transactional // Ensures both saves happen safely
    public Account deposit(String id, BigDecimal amount){
        int valid = amount.compareTo(BigDecimal.ZERO);
        if(valid <= 0){
            throw new IllegalArgumentException("Amount is lower or equals to zero");
        }

        Account account = getAccount(id);

        BigDecimal balance = account.getBalance();
        BigDecimal newBalance = balance.add(amount);

        account.setBalance(newBalance);

        Transaction transaction = new Transaction(account,amount, TransactionType.DEPOSIT);

        accountRepository.save(account); // Update DB
        transactionRepository.save(transaction);
        return account;
    }

    @Transactional // Ensures both saves happen safely
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

        Transaction transaction = new Transaction(account,amount,TransactionType.WITHDRAW);

        accountRepository.save(account); // Update DB
        transactionRepository.save(transaction);
        return account;
    }

    @Transactional
    public void transfer(String fromId,BigDecimal amount, String toId){
        getAccount(fromId);
        getAccount(toId);

        withdraw(fromId,amount);
        deposit(toId,amount);
    }

    public List<Transaction> getTransactions(String id){
        getAccount(id);
        return transactionRepository.findByAccountIdOrderByTimestampDesc(id);
    }
}
