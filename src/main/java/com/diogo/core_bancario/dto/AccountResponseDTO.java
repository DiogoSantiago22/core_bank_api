package com.diogo.core_bancario.dto;

import com.diogo.core_bancario.model.State;

import java.math.BigDecimal;

public record AccountResponseDTO(
        String id,
        String ownerName,
        BigDecimal balance,
        State state
) {}