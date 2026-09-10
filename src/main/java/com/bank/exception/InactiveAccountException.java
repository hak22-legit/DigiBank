package com.bank.exception;

public class InactiveAccountException extends AuthenticationException {
    public InactiveAccountException(String message) {
        super(message);
    }
}
