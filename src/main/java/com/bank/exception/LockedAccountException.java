package com.bank.exception;

public class LockedAccountException extends AuthenticationException {
    public LockedAccountException(String message) {
        super(message);
    }
}
