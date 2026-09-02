package com.bank.exception;

public class FraudAlertStateException extends RuntimeException {
    public FraudAlertStateException(String message) {
        super(message);
    }
}