package com.bank.exception;

public class InvalidCreditScoreException extends RuntimeException {
    public InvalidCreditScoreException(String message) {
        super(message);
    }
}