package com.bank.model.enums;

public enum AdminRole {
    SUPER_ADMIN,
    LOAN_OFFICER,
    COMPLIANCE_OFFICER;

    public UserRole toUserRole() {
        return this == SUPER_ADMIN ? UserRole.ADMIN : UserRole.STAFF;
    }
}