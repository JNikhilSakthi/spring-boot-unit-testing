package com.ecommerce.unittesting.exception;

// Custom exception for duplicate resource scenarios (e.g., email already exists)
// Caught by GlobalExceptionHandler → returns 409 Conflict response
public class DuplicateResourceException extends RuntimeException {

    public DuplicateResourceException(String message) {
        super(message);
    }
}
