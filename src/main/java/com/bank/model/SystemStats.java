package com.bank.model;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class SystemStats {
    private long totalUsers;
    private long totalAdmins;
    private long totalAccounts;
    private long totalTransactions;
    private long totalFraudAlerts;
    private long openFraudAlerts;
}