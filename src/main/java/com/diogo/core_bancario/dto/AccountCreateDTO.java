package com.diogo.core_bancario.dto;

import com.diogo.core_bancario.model.Role;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

// 'record' automatically generates constructors and getters

public record AccountCreateDTO(

        // Validate the data before the code runs
        @NotBlank(message = "ID of the account its mandatory")
        String id,

        @NotBlank(message = "Name of the owner its mandatory")
        String ownerName,

        @NotBlank(message = "Email of the owner its mandatory")
        String email,

        @NotBlank(message = "Password of the owner its mandatory")
        String password,

        @NotNull(message = "Role of the owner its mandatory")
        Role role

) {}
