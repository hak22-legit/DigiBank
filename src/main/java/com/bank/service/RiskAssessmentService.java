package com.bank.service;

import com.bank.model.enums.RiskLevel;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Simulated ML-style risk scoring (educational simulation, not real XGBoost -
 * see original master prompt Phase 22 notes). Deliberately kept separate from
 * LoanService so a real ML model could be swapped in later without touching
 * loan application logic.
 *
 * Continuous weighted formula (0-100, higher = riskier):
 *   Credit Score   - 40 pts max
 *   DTI            - 30 pts max
 *   LTI            - 20 pts max
 *   Term Length    - 10 pts max
 */
public class RiskAssessmentService {

    private static final BigDecimal CREDIT_WEIGHT = new BigDecimal("40");
    private static final BigDecimal DTI_WEIGHT = new BigDecimal("30");
    private static final BigDecimal LTI_WEIGHT = new BigDecimal("20");
    private static final BigDecimal TERM_WEIGHT = new BigDecimal("10");

    private static final BigDecimal CREDIT_MIN = new BigDecimal("300");
    private static final BigDecimal CREDIT_MAX = new BigDecimal("850");

    private static final BigDecimal DTI_CAP_PERCENT = new BigDecimal("80");   // 80%+ DTI = max risk
    private static final BigDecimal LTI_CAP_RATIO = new BigDecimal("5");     // 5x annual income = max risk
    private static final BigDecimal TERM_CAP_MONTHS = new BigDecimal("60"); // 60 months = max risk

    private static final BigDecimal RISK_LOW_MAX = new BigDecimal("30");
    private static final BigDecimal RISK_MEDIUM_MAX = new BigDecimal("70");

    public RiskAssessmentResult assess(BigDecimal requestedAmount,
                                       BigDecimal monthlyIncome,
                                       BigDecimal monthlyExpense,
                                       BigDecimal existingDebt,
                                       Integer creditScore,
                                       Integer termMonths) {

        BigDecimal creditRisk = calculateCreditRisk(creditScore);
        BigDecimal dtiRisk = calculateDTIRisk(monthlyIncome, monthlyExpense, existingDebt);
        BigDecimal ltiRisk = calculateLTIRisk(requestedAmount, monthlyIncome);
        BigDecimal termRisk = calculateTermRisk(termMonths);

        BigDecimal totalRisk = creditRisk.add(dtiRisk).add(ltiRisk).add(termRisk);
        totalRisk = clamp(totalRisk, BigDecimal.ZERO, new BigDecimal("100"))
                .setScale(2, RoundingMode.HALF_UP);

        RiskLevel riskLevel = determineRiskLevel(totalRisk);
        String prediction = riskLevel == RiskLevel.HIGH ? "REJECTED" : "APPROVED";

        return new RiskAssessmentResult(totalRisk, riskLevel, prediction);
    }

    /** Linear: creditScore=300 -> full 40pts risk; creditScore=850 -> 0pts risk. */
    private BigDecimal calculateCreditRisk(Integer creditScore) {
        BigDecimal score = BigDecimal.valueOf(creditScore);
        BigDecimal range = CREDIT_MAX.subtract(CREDIT_MIN);
        BigDecimal position = clamp(score, CREDIT_MIN, CREDIT_MAX).subtract(CREDIT_MIN);
        BigDecimal normalizedGood = position.divide(range, 10, RoundingMode.HALF_UP); // 0 (bad) -> 1 (good)
        BigDecimal normalizedRisk = BigDecimal.ONE.subtract(normalizedGood); // 1 (bad) -> 0 (good)
        return normalizedRisk.multiply(CREDIT_WEIGHT).setScale(4, RoundingMode.HALF_UP);
    }

    /** Linear: DTI 0% -> 0pts; DTI >= 80% -> full 30pts. */
    private BigDecimal calculateDTIRisk(BigDecimal monthlyIncome, BigDecimal monthlyExpense,
                                        BigDecimal existingDebt) {
        if (monthlyIncome.compareTo(BigDecimal.ZERO) == 0) return DTI_WEIGHT;

        BigDecimal monthlyExistingDebt = existingDebt.divide(new BigDecimal("12"), 4, RoundingMode.HALF_UP);
        BigDecimal totalMonthlyObligations = monthlyExpense.add(monthlyExistingDebt);
        BigDecimal dtiPercent = totalMonthlyObligations
                .divide(monthlyIncome, 6, RoundingMode.HALF_UP)
                .multiply(new BigDecimal("100"));

        BigDecimal normalized = clamp(dtiPercent, BigDecimal.ZERO, DTI_CAP_PERCENT)
                .divide(DTI_CAP_PERCENT, 10, RoundingMode.HALF_UP);
        return normalized.multiply(DTI_WEIGHT).setScale(4, RoundingMode.HALF_UP);
    }

    /** Linear: LTI 0x -> 0pts; LTI >= 5x annual income -> full 20pts. */
    private BigDecimal calculateLTIRisk(BigDecimal requestedAmount, BigDecimal monthlyIncome) {
        BigDecimal annualIncome = monthlyIncome.multiply(new BigDecimal("12"));
        if (annualIncome.compareTo(BigDecimal.ZERO) == 0) return LTI_WEIGHT;

        BigDecimal lti = requestedAmount.divide(annualIncome, 6, RoundingMode.HALF_UP);
        BigDecimal normalized = clamp(lti, BigDecimal.ZERO, LTI_CAP_RATIO)
                .divide(LTI_CAP_RATIO, 10, RoundingMode.HALF_UP);
        return normalized.multiply(LTI_WEIGHT).setScale(4, RoundingMode.HALF_UP);
    }

    /** Linear: term 0mo -> 0pts; term >= 60mo (5yr) -> full 10pts. Longer term = more default risk. */
    private BigDecimal calculateTermRisk(Integer termMonths) {
        BigDecimal term = BigDecimal.valueOf(termMonths);
        BigDecimal normalized = clamp(term, BigDecimal.ZERO, TERM_CAP_MONTHS)
                .divide(TERM_CAP_MONTHS, 10, RoundingMode.HALF_UP);
        return normalized.multiply(TERM_WEIGHT).setScale(4, RoundingMode.HALF_UP);
    }

    private RiskLevel determineRiskLevel(BigDecimal riskScore) {
        if (riskScore.compareTo(RISK_LOW_MAX) <= 0) return RiskLevel.LOW;
        else if (riskScore.compareTo(RISK_MEDIUM_MAX) <= 0) return RiskLevel.MEDIUM;
        else return RiskLevel.HIGH;
    }

    private BigDecimal clamp(BigDecimal value, BigDecimal min, BigDecimal max) {
        if (value.compareTo(min) < 0) return min;
        if (value.compareTo(max) > 0) return max;
        return value;
    }
}