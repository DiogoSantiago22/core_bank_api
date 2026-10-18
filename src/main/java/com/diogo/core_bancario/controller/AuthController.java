package com.diogo.core_bancario.controller;

import com.diogo.core_bancario.dto.AccountResponseDTO;
import com.diogo.core_bancario.dto.LoginDTO;
import com.diogo.core_bancario.dto.TokenResponseDTO;
import com.diogo.core_bancario.service.AccountService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/accounts")
public class AuthController {
    private final AccountService accountService;

    public AuthController(AccountService accountService) {
        this.accountService = accountService;
    }

    @PostMapping("/login")
    public TokenResponseDTO login(@Valid @RequestBody LoginDTO loginDTO){
       return new TokenResponseDTO(accountService.login(loginDTO));
    }

}
