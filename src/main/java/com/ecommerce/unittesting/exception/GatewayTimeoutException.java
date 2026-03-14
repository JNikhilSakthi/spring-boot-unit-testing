package com.ecommerce.unittesting.exception;

public class GatewayTimeoutException extends RuntimeException {
    public GatewayTimeoutException(String message) {
        super(message);
    }
}
