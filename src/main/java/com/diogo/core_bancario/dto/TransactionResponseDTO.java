package com.diogo.core_bancario.dto;

import com.diogo.core_bancario.model.TransactionType;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record TransactionResponseDTO(Long id,
                                     TransactionType type,
                                     BigDecimal amount,
                                     LocalDateTime timestamp) {
}
