package com.bank.service;

import com.bank.model.enums.RiskLevel;
import java.math.BigDecimal;

/**
 * Result of a risk assessment - not persisted directly; riskScore/riskLevel
 * are stored on the Loan entity as before, prediction is informational only.
 */
public class RiskAssessmentResult {
    private final BigDecimal riskScore;
    private final RiskLevel riskLevel;
    private final String prediction; // "APPROVED" or "REJECTED" (recommendation only)

    public RiskAssessmentResult(BigDecimal riskScore, RiskLevel riskLevel, String prediction) {
        this.riskScore = riskScore;
        this.riskLevel = riskLevel;
        this.prediction = prediction;
    }

    public BigDecimal getRiskScore() { return riskScore; }
    public RiskLevel getRiskLevel() { return riskLevel; }
    public String getPrediction() { return prediction; }
}