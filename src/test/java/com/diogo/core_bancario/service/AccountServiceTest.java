package com.diogo.core_bancario.service;

import com.diogo.core_bancario.dto.LoginDTO;
import com.diogo.core_bancario.exception.ForbiddenException;
import com.diogo.core_bancario.exception.ResourceNotFoundException;
import com.diogo.core_bancario.exception.UnauthorizedException;
import com.diogo.core_bancario.model.Account;
import com.diogo.core_bancario.model.Role;
import com.diogo.core_bancario.model.State;
import com.diogo.core_bancario.repository.AccountRepository;
import com.diogo.core_bancario.repository.TransactionRepository;
import com.diogo.core_bancario.security.JwtService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.math.BigDecimal;
import java.util.List;
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

    @Mock
    private JwtService jwtService;

    @InjectMocks
    private AccountService accountService;

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    // --- SECURITY TEST UTILS ---
    private void mockAuthenticatedUser(String email) {
        Authentication authentication = mock(Authentication.class);
        SecurityContext securityContext = mock(SecurityContext.class);
        when(securityContext.getAuthentication()).thenReturn(authentication);
        lenient().when(authentication.getName()).thenReturn(email);
        SecurityContextHolder.setContext(securityContext);
    }

    private void mockAuthenticatedAdmin(String email) {
        Authentication authentication = mock(Authentication.class);
        SecurityContext securityContext = mock(SecurityContext.class);
        when(securityContext.getAuthentication()).thenReturn(authentication);
        lenient().when(authentication.getName()).thenReturn(email);
        doReturn(List.of(new SimpleGrantedAuthority("ROLE_ADMIN")))
                .when(authentication).getAuthorities();
        SecurityContextHolder.setContext(securityContext);
    }

    // --- LOGIN TESTS ---

    @Test
    void shouldSuccessfullyLoginAndReturnToken() {
        LoginDTO loginDTO = new LoginDTO("diogo@mail.com", "minha_senha");
        Account account = new Account("100", "Diogo Santiago", BigDecimal.ZERO, "diogo@mail.com", "senha_encriptada", Role.CLIENT, State.ACTIVE);

        when(accountRepository.findByEmail(loginDTO.email())).thenReturn(Optional.of(account));
        when(passwordEncoder.matches(loginDTO.password(), account.getPassword())).thenReturn(true);
        when(jwtService.generateToken(account.getEmail(), account.getRole())).thenReturn("eyJh...token_valido");

        String token = accountService.login(loginDTO);

        assertEquals("eyJh...token_valido", token);
        verify(jwtService, times(1)).generateToken(account.getEmail(), account.getRole());
    }

    @Test
    void shouldThrowUnauthorizedExceptionWhenLoginEmailNotFound() {
        LoginDTO loginDTO = new LoginDTO("email_falso@mail.com", "minha_senha");

        when(accountRepository.findByEmail(loginDTO.email())).thenReturn(Optional.empty());

        UnauthorizedException exception = assertThrows(
                UnauthorizedException.class,
                () -> accountService.login(loginDTO)
        );

        assertEquals("Invalid password or email", exception.getMessage());
        verify(passwordEncoder, never()).matches(anyString(), anyString());
    }

    @Test
    void shouldThrowUnauthorizedExceptionWhenLoginPasswordIsWrong() {
        LoginDTO loginDTO = new LoginDTO("diogo@mail.com", "senha_errada");
        Account account = new Account("100", "Diogo Santiago", BigDecimal.ZERO, "diogo@mail.com", "senha_encriptada", Role.CLIENT, State.ACTIVE);

        when(accountRepository.findByEmail(loginDTO.email())).thenReturn(Optional.of(account));
        when(passwordEncoder.matches(loginDTO.password(), account.getPassword())).thenReturn(false);

        UnauthorizedException exception = assertThrows(
                UnauthorizedException.class,
                () -> accountService.login(loginDTO)
        );

        assertEquals("Invalid password or email", exception.getMessage());
        verify(jwtService, never()).generateToken(any(), any());
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

        assertEquals("Amount must be greater than zero", exception.getMessage());
        verify(accountRepository, never()).save(any(Account.class));
    }

    @Test
    void shouldSuccessfullyDepositWhenAccountExists() {
        mockAuthenticatedUser("diogo@mail.com");

        String accountId = "100";
        Account existingAccount = new Account(accountId, "Diogo Santiago", new BigDecimal("100.00"), "diogo@mail.com", "hash", Role.CLIENT, State.ACTIVE);
        BigDecimal depositAmount = new BigDecimal("50.00");

        when(accountRepository.findByIdForUpdate(accountId)).thenReturn(Optional.of(existingAccount));

        Account updatedAccount = accountService.deposit(accountId, depositAmount);

        assertEquals(0, new BigDecimal("150.00").compareTo(updatedAccount.getBalance()));
        verify(accountRepository, times(1)).save(existingAccount);
        verify(transactionRepository, times(1)).save(any());
    }

    @Test
    void shouldThrowForbiddenExceptionWhenNotOwnerOnDeposit() {
        mockAuthenticatedUser("hacker@mail.com");

        String accountId = "100";
        Account existingAccount = new Account(accountId, "Diogo Santiago", new BigDecimal("100.00"), "diogo@mail.com", "hash", Role.CLIENT, State.ACTIVE);

        when(accountRepository.findByIdForUpdate(accountId)).thenReturn(Optional.of(existingAccount));

        ForbiddenException exception = assertThrows(
                ForbiddenException.class,
                () -> accountService.deposit(accountId, new BigDecimal("50.00"))
        );

        assertEquals("Not your account", exception.getMessage());
        verify(accountRepository, never()).save(any());
        verify(transactionRepository, never()).save(any());
    }

    @Test
    void shouldThrowResourceNotFoundExceptionWhenAccountNotFoundOnDeposit() {
        String accountId = "999";
        BigDecimal amount = new BigDecimal("50.00");

        when(accountRepository.findByIdForUpdate(accountId)).thenReturn(Optional.empty());

        ResourceNotFoundException exception = assertThrows(
                ResourceNotFoundException.class,
                () -> accountService.deposit(accountId, amount)
        );

        assertTrue(exception.getMessage().contains("does not exist"));
        verify(accountRepository, never()).save(any());
        verify(transactionRepository, never()).save(any());
    }

    @Test
    void shouldThrowIllegalArgumentExceptionWhenDepositingIntoFrozenAccount() {
        mockAuthenticatedUser("diogo@mail.com");

        String accountId = "100";
        Account frozenAccount = new Account(accountId, "Diogo Santiago", new BigDecimal("100.00"), "diogo@mail.com", "hash", Role.CLIENT, State.FROZEN);

        when(accountRepository.findByIdForUpdate(accountId)).thenReturn(Optional.of(frozenAccount));

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> accountService.deposit(accountId, new BigDecimal("50.00"))
        );

        assertEquals("Your account is frozen", exception.getMessage());
        verify(accountRepository, never()).save(any());
        verify(transactionRepository, never()).save(any());
    }

    // --- WITHDRAW TESTS ---

    @Test
    void shouldThrowForbiddenExceptionWhenNotOwnerOnWithdraw() {
        mockAuthenticatedUser("hacker@mail.com");

        String accountId = "100";
        Account existingAccount = new Account(accountId, "Diogo Santiago", new BigDecimal("200.00"), "diogo@mail.com", "hash", Role.CLIENT, State.ACTIVE);

        when(accountRepository.findByIdForUpdate(accountId)).thenReturn(Optional.of(existingAccount));

        ForbiddenException exception = assertThrows(
                ForbiddenException.class,
                () -> accountService.withdraw(accountId, new BigDecimal("50.00"))
        );

        assertEquals("Not your account", exception.getMessage());
        verify(accountRepository, never()).save(any());
    }

    @Test
    void shouldSuccessfullyWithdrawWhenBalanceIsSufficientAndIsOwner() {
        mockAuthenticatedUser("diogo@mail.com");

        String accountId = "100";
        Account existingAccount = new Account(accountId, "Diogo Santiago", new BigDecimal("200.00"), "diogo@mail.com", "hash", Role.CLIENT, State.ACTIVE);
        BigDecimal withdrawAmount = new BigDecimal("50.00");

        when(accountRepository.findByIdForUpdate(accountId)).thenReturn(Optional.of(existingAccount));

        Account updatedAccount = accountService.withdraw(accountId, withdrawAmount);

        assertEquals(0, new BigDecimal("150.00").compareTo(updatedAccount.getBalance()));
        verify(accountRepository, times(1)).save(existingAccount);
        verify(transactionRepository, times(1)).save(any());
    }

    @Test
    void shouldThrowExceptionWhenWithdrawExceedsBalance() {
        mockAuthenticatedUser("diogo@mail.com");

        String accountId = "100";
        Account existingAccount = new Account(accountId, "Diogo Santiago", new BigDecimal("30.00"), "diogo@mail.com", "hash", Role.CLIENT, State.ACTIVE);
        BigDecimal withdrawAmount = new BigDecimal("50.00");

        when(accountRepository.findByIdForUpdate(accountId)).thenReturn(Optional.of(existingAccount));

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> accountService.withdraw(accountId, withdrawAmount)
        );

        assertEquals("Amount is bigger than actual balance", exception.getMessage());
        verify(accountRepository, never()).save(any());
    }

    @Test
    void shouldThrowIllegalArgumentExceptionWhenWithdrawingFromFrozenAccount() {
        mockAuthenticatedUser("diogo@mail.com");

        String accountId = "100";
        Account frozenAccount = new Account(accountId, "Diogo Santiago", new BigDecimal("200.00"), "diogo@mail.com", "hash", Role.CLIENT, State.FROZEN);

        when(accountRepository.findByIdForUpdate(accountId)).thenReturn(Optional.of(frozenAccount));

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> accountService.withdraw(accountId, new BigDecimal("50.00"))
        );

        assertEquals("Your account is frozen", exception.getMessage());
        verify(accountRepository, never()).save(any());
        verify(transactionRepository, never()).save(any());
    }

    // --- TRANSFER TESTS ---

    @Test
    void shouldSuccessfullyTransferBetweenAccounts() {
        mockAuthenticatedUser("sender@mail.com");

        Account sender = new Account("100", "Sender", new BigDecimal("200.00"), "sender@mail.com", "hash", Role.CLIENT, State.ACTIVE);
        Account receiver = new Account("200", "Receiver", new BigDecimal("50.00"), "receiver@mail.com", "hash", Role.CLIENT, State.ACTIVE);

        when(accountRepository.findByIdForUpdate("100")).thenReturn(Optional.of(sender));
        when(accountRepository.findByIdForUpdate("200")).thenReturn(Optional.of(receiver));

        accountService.transfer("100", new BigDecimal("100.00"), "200");

        assertEquals(0, new BigDecimal("100.00").compareTo(sender.getBalance()));
        assertEquals(0, new BigDecimal("150.00").compareTo(receiver.getBalance()));

        verify(accountRepository, times(1)).save(sender);
        verify(accountRepository, times(1)).save(receiver);
        verify(transactionRepository, times(2)).save(any());
    }

    @Test
    void shouldThrowExceptionWhenTransferringToSameAccount() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> accountService.transfer("100", new BigDecimal("50.00"), "100")
        );

        assertEquals("Cannot transfer to the same account", exception.getMessage());
    }

    @Test
    void shouldThrowForbiddenExceptionWhenUserDoesNotOwnSenderAccountInTransfer() {
        mockAuthenticatedUser("hacker@mail.com");

        Account sender = new Account("100", "Sender", new BigDecimal("200.00"), "sender@mail.com", "hash", Role.CLIENT, State.ACTIVE);
        Account receiver = new Account("200", "Receiver", new BigDecimal("50.00"), "receiver@mail.com", "hash", Role.CLIENT, State.ACTIVE);

        when(accountRepository.findByIdForUpdate("100")).thenReturn(Optional.of(sender));
        when(accountRepository.findByIdForUpdate("200")).thenReturn(Optional.of(receiver));

        ForbiddenException exception = assertThrows(
                ForbiddenException.class,
                () -> accountService.transfer("100", new BigDecimal("50.00"), "200")
        );

        assertEquals("You can only transfer from your own account", exception.getMessage());
    }

    @Test
    void shouldThrowIllegalArgumentExceptionWhenTransferringFromFrozenAccount() {
        mockAuthenticatedUser("sender@mail.com");

        Account sender = new Account("100", "Sender", new BigDecimal("200.00"), "sender@mail.com", "hash", Role.CLIENT, State.FROZEN);
        Account receiver = new Account("200", "Receiver", new BigDecimal("50.00"), "receiver@mail.com", "hash", Role.CLIENT, State.ACTIVE);

        when(accountRepository.findByIdForUpdate("100")).thenReturn(Optional.of(sender));
        when(accountRepository.findByIdForUpdate("200")).thenReturn(Optional.of(receiver));

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> accountService.transfer("100", new BigDecimal("50.00"), "200")
        );

        assertEquals("One of the accounts is frozen", exception.getMessage());
        verify(accountRepository, never()).save(any());
        verify(transactionRepository, never()).save(any());
    }

    @Test
    void shouldThrowIllegalArgumentExceptionWhenTransferringToFrozenAccount() {
        mockAuthenticatedUser("sender@mail.com");

        Account sender = new Account("100", "Sender", new BigDecimal("200.00"), "sender@mail.com", "hash", Role.CLIENT, State.ACTIVE);
        Account receiver = new Account("200", "Receiver", new BigDecimal("50.00"), "receiver@mail.com", "hash", Role.CLIENT, State.FROZEN);

        when(accountRepository.findByIdForUpdate("100")).thenReturn(Optional.of(sender));
        when(accountRepository.findByIdForUpdate("200")).thenReturn(Optional.of(receiver));

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> accountService.transfer("100", new BigDecimal("50.00"), "200")
        );

        assertEquals("One of the accounts is frozen", exception.getMessage());
        verify(accountRepository, never()).save(any());
        verify(transactionRepository, never()).save(any());
    }

    // --- ACCOUNT STATE (ADMIN FREEZE / ACTIVATE) TESTS ---

    @Test
    void shouldFreezeAccountWhenUserIsAdmin() {
        mockAuthenticatedAdmin("admin@mail.com");

        Account account = new Account("100", "Diogo Santiago", new BigDecimal("200.00"), "diogo@mail.com", "hash", Role.CLIENT, State.ACTIVE);
        when(accountRepository.findByIdForUpdate("100")).thenReturn(Optional.of(account));

        Account result = accountService.frozenAccount("100");

        assertEquals(State.FROZEN, result.getState());
        verify(accountRepository, times(1)).save(account);
    }

    @Test
    void shouldThrowForbiddenExceptionWhenNonAdminFreezesAccount() {
        mockAuthenticatedUser("diogo@mail.com");

        Account account = new Account("100", "Diogo Santiago", new BigDecimal("200.00"), "diogo@mail.com", "hash", Role.CLIENT, State.ACTIVE);
        when(accountRepository.findByIdForUpdate("100")).thenReturn(Optional.of(account));

        ForbiddenException exception = assertThrows(
                ForbiddenException.class,
                () -> accountService.frozenAccount("100")
        );

        assertEquals("Only admins can change the account state", exception.getMessage());
        verify(accountRepository, never()).save(any());
    }

    @Test
    void shouldThrowIllegalArgumentExceptionWhenFreezingAlreadyFrozenAccount() {
        mockAuthenticatedAdmin("admin@mail.com");

        Account account = new Account("100", "Diogo Santiago", new BigDecimal("200.00"), "diogo@mail.com", "hash", Role.CLIENT, State.FROZEN);
        when(accountRepository.findByIdForUpdate("100")).thenReturn(Optional.of(account));

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> accountService.frozenAccount("100")
        );

        assertEquals("Account with ID: 100 is already frozen", exception.getMessage());
        verify(accountRepository, never()).save(any());
    }

    @Test
    void shouldActivateAccountWhenUserIsAdmin() {
        mockAuthenticatedAdmin("admin@mail.com");

        Account account = new Account("100", "Diogo Santiago", new BigDecimal("200.00"), "diogo@mail.com", "hash", Role.CLIENT, State.FROZEN);
        when(accountRepository.findByIdForUpdate("100")).thenReturn(Optional.of(account));

        Account result = accountService.activeAccount("100");

        assertEquals(State.ACTIVE, result.getState());
        verify(accountRepository, times(1)).save(account);
    }

    @Test
    void shouldThrowForbiddenExceptionWhenNonAdminActivatesAccount() {
        mockAuthenticatedUser("diogo@mail.com");

        Account account = new Account("100", "Diogo Santiago", new BigDecimal("200.00"), "diogo@mail.com", "hash", Role.CLIENT, State.FROZEN);
        when(accountRepository.findByIdForUpdate("100")).thenReturn(Optional.of(account));

        ForbiddenException exception = assertThrows(
                ForbiddenException.class,
                () -> accountService.activeAccount("100")
        );

        assertEquals("Only admins can change the account state", exception.getMessage());
        verify(accountRepository, never()).save(any());
    }

    @Test
    void shouldThrowIllegalArgumentExceptionWhenActivatingAlreadyActiveAccount() {
        mockAuthenticatedAdmin("admin@mail.com");

        Account account = new Account("100", "Diogo Santiago", new BigDecimal("200.00"), "diogo@mail.com", "hash", Role.CLIENT, State.ACTIVE);
        when(accountRepository.findByIdForUpdate("100")).thenReturn(Optional.of(account));

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> accountService.activeAccount("100")
        );

        assertEquals("Account with ID: 100 is already active", exception.getMessage());
        verify(accountRepository, never()).save(any());
    }

    // --- ACCOUNT CREATION & LOOKUP TESTS ---

    @Test
    void shouldSuccessfullyCreateAccountWhenIdDoesNotExist() {
        String accountId = "200";
        String ownerName = "Maria Silva";
        String email = "maria@mail.com";
        String rawPassword = "password123";
        Role role = Role.CLIENT;
        State state = State.ACTIVE;

        when(accountRepository.existsById(accountId)).thenReturn(false);
        when(passwordEncoder.encode(rawPassword)).thenReturn("hashed_password123");

        Account newAccount = accountService.createAccount(accountId, ownerName, email, rawPassword, role, state);

        assertNotNull(newAccount);
        assertEquals(accountId, newAccount.getId());
        assertEquals(ownerName, newAccount.getOwnerName());
        assertEquals(email, newAccount.getEmail());
        assertEquals("hashed_password123", newAccount.getPassword());
        assertEquals(State.ACTIVE, newAccount.getState());
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
        State state = State.ACTIVE;

        when(accountRepository.existsById(accountId)).thenReturn(true);

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> accountService.createAccount(accountId, ownerName, email, rawPassword, role, state)
        );

        assertEquals("Account ID already exists", exception.getMessage());
        verify(accountRepository, never()).save(any());
        verify(passwordEncoder, never()).encode(anyString());
    }

    @Test
    void shouldReturnAccountWhenUserIsOwner() {
        mockAuthenticatedUser("diogo@mail.com");
        Account account = new Account("100", "Diogo Santiago", new BigDecimal("250.00"), "diogo@mail.com", "hash", Role.CLIENT, State.ACTIVE);
        when(accountRepository.findById("100")).thenReturn(Optional.of(account));

        Account result = accountService.getAccount("100");

        assertEquals("100", result.getId());
    }

    @Test
    void shouldReturnAccountWhenUserIsAdmin() {
        mockAuthenticatedAdmin("admin@mail.com");

        Account account = new Account("100", "Diogo Santiago", new BigDecimal("250.00"), "diogo@mail.com", "hash", Role.CLIENT, State.ACTIVE);
        when(accountRepository.findById("100")).thenReturn(Optional.of(account));

        Account result = accountService.getAccount("100");

        assertEquals("100", result.getId());
    }

    @Test
    void shouldThrowForbiddenExceptionWhenNonOwnerClientReadsAccount() {
        mockAuthenticatedUser("hacker@mail.com");
        Account account = new Account("100", "Diogo Santiago", new BigDecimal("250.00"), "diogo@mail.com", "hash", Role.CLIENT, State.ACTIVE);
        when(accountRepository.findById("100")).thenReturn(Optional.of(account));

        assertThrows(ForbiddenException.class, () -> accountService.getAccount("100"));
    }
}