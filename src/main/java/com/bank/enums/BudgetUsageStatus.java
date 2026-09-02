package com.bank.enums;

public enum BudgetUsageStatus {
    OK,        // < 80% used
    WARNING,   // >= 80% and <= 100% used
    EXCEEDED   // > 100% used
}