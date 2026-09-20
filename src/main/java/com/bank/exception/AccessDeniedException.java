package com.bank.exception;

public class AccessDeniedException extends UnauthorizedException {
    public AccessDeniedException(String message) {
        super(message);
    }
}
