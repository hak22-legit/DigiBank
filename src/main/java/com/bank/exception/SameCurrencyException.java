package com.bank.exception;

/**
 * Exception thrown when a user attempts to exchange currency between two accounts
 * that already share the same currency.
 */
public class SameCurrencyException extends RuntimeException {
    public SameCurrencyException(String message) {
        super(message);
    }
}
