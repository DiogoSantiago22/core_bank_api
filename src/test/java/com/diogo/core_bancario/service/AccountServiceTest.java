package com.diogo.core_bancario.service;

import com.diogo.core_bancario.dto.LoginDTO;
import com.diogo.core_bancario.model.Account;
import com.diogo.core_bancario.model.Role;
import com.diogo.core_bancario.model.TransactionType;
import com.diogo.core_bancario.repository.AccountRepository;
import com.diogo.core_bancario.repository.TransactionRepository;
import com.diogo.core_bancario.security.JwtService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AccountServiceTest {

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock // Added JwtService mock so Spring can instantiate AccountService
    private JwtService jwtService;

    @InjectMocks
    private AccountService accountService;

    // --- LOGIN TESTS ---

    @Test
    void shouldSuccessfullyLoginAndReturnToken() {
        LoginDTO loginDTO = new LoginDTO("diogo@mail.com", "minha_senha");
        Account account = new Account("100", "Diogo Santiago", BigDecimal.ZERO, "diogo@mail.com", "senha_encriptada", Role.CLIENT);

        when(accountRepository.findByEmail(loginDTO.email())).thenReturn(Optional.of(account));
        when(passwordEncoder.matches(loginDTO.password(), account.getPassword())).thenReturn(true);
        when(jwtService.generateToken(account.getEmail())).thenReturn("eyJh...token_valido");

        String token = accountService.login(loginDTO);

        assertEquals("eyJh...token_valido", token);
        verify(jwtService, times(1)).generateToken(account.getEmail());
    }

    @Test
    void shouldThrowExceptionWhenLoginEmailNotFound() {
        LoginDTO loginDTO = new LoginDTO("email_falso@mail.com", "minha_senha");

        when(accountRepository.findByEmail(loginDTO.email())).thenReturn(Optional.empty());

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> accountService.login(loginDTO)
        );

        assertEquals("Invalid password or email", exception.getMessage());
        verify(passwordEncoder, never()).matches(anyString(), anyString()); // If the email doesn't exist, it shouldn't waste CPU validating passwords
    }

    @Test
    void shouldThrowExceptionWhenLoginPasswordIsWrong() {
        LoginDTO loginDTO = new LoginDTO("diogo@mail.com", "senha_errada");
        Account account = new Account("100", "Diogo Santiago", BigDecimal.ZERO, "diogo@mail.com", "senha_encriptada", Role.CLIENT);

        when(accountRepository.findByEmail(loginDTO.email())).thenReturn(Optional.of(account));
        when(passwordEncoder.matches(loginDTO.password(), account.getPassword())).thenReturn(false);

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> accountService.login(loginDTO)
        );

        assertEquals("Invalid password or email", exception.getMessage());
        verify(jwtService, never()).generateToken(anyString()); // Cannot generate token if the password failed
    }

    // --- DEPOSIT TESTS ---

    @Test
    void shouldThrowExceptionWhenDepositIsNegative() {
        String accountId = "100";
        BigDecimal negativeAmount = new BigDecimal("-50.00");

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> accountService.deposit(accountId, negativeAmount)
        );

        assertEquals("Amount is lower or equals to zero", exception.getMessage());
        verify(accountRepository, never()).save(any(Account.class));
    }

    @Test
    void shouldSuccessfullyDepositWhenAccountExists() {
        String accountId = "100";
        Account existingAccount = new Account(accountId, "Diogo Santiago", new BigDecimal("100.00"), "diogo@mail.com", "hash", Role.CLIENT);
        BigDecimal depositAmount = new BigDecimal("50.00");

        when(accountRepository.findById(accountId)).thenReturn(Optional.of(existingAccount));

        Account updatedAccount = accountService.deposit(accountId, depositAmount);

        assertEquals(0, new BigDecimal("150.00").compareTo(updatedAccount.getBalance()));
        verify(accountRepository, times(1)).save(existingAccount);
        verify(transactionRepository, times(1)).save(any());
    }

    @Test
    void shouldThrowExceptionWhenAccountNotFoundOnDeposit() {
        String accountId = "999";
        BigDecimal amount = new BigDecimal("50.00");

        when(accountRepository.findById(accountId)).thenReturn(Optional.empty());

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> accountService.deposit(accountId, amount)
        );

        assertTrue(exception.getMessage().contains("does not exist"));
        verify(accountRepository, never()).save(any());
        verify(transactionRepository, never()).save(any());
    }

    // --- WITHDRAW TESTS ---

    @Test
    void shouldSuccessfullyWithdrawWhenBalanceIsSufficient() {
        String accountId = "100";
        Account existingAccount = new Account(accountId, "Diogo Santiago", new BigDecimal("200.00"), "diogo@mail.com", "hash", Role.CLIENT);
        BigDecimal withdrawAmount = new BigDecimal("50.00");

        when(accountRepository.findById(accountId)).thenReturn(Optional.of(existingAccount));

        Account updatedAccount = accountService.withdraw(accountId, withdrawAmount);

        assertEquals(0, new BigDecimal("150.00").compareTo(updatedAccount.getBalance()));
        verify(accountRepository, times(1)).save(existingAccount);
        verify(transactionRepository, times(1)).save(any());
    }

    @Test
    void shouldThrowExceptionWhenWithdrawExceedsBalance() {
        String accountId = "100";
        Account existingAccount = new Account(accountId, "Diogo Santiago", new BigDecimal("30.00"), "diogo@mail.com", "hash", Role.CLIENT);
        BigDecimal withdrawAmount = new BigDecimal("50.00");

        when(accountRepository.findById(accountId)).thenReturn(Optional.of(existingAccount));

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> accountService.withdraw(accountId, withdrawAmount)
        );

        assertEquals("Amount is bigger than actual balance", exception.getMessage());
        verify(accountRepository, never()).save(any());
    }

    @Test
    void shouldThrowExceptionWhenWithdrawIsNegative() {
        String accountId = "100";
        BigDecimal negativeAmount = new BigDecimal("-10.00");

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> accountService.withdraw(accountId, negativeAmount)
        );

        assertEquals("Amount is lower or equals to zero", exception.getMessage());
    }

    // --- ACCOUNT CREATION TESTS ---

    @Test
    void shouldSuccessfullyCreateAccountWhenIdDoesNotExist() {
        String accountId = "200";
        String ownerName = "Maria Silva";
        String email = "maria@mail.com";
        String rawPassword = "password123";
        Role role = Role.CLIENT;

        when(accountRepository.existsById(accountId)).thenReturn(false);
        when(passwordEncoder.encode(rawPassword)).thenReturn("hashed_password123");

        Account newAccount = accountService.createAccount(accountId, ownerName, email, rawPassword, role);

        assertNotNull(newAccount);
        assertEquals(accountId, newAccount.getId());
        assertEquals(ownerName, newAccount.getOwnerName());
        assertEquals(email, newAccount.getEmail());
        assertEquals("hashed_password123", newAccount.getPassword());
        assertEquals(0, BigDecimal.ZERO.compareTo(newAccount.getBalance()));

        verify(passwordEncoder, times(1)).encode(rawPassword);
        verify(accountRepository, times(1)).save(any(Account.class));
    }

    @Test
    void shouldThrowExceptionWhenCreatingAccountWithExistingId() {
        String accountId = "100";
        String ownerName = "Diogo Santiago";
        String email = "diogo@mail.com";
        String rawPassword = "password123";
        Role role = Role.CLIENT;

        when(accountRepository.existsById(accountId)).thenReturn(true);

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> accountService.createAccount(accountId, ownerName, email, rawPassword, role)
        );

        assertEquals("Account ID alredy exists", exception.getMessage());
        verify(accountRepository, never()).save(any());
        verify(passwordEncoder, never()).encode(anyString());
    }
}