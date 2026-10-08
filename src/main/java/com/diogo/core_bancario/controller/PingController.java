package com.diogo.core_bancario.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class PingController {

    @GetMapping("/ping")
    public String healthCheck() {
        return "System health: Core Banking API is up and running!";
    }
}