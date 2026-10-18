package com.diogo.core_bancario.dto;

import jakarta.validation.constraints.NotBlank;

public record LoginDTO(

        @NotBlank(message = "Email of the owner its mandatory")
        String email,

        @NotBlank(message = "Password of the owner its mandatory")
        String password
) {
}
