package com.bank.exception;

public class LoanStateException extends RuntimeException {
    public LoanStateException(String message) {
        super(message);
    }
}