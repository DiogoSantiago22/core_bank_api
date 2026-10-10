package com.diogo.core_bancario.repository;

import com.diogo.core_bancario.model.Account;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AccountRepository extends JpaRepository<Account, String> {
}