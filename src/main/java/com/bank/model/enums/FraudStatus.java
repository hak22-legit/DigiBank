package com.bank.model.enums;

public enum FraudStatus {
    OPEN,
    PENDING,
    INVESTIGATING,
    UNDER_INVESTIGATION,
    RESOLVED,
    CONFIRMED_FRAUD;

    public boolean isPending() {
        return this == OPEN || this == PENDING;
    }

    public boolean isUnderInvestigation() {
        return this == INVESTIGATING || this == UNDER_INVESTIGATION;
    }

    public boolean isResolved() {
        return this == RESOLVED || this == CONFIRMED_FRAUD;
    }
}