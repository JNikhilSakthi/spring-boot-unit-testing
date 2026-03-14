package com.ecommerce.unittesting.exception;

public class AmountLimitExceededException extends RuntimeException {
    public AmountLimitExceededException(String message) {
        super(message);
    }
}
