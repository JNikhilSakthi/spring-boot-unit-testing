package com.ecommerce.unittesting.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

// @RestControllerAdvice → Global exception handler for ALL controllers
// Catches exceptions thrown anywhere in Controller/Service layers
// and converts them to proper HTTP error responses
//
// Without this: exceptions would return 500 Internal Server Error with ugly stack trace
// With this:    exceptions return clean JSON responses with proper status codes
//
// How it works:
//   Service throws ResourceNotFoundException
//   → Spring catches it
//   → Looks for @ExceptionHandler(ResourceNotFoundException.class) in this class
//   → Calls that method → returns 404 with clean JSON error body
@RestControllerAdvice
public class GlobalExceptionHandler {

    // Handles: ResourceNotFoundException → 404 Not Found
    // Triggered when: productRepository.findById() or userRepository.findById() returns empty
    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleResourceNotFound(ResourceNotFoundException ex) {
        Map<String, Object> error = new HashMap<>();
        error.put("timestamp", LocalDateTime.now().toString());
        error.put("status", HttpStatus.NOT_FOUND.value());
        error.put("error", "Not Found");
        error.put("message", ex.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
    }

    // Handles: DuplicateResourceException → 409 Conflict
    // Triggered when: creating a user with an email that already exists
    @ExceptionHandler(DuplicateResourceException.class)
    public ResponseEntity<Map<String, Object>> handleDuplicateResource(DuplicateResourceException ex) {
        Map<String, Object> error = new HashMap<>();
        error.put("timestamp", LocalDateTime.now().toString());
        error.put("status", HttpStatus.CONFLICT.value());
        error.put("error", "Conflict");
        error.put("message", ex.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(error);
    }

    // Handles: MethodArgumentNotValidException → 400 Bad Request
    // Triggered when: @Valid fails on request body (blank name, invalid email, etc.)
    // Collects all field errors and returns them as a map:
    //   { "errors": { "name": "Product name is required", "price": "Price must be greater than 0" } }
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException ex) {
        Map<String, String> fieldErrors = new HashMap<>();
        ex.getBindingResult().getFieldErrors()
                .forEach(err -> fieldErrors.put(err.getField(), err.getDefaultMessage()));

        Map<String, Object> error = new HashMap<>();
        error.put("timestamp", LocalDateTime.now().toString());
        error.put("status", HttpStatus.BAD_REQUEST.value());
        error.put("error", "Validation Failed");
        error.put("errors", fieldErrors);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }
}
