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

    // Handles: ResourceInUseException → 409 Conflict
    // Triggered when: deleting a user who has associated products (FK constraint)
    @ExceptionHandler(ResourceInUseException.class)
    public ResponseEntity<Map<String, Object>> handleResourceInUse(ResourceInUseException ex) {
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

    // ==================== Payment Gateway Exception Handlers ====================

    // Handles: InvalidCardException → 400 Bad Request
    // Triggered when: card number fails Luhn validation or format check
    @ExceptionHandler(InvalidCardException.class)
    public ResponseEntity<Map<String, Object>> handleInvalidCard(InvalidCardException ex) {
        return buildErrorResponse(HttpStatus.BAD_REQUEST, "Invalid Card", ex.getMessage());
    }

    // Handles: CardExpiredException → 400 Bad Request
    // Triggered when: card expiry date is in the past or too far in future
    @ExceptionHandler(CardExpiredException.class)
    public ResponseEntity<Map<String, Object>> handleCardExpired(CardExpiredException ex) {
        return buildErrorResponse(HttpStatus.BAD_REQUEST, "Card Expired", ex.getMessage());
    }

    // Handles: InvalidCVVException → 400 Bad Request
    // Triggered when: CVV format is invalid (not 3 digits, or not 4 digits for AMEX)
    @ExceptionHandler(InvalidCVVException.class)
    public ResponseEntity<Map<String, Object>> handleInvalidCVV(InvalidCVVException ex) {
        return buildErrorResponse(HttpStatus.BAD_REQUEST, "Invalid CVV", ex.getMessage());
    }

    // Handles: UnsupportedCurrencyException → 400 Bad Request
    // Triggered when: currency code is not in the supported list
    @ExceptionHandler(UnsupportedCurrencyException.class)
    public ResponseEntity<Map<String, Object>> handleUnsupportedCurrency(UnsupportedCurrencyException ex) {
        return buildErrorResponse(HttpStatus.BAD_REQUEST, "Unsupported Currency", ex.getMessage());
    }

    // Handles: AmountLimitExceededException → 400 Bad Request
    // Triggered when: amount is negative, zero, exceeds max limit, or has bad precision
    @ExceptionHandler(AmountLimitExceededException.class)
    public ResponseEntity<Map<String, Object>> handleAmountLimitExceeded(AmountLimitExceededException ex) {
        return buildErrorResponse(HttpStatus.BAD_REQUEST, "Amount Limit Exceeded", ex.getMessage());
    }

    // Handles: XmlProcessingException → 400 Bad Request
    // Triggered when: JAXB fails to marshal or unmarshal XML
    @ExceptionHandler(XmlProcessingException.class)
    public ResponseEntity<Map<String, Object>> handleXmlProcessing(XmlProcessingException ex) {
        return buildErrorResponse(HttpStatus.BAD_REQUEST, "XML Processing Error", ex.getMessage());
    }

    // Handles: FraudDetectedException → 403 Forbidden
    // Triggered when: fraud risk score exceeds threshold (> 80)
    @ExceptionHandler(FraudDetectedException.class)
    public ResponseEntity<Map<String, Object>> handleFraudDetected(FraudDetectedException ex) {
        return buildErrorResponse(HttpStatus.FORBIDDEN, "Fraud Detected", ex.getMessage());
    }

    // Handles: FraudServiceUnavailableException → 503 Service Unavailable
    // Triggered when: fraud detection service is down or returns an error
    @ExceptionHandler(FraudServiceUnavailableException.class)
    public ResponseEntity<Map<String, Object>> handleFraudServiceUnavailable(FraudServiceUnavailableException ex) {
        return buildErrorResponse(HttpStatus.SERVICE_UNAVAILABLE, "Fraud Service Unavailable", ex.getMessage());
    }

    // Handles: PaymentDeclinedException → 422 Unprocessable Entity
    // Triggered when: bank declines the payment for any reason
    @ExceptionHandler(PaymentDeclinedException.class)
    public ResponseEntity<Map<String, Object>> handlePaymentDeclined(PaymentDeclinedException ex) {
        return buildErrorResponse(HttpStatus.UNPROCESSABLE_ENTITY, "Payment Declined", ex.getMessage());
    }

    // Handles: InsufficientFundsException → 422 Unprocessable Entity
    // Triggered when: bank reports insufficient funds
    @ExceptionHandler(InsufficientFundsException.class)
    public ResponseEntity<Map<String, Object>> handleInsufficientFunds(InsufficientFundsException ex) {
        return buildErrorResponse(HttpStatus.UNPROCESSABLE_ENTITY, "Insufficient Funds", ex.getMessage());
    }

    // Handles: GatewayTimeoutException → 504 Gateway Timeout
    // Triggered when: bank authorization service times out or is unreachable
    @ExceptionHandler(GatewayTimeoutException.class)
    public ResponseEntity<Map<String, Object>> handleGatewayTimeout(GatewayTimeoutException ex) {
        return buildErrorResponse(HttpStatus.GATEWAY_TIMEOUT, "Gateway Timeout", ex.getMessage());
    }

    // Helper method to build consistent error responses
    private ResponseEntity<Map<String, Object>> buildErrorResponse(
            HttpStatus status, String error, String message) {
        Map<String, Object> errorBody = new HashMap<>();
        errorBody.put("timestamp", LocalDateTime.now().toString());
        errorBody.put("status", status.value());
        errorBody.put("error", error);
        errorBody.put("message", message);
        return ResponseEntity.status(status).body(errorBody);
    }
}
