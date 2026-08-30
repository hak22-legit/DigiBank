package com.bank.exception;

public class SavingGoalNotFoundException extends RuntimeException {
    public SavingGoalNotFoundException(String message) {
        super(message);
    }
}