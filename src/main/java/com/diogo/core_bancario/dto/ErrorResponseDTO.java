package com.diogo.core_bancario.dto;

import java.time.LocalDateTime;

// A professional error structure
public record ErrorResponseDTO(
        LocalDateTime timestamp,
        int status,
        String error,
        String message,
        String path
) {}