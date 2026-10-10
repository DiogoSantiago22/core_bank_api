package com.diogo.core_bancario.dto;

import jakarta.validation.constraints.NotBlank;

// 'record' automatically generates constructors and getters

public record AccountCreateDTO(

        // Validate the data before the code runs
        @NotBlank(message = "ID of the account its mandatory")
        String id,

        @NotBlank(message = "Name of the owner its mandatory")
        String ownerName
) {}
