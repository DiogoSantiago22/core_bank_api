package com.diogo.core_bancario.service;

import com.diogo.core_bancario.model.Account;
import com.diogo.core_bancario.model.Role;
import com.diogo.core_bancario.model.TransactionType;
import com.diogo.core_bancario.repository.AccountRepository;
import com.diogo.core_bancario.repository.TransactionRepository;
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

    @Mock // Adicionamos o mock do motor de encriptação
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private AccountService accountService;

    // --- TESTES DE DEPÓSITO ---

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
        // Atualizado com email, password e role
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

    // --- TESTES DE LEVANTAMENTO (WITHDRAW) ---

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

    // --- TESTES DE CRIAÇÃO DE CONTA ---

    @Test
    void shouldSuccessfullyCreateAccountWhenIdDoesNotExist() {
        String accountId = "200";
        String ownerName = "Maria Silva";
        String email = "maria@mail.com";
        String rawPassword = "password123";
        Role role = Role.CLIENT;

        when(accountRepository.existsById(accountId)).thenReturn(false);
        // Ensinamos o mock do encriptador a devolver uma string fictícia
        when(passwordEncoder.encode(rawPassword)).thenReturn("hashed_password123");

        // Chamada atualizada com os 5 parâmetros
        Account newAccount = accountService.createAccount(accountId, ownerName, email, rawPassword, role);

        assertNotNull(newAccount);
        assertEquals(accountId, newAccount.getId());
        assertEquals(ownerName, newAccount.getOwnerName());
        assertEquals(email, newAccount.getEmail());
        assertEquals("hashed_password123", newAccount.getPassword()); // Verifica se guardou a versão encriptada
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
        // Garante que se falhar na validação do ID, nem chega a gastar CPU a encriptar a password
        verify(passwordEncoder, never()).encode(anyString());
    }
}