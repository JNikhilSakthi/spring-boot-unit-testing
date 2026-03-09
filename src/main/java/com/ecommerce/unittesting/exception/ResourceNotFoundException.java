package com.ecommerce.unittesting.exception;

// Custom exception for "not found" scenarios (Product/User not found by ID)
// Extends RuntimeException → unchecked exception (no need for try/catch everywhere)
// Caught by GlobalExceptionHandler → returns 404 Not Found response
public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String message) {
        super(message);
    }
}
