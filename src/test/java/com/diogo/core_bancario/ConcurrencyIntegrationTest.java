package com.diogo.core_bancario;

import com.diogo.core_bancario.model.Account;
import com.diogo.core_bancario.model.Role;
import com.diogo.core_bancario.repository.AccountRepository;
import com.diogo.core_bancario.service.AccountService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
@Testcontainers
class ConcurrencyIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @Autowired
    private AccountService accountService;

    @Autowired
    private AccountRepository accountRepository;

    @BeforeEach
    void setUp() {
        accountRepository.deleteAll();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("userA@mail.com", null)
        );
    }

    @Test
    void testConcurrentTransfersMaintainDataIntegrity() throws InterruptedException {
        accountRepository.save(new Account("A", "User A", new BigDecimal("1000.00"), "userA@mail.com", "pass", Role.CLIENT));
        accountRepository.save(new Account("B", "User B", new BigDecimal("1000.00"), "userB@mail.com", "pass", Role.CLIENT));

        int numberOfThreads = 100;
        ExecutorService executor = Executors.newFixedThreadPool(10);
        CountDownLatch latch = new CountDownLatch(numberOfThreads);

        for (int i = 0; i < numberOfThreads; i++) {
            executor.submit(() -> {
                // 1. Injetar a segurança manualmente dentro de CADA nova thread
                SecurityContextHolder.getContext().setAuthentication(
                        new UsernamePasswordAuthenticationToken("userA@mail.com", null)
                );
                try {
                    accountService.transfer("A", new BigDecimal("5.00"), "B");
                } catch (Exception e) {
                    e.printStackTrace(); // Mostra na consola se algo falhar em vez de ignorar
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        executor.shutdown();

        Account accountA = accountRepository.findById("A").get();
        assertEquals(0, new BigDecimal("500.00").compareTo(accountA.getBalance()));

        Account accountB = accountRepository.findById("B").get();
        assertEquals(0, new BigDecimal("1500.00").compareTo(accountB.getBalance()));
    }
}