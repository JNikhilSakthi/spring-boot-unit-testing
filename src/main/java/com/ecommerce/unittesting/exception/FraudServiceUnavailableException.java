package com.ecommerce.unittesting.exception;

public class FraudServiceUnavailableException extends RuntimeException {
    public FraudServiceUnavailableException(String message) {
        super(message);
    }
}
