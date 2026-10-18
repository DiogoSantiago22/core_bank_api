package com.diogo.core_bancario.service;

import com.diogo.core_bancario.model.Account;
import com.diogo.core_bancario.model.TransactionType;
import com.diogo.core_bancario.repository.AccountRepository;
import com.diogo.core_bancario.repository.TransactionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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
        Account existingAccount = new Account(accountId, "Diogo Santiago", new BigDecimal("100.00"));
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

        assertTrue(exception.getMessage().contains("does not exists"));
        verify(accountRepository, never()).save(any());
        verify(transactionRepository, never()).save(any());
    }

    // --- TESTES DE LEVANTAMENTO (WITHDRAW) ---

    @Test
    void shouldSuccessfullyWithdrawWhenBalanceIsSufficient() {
        String accountId = "100";
        Account existingAccount = new Account(accountId, "Diogo Santiago", new BigDecimal("200.00"));
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
        Account existingAccount = new Account(accountId, "Diogo Santiago", new BigDecimal("30.00"));
        BigDecimal withdrawAmount = new BigDecimal("50.00");

        when(accountRepository.findById(accountId)).thenReturn(Optional.of(existingAccount));

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> accountService.withdraw(accountId, withdrawAmount)
        );

        assertEquals("Amount is bigger then atual balance", exception.getMessage());
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

        when(accountRepository.existsById(accountId)).thenReturn(false);

        Account newAccount = accountService.createAccount(accountId, ownerName);

        assertNotNull(newAccount);
        assertEquals(accountId, newAccount.getId());
        assertEquals(ownerName, newAccount.getOwnerName());
        assertEquals(0, BigDecimal.ZERO.compareTo(newAccount.getBalance()));
        verify(accountRepository, times(1)).save(any(Account.class));
    }

    @Test
    void shouldThrowExceptionWhenCreatingAccountWithExistingId() {
        String accountId = "100";
        String ownerName = "Diogo Santiago";

        when(accountRepository.existsById(accountId)).thenReturn(true);

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> accountService.createAccount(accountId, ownerName)
        );

        assertEquals("Account ID alredy exists", exception.getMessage());
        verify(accountRepository, never()).save(any());
    }
}