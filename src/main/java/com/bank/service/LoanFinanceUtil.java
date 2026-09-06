package com.bank.service;

import com.bank.model.entity.Loan;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Flat-rate interest calculation, shared between LoanApprovalService
 * (to set the initial total obligation) and LoanRepaymentService
 * (to split each payment into principal/interest).
 *
 * Formula: Total Interest = Principal x Rate% x (Term/12)
 *          Total Obligation = Principal + Total Interest
 */
public class LoanFinanceUtil {

    private LoanFinanceUtil() {}

    public static BigDecimal calculateTotalObligation(Loan loan) {
        BigDecimal principal = loan.getApprovedAmount();
        BigDecimal annualRatePercent = loan.getInterestRate();
        BigDecimal termYears = BigDecimal.valueOf(loan.getTermMonths())
                .divide(BigDecimal.valueOf(12), 10, RoundingMode.HALF_UP);

        BigDecimal totalInterest = principal
                .multiply(annualRatePercent)
                .divide(BigDecimal.valueOf(100), 10, RoundingMode.HALF_UP)
                .multiply(termYears);

        return principal.add(totalInterest).setScale(4, RoundingMode.HALF_UP);
    }
}