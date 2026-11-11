package com.diogo.core_bancario.service;

import com.diogo.core_bancario.dto.LoginDTO;
import com.diogo.core_bancario.model.Account;
import com.diogo.core_bancario.model.Role;
import com.diogo.core_bancario.model.Transaction;
import com.diogo.core_bancario.model.TransactionType;
import com.diogo.core_bancario.repository.AccountRepository;
import com.diogo.core_bancario.repository.TransactionRepository;
import com.diogo.core_bancario.security.JwtService;
import jakarta.transaction.Transactional;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;

import com.diogo.core_bancario.exception.ForbiddenException;
import com.diogo.core_bancario.exception.ResourceNotFoundException;
import com.diogo.core_bancario.exception.UnauthorizedException;

// Logic of the account
@Service
public class AccountService {
    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AccountService(AccountRepository repository, TransactionRepository transactionRepository, PasswordEncoder passwordEncoder, JwtService jwtService) {
        this.accountRepository = repository;
        this.transactionRepository = transactionRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    public Account createAccount(String id,String ownerName,String email, String password, Role role){
        if(accountRepository.existsById(id)){
            throw new IllegalArgumentException("Account ID already exists");
        }
        String hashedPassword = passwordEncoder.encode(password);
        Account newAcount = new Account(id,ownerName,BigDecimal.valueOf(0),email,hashedPassword,role);

        accountRepository.save(newAcount);
        return newAcount;
    }

    public Account getAccount(String id){
        return accountRepository.findById(id).orElseThrow(() ->
                new ResourceNotFoundException("Account with ID: " + id + " does not exist")
        );
    }

    private Account getAccountForUpdate(String id) {
        return accountRepository.findByIdForUpdate(id).orElseThrow(() ->
                new ResourceNotFoundException("Account with ID: " + id + " does not exist")
        );
    }

    @Transactional // Ensures both saves happen safely
    public Account deposit(String id, BigDecimal amount){
        int valid = amount.compareTo(BigDecimal.ZERO);
        if(valid <= 0){
            throw new IllegalArgumentException("Amount must be greater than zero");
        }

        Account account = getAccountForUpdate(id);

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
            throw new IllegalArgumentException("Amount must be greater than zero");
        }

        Account account = getAccountForUpdate(id);
        if (!account.getEmail().equals(getEmailFromSecurityContextHolder())){
            throw new ForbiddenException("Not your account");
        }
        BigDecimal balance = account.getBalance();

        int validBalance = balance.compareTo(amount);
        if (validBalance < 0){
            throw new IllegalArgumentException("Amount is bigger than actual balance");
        }
        BigDecimal newBalance = balance.subtract(amount);
        account.setBalance(newBalance);

        Transaction transaction = new Transaction(account,amount,TransactionType.WITHDRAW);

        accountRepository.save(account); // Update DB
        transactionRepository.save(transaction);
        return account;
    }

    @Transactional
    public void transfer(String fromId, BigDecimal amount, String toId){
        if (fromId.equals(toId)) {
            throw new IllegalArgumentException("Cannot transfer to the same account");
        }
        if(amount.compareTo(BigDecimal.ZERO) <= 0){
            throw new IllegalArgumentException("Amount must be greater than zero");
        }

        // DEADLOCK PREVENTION
        Account firstLock, secondLock;
        if (fromId.compareTo(toId) < 0) {
            firstLock = getAccountForUpdate(fromId);
            secondLock = getAccountForUpdate(toId);
        } else {
            firstLock = getAccountForUpdate(toId);
            secondLock = getAccountForUpdate(fromId);
        }

        Account sender = fromId.equals(firstLock.getId()) ? firstLock : secondLock;
        Account receiver = toId.equals(firstLock.getId()) ? firstLock : secondLock;

        if (!sender.getEmail().equals(getEmailFromSecurityContextHolder())){
            throw new ForbiddenException("You can only transfer from your own account");
        }
        if (sender.getBalance().compareTo(amount) < 0){
            throw new IllegalArgumentException("Insufficient balance for transfer");
        }

        sender.setBalance(sender.getBalance().subtract(amount));
        receiver.setBalance(receiver.getBalance().add(amount));

        transactionRepository.save(new Transaction(sender, amount, TransactionType.WITHDRAW));
        transactionRepository.save(new Transaction(receiver, amount, TransactionType.DEPOSIT));

        accountRepository.save(sender);
        accountRepository.save(receiver);
    }

    public String login(LoginDTO loginDTO){
        Account account = accountRepository.findByEmail(loginDTO.email()).orElseThrow((() -> new UnauthorizedException("Invalid password or email")));

        if (!passwordEncoder.matches(loginDTO.password(), account.getPassword())) { throw new UnauthorizedException("Invalid password or email");}
        return jwtService.generateToken(account.getEmail());
    }

    public List<Transaction> getTransactions(String id){
        // Security check: ensure users can only view their own transaction history
        if (!getAccount(id).getEmail().equals(getEmailFromSecurityContextHolder())){
            throw new ForbiddenException("Not your account");
        }
        return transactionRepository.findByAccountIdOrderByTimestampDesc(id);
    }

    private String getEmailFromSecurityContextHolder(){
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        if (email == null){
            throw new UnauthorizedException("No email found");
        }
       return email;
    }
}
