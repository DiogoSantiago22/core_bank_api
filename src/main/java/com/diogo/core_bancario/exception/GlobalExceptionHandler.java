package com.diogo.core_bancario.exception;

import com.diogo.core_bancario.dto.ErrorResponseDTO;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;

@RestControllerAdvice // Tells Spring this class intercepts all controllers
public class GlobalExceptionHandler {

    // Handles rules (f.e.: "Account not found", "Insufficient funds")
    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST) // Returns 400 instead of 500
    public ErrorResponseDTO handleIllegalArgumentException(IllegalArgumentException ex, HttpServletRequest request) {

        return new ErrorResponseDTO(
                LocalDateTime.now(),
                HttpStatus.BAD_REQUEST.value(),
                "Bad Request",
                ex.getMessage(), // This prints custom message
                request.getRequestURI()
        );
    }

    // Handles DTO @Valid failures (f.e.: amount = -50)
    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponseDTO handleValidationExceptions(MethodArgumentNotValidException ex, HttpServletRequest request) {

        // Grabs the custom message
        String errorMessage = ex.getBindingResult().getFieldErrors().getFirst().getDefaultMessage();

        return new ErrorResponseDTO(
                LocalDateTime.now(),
                HttpStatus.BAD_REQUEST.value(),
                "Validation Failed",
                errorMessage,
                request.getRequestURI()
        );
    }
}