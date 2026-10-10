package com.diogo.core_bancario.dto;

import java.math.BigDecimal;

public record AccountResponseDTO(
        String id,
        String ownerName,
        BigDecimal balance
) {}