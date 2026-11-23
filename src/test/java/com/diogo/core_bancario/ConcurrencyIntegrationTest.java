package com.diogo.core_bancario;

import com.diogo.core_bancario.model.Account;
import com.diogo.core_bancario.model.Role;
import com.diogo.core_bancario.model.State;
import com.diogo.core_bancario.repository.AccountRepository;
import com.diogo.core_bancario.repository.TransactionRepository;
import com.diogo.core_bancario.service.AccountService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@Testcontainers
@TestPropertySource(properties = {
        "jwt.secret=chave_secreta_para_testes_de_integracao_com_mais_de_32_caracteres"
})
class ConcurrencyIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @Autowired
    private AccountService accountService;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private TransactionRepository transactionRepository;

    @BeforeEach
    void setUp() {
        transactionRepository.deleteAll();
        accountRepository.deleteAll();
    }

    @Test
    void testConcurrentTransfersMaintainDataIntegrity() throws InterruptedException {
        accountRepository.save(new Account("A", "User A", new BigDecimal("1000.00"), "userA@mail.com", "pass", Role.CLIENT, State.ACTIVE));
        accountRepository.save(new Account("B", "User B", new BigDecimal("1000.00"), "userB@mail.com", "pass", Role.CLIENT, State.ACTIVE));

        int numberOfThreads = 100;
        ExecutorService executor = Executors.newFixedThreadPool(10);
        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(numberOfThreads);
        List<Throwable> errors = Collections.synchronizedList(new ArrayList<>());

        for (int i = 0; i < numberOfThreads; i++) {
            executor.submit(() -> {
                SecurityContextHolder.getContext().setAuthentication(
                        new UsernamePasswordAuthenticationToken("userA@mail.com", null, List.of())
                );
                try {
                    startGate.await();
                    accountService.transfer("A", new BigDecimal("5.00"), "B");
                } catch (Throwable t) {
                    errors.add(t);
                } finally {
                    SecurityContextHolder.clearContext();
                    doneLatch.countDown();
                }
            });
        }

        startGate.countDown();
        doneLatch.await();
        executor.shutdown();

        assertTrue(errors.isEmpty(), "Unexpected errors during concurrent transfers: " + errors);

        Account accountA = accountRepository.findById("A").orElseThrow();
        assertEquals(0, new BigDecimal("500.00").compareTo(accountA.getBalance()));

        Account accountB = accountRepository.findById("B").orElseThrow();
        assertEquals(0, new BigDecimal("1500.00").compareTo(accountB.getBalance()));
    }

    @Test
    void testBidirectionalConcurrentTransfersPreventDeadlocks() throws InterruptedException {
        accountRepository.save(new Account("A", "User A", new BigDecimal("1000.00"), "userA@mail.com", "pass", Role.CLIENT, State.ACTIVE));
        accountRepository.save(new Account("B", "User B", new BigDecimal("1000.00"), "userB@mail.com", "pass", Role.CLIENT, State.ACTIVE));

        int transfersPerDirection = 50;
        int totalTasks = transfersPerDirection * 2;
        ExecutorService executor = Executors.newFixedThreadPool(16);
        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(totalTasks);
        List<Throwable> errors = Collections.synchronizedList(new ArrayList<>());

        for (int i = 0; i < transfersPerDirection; i++) {
            // Thread A -> B
            executor.submit(() -> {
                SecurityContextHolder.getContext().setAuthentication(
                        new UsernamePasswordAuthenticationToken("userA@mail.com", null, List.of())
                );
                try {
                    startGate.await();
                    accountService.transfer("A", new BigDecimal("5.00"), "B");
                } catch (Throwable t) {
                    errors.add(t);
                } finally {
                    SecurityContextHolder.clearContext();
                    doneLatch.countDown();
                }
            });

            // Thread B -> A
            executor.submit(() -> {
                SecurityContextHolder.getContext().setAuthentication(
                        new UsernamePasswordAuthenticationToken("userB@mail.com", null, List.of())
                );
                try {
                    startGate.await();
                    accountService.transfer("B", new BigDecimal("5.00"), "A");
                } catch (Throwable t) {
                    errors.add(t);
                } finally {
                    SecurityContextHolder.clearContext();
                    doneLatch.countDown();
                }
            });
        }

        startGate.countDown();
        doneLatch.await();
        executor.shutdown();

        assertTrue(errors.isEmpty(), "Deadlock or concurrency failure detected: " + errors);

        Account accountA = accountRepository.findById("A").orElseThrow();
        assertEquals(0, new BigDecimal("1000.00").compareTo(accountA.getBalance()));

        Account accountB = accountRepository.findById("B").orElseThrow();
        assertEquals(0, new BigDecimal("1000.00").compareTo(accountB.getBalance()));
    }
}