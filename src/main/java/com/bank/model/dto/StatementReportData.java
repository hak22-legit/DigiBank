package com.bank.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StatementReportData {
    private String customerName;
    private String customerId;
    @Builder.Default
    private String assignedBranch = "Head Office";
    private String accountNumber;
    private String accountStructure;
    @Builder.Default
    private String currency = "USD";
    @Builder.Default
    private String currencySymbol = "$";
    @Builder.Default
    private String standingStatus = "ACTIVE / Cleared";
    private LocalDate statementDate;
    private LocalDate startDate;
    private LocalDate endDate;
    private BigDecimal openingBalance;
    private BigDecimal closingBalance;
    private BigDecimal totalCredits;
    private int creditCount;
    private BigDecimal totalDebits;
    private int debitCount;
    @Builder.Default
    private List<StatementTransactionItem> transactions = new ArrayList<>();
    private String sha256Hash;
}
