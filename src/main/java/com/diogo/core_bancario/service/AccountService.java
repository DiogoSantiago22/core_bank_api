package com.diogo.core_bancario.service;

import com.diogo.core_bancario.dto.LoginDTO;
import com.diogo.core_bancario.model.*;
import com.diogo.core_bancario.repository.AccountRepository;
import com.diogo.core_bancario.repository.TransactionRepository;
import com.diogo.core_bancario.security.JwtService;
import jakarta.transaction.Transactional;

import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
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

    private static final BigDecimal DAILY_OUTGOING_LIMIT = new BigDecimal("1000.00");

    public AccountService(AccountRepository repository, TransactionRepository transactionRepository, PasswordEncoder passwordEncoder, JwtService jwtService) {
        this.accountRepository = repository;
        this.transactionRepository = transactionRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    public Account createAccount(String id,String ownerName,String email, String password, Role role, State state){
        if(accountRepository.existsById(id)){
            throw new IllegalArgumentException("Account ID already exists");
        }

        if(accountRepository.existsByEmail(email)){
            throw new IllegalArgumentException("Email is already in use");
        }
        String hashedPassword = passwordEncoder.encode(password);
        Account newAcount = new Account(id,ownerName,BigDecimal.valueOf(0),email,hashedPassword,role, state);

        accountRepository.save(newAcount);
        return newAcount;
    }

    public Account getAccount(String id) {
        Account account = accountRepository.findById(id).orElseThrow(() ->
                new ResourceNotFoundException("Account with ID: " + id + " does not exist")
        );

        String currentEmail = getEmailFromSecurityContextHolder();
        boolean isAdmin = isAdmin();

        if (!account.getEmail().equals(currentEmail) && !isAdmin) {
            throw new ForbiddenException("Not your account");
        }

        return account;
    }

    @Transactional
    public Account activeAccount(String id){

        Account account = getAccountForUpdate(id);
        if(!isAdmin()){
            throw new ForbiddenException("Only admins can change the account state");
        }
        State atualState = account.getState();
        if(atualState == State.FROZEN){
            account.setState(State.ACTIVE);} else if (atualState == State.ACTIVE) {throw new IllegalArgumentException("Account with ID: " + id + " is already active");}
        else throw new IllegalArgumentException("Account with ID: " + id + " does not have a state");

        accountRepository.save(account);

        return account;
        }

    @Transactional
    public Account frozenAccount(String id){

        Account account = getAccountForUpdate(id);
        if(!isAdmin()){
            throw new ForbiddenException("Only admins can change the account state");
        }
        State atualState = account.getState();
        if(atualState == State.ACTIVE){
            account.setState(State.FROZEN);} else if (atualState == State.FROZEN) {throw new IllegalArgumentException("Account with ID: " + id + " is already frozen");}
        else throw new IllegalArgumentException("Account with ID: " + id + " does not have a state");

        accountRepository.save(account);

        return account;
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
        if (!account.getEmail().equals(getEmailFromSecurityContextHolder())) {
            throw new ForbiddenException("Not your account");
        }
        if(account.getState() == State.FROZEN){
            throw new IllegalArgumentException("Your account is frozen");
        }

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

        if(account.getState() == State.FROZEN){
            throw new IllegalArgumentException("Your account is frozen");
        }

        BigDecimal balance = account.getBalance();

        int validBalance = balance.compareTo(amount);
        if (validBalance < 0){
            throw new IllegalArgumentException("Amount is bigger than actual balance");
        }

        if(isDailyLimitExceeded(id, amount)){
            throw new IllegalArgumentException("Daily outgoing limit of " + DAILY_OUTGOING_LIMIT + " exceeded");
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
        if(sender.getState() == State.FROZEN || receiver.getState() == State.FROZEN ){
            throw new IllegalArgumentException("One of the accounts is frozen");
        }
        if (sender.getBalance().compareTo(amount) < 0){
            throw new IllegalArgumentException("Insufficient balance for transfer");
        }

        if(isDailyLimitExceeded(fromId, amount)){
            throw new IllegalArgumentException("Daily outgoing limit of " + DAILY_OUTGOING_LIMIT + " exceeded");
        }

        sender.setBalance(sender.getBalance().subtract(amount));
        receiver.setBalance(receiver.getBalance().add(amount));

        transactionRepository.save(new Transaction(sender, amount, TransactionType.WITHDRAW));
        transactionRepository.save(new Transaction(receiver, amount, TransactionType.DEPOSIT));

        accountRepository.save(sender);
        accountRepository.save(receiver);
    }

    public String login(LoginDTO loginDTO) {
        Account account = accountRepository.findByEmail(loginDTO.email()).orElseThrow(() -> new UnauthorizedException("Invalid password or email"));

        if (!passwordEncoder.matches(loginDTO.password(), account.getPassword())) {
            throw new UnauthorizedException("Invalid password or email");
        }
        return jwtService.generateToken(account.getEmail(), account.getRole());
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

    private boolean isAdmin(){
        boolean isAdmin = SecurityContextHolder.getContext().getAuthentication().getAuthorities()
                .stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
        return isAdmin;
    }

    private boolean isDailyLimitExceeded(String id, BigDecimal amount) {
        BigDecimal sum = transactionRepository.sumDailyOutgoingAmount(id, LocalDate.now().atStartOfDay());
        if (sum == null) {
            sum = BigDecimal.ZERO;
        }
        return sum.add(amount).compareTo(DAILY_OUTGOING_LIMIT) > 0;
    }
}
