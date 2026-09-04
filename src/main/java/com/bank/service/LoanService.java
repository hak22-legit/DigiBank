package com.bank.service;

import com.bank.model.enums.LoanStatus;
import com.bank.model.enums.RiskLevel;
import com.bank.exception.InvalidAmountException;
import com.bank.exception.InvalidCreditScoreException;
import com.bank.exception.LoanNotFoundException;
import com.bank.exception.UnauthorizedException;
import com.bank.model.entity.Loan;
import com.bank.model.entity.User;
import com.bank.model.repository.LoanRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

public class LoanService {
    private static final Logger logger = LoggerFactory.getLogger(LoanService.class);

    private static final BigDecimal DTI_EXCELLENT_THRESHOLD = new BigDecimal("36");
    private static final BigDecimal DTI_HIGH_THRESHOLD = new BigDecimal("50");

    private static final int CREDIT_SCORE_EXCELLENT = 740;
    private static final int CREDIT_SCORE_GOOD = 670;
    private static final int CREDIT_SCORE_FAIR = 580;

    private static final BigDecimal LTI_LOW_THRESHOLD = new BigDecimal("2");
    private static final BigDecimal LTI_HIGH_THRESHOLD = new BigDecimal("4");

    private static final BigDecimal RISK_LOW_MAX = new BigDecimal("30");
    private static final BigDecimal RISK_MEDIUM_MAX = new BigDecimal("70");

    private final LoanRepository loanRepository;

    public LoanService(LoanRepository loanRepository) {
        this.loanRepository = loanRepository;
    }

    /**
     * Submit a loan application with deterministic risk assessment.
     * Takes the authenticated applicant directly (not a raw userId) so the
     * loan can never be created under someone else's identity.
     */
    public Loan applyForLoan(User applicant, BigDecimal requestedAmount,
                             BigDecimal monthlyIncome, BigDecimal monthlyExpense,
                             BigDecimal existingDebt, Integer creditScore,
                             Integer termMonths) {

        validateLoanApplication(requestedAmount, monthlyIncome, creditScore, termMonths);

        BigDecimal riskScore = calculateDeterministicRiskScore(
                requestedAmount, monthlyIncome, monthlyExpense, existingDebt, creditScore);
        RiskLevel riskLevel = determineRiskLevel(riskScore);

        Loan loan = Loan.builder()
                .userId(applicant.getUserId())
                .requestedAmount(requestedAmount)
                .monthlyIncome(monthlyIncome)
                .monthlyExpense(monthlyExpense)
                .existingDebt(existingDebt != null ? existingDebt : BigDecimal.ZERO)
                .creditScore(creditScore)
                .termMonths(termMonths)
                .riskScore(riskScore)
                .riskLevel(riskLevel)
                .status(LoanStatus.PENDING)
                .outstandingBalance(BigDecimal.ZERO)
                .build();

        Loan savedLoan = loanRepository.save(loan);
        logger.info("Loan application created: ID={}, Risk={}, Status={}",
                savedLoan.getLoanId(), riskLevel, savedLoan.getStatus());
        return savedLoan;
    }

    private void validateLoanApplication(BigDecimal requestedAmount, BigDecimal monthlyIncome,
                                         Integer creditScore, Integer termMonths) {
        if (requestedAmount == null || requestedAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new InvalidAmountException("Requested loan amount must be greater than zero");
        }
        if (monthlyIncome == null || monthlyIncome.compareTo(BigDecimal.ZERO) <= 0) {
            throw new InvalidAmountException("Monthly income must be greater than zero");
        }
        if (creditScore == null || creditScore < 300 || creditScore > 850) {
            throw new InvalidCreditScoreException("Credit score must be between 300 and 850");
        }
        if (termMonths == null || termMonths <= 0) {
            throw new InvalidAmountException("Loan term must be greater than zero months");
        }
    }

    private BigDecimal calculateDeterministicRiskScore(BigDecimal requestedAmount,
                                                       BigDecimal monthlyIncome,
                                                       BigDecimal monthlyExpense,
                                                       BigDecimal existingDebt,
                                                       Integer creditScore) {
        BigDecimal creditRisk = calculateCreditRisk(creditScore);
        BigDecimal dtiRisk = calculateDTIRisk(monthlyIncome, monthlyExpense, existingDebt);
        BigDecimal ltiRisk = calculateLTIRisk(requestedAmount, monthlyIncome);

        BigDecimal totalRisk = creditRisk.add(dtiRisk).add(ltiRisk);
        if (totalRisk.compareTo(new BigDecimal("100")) > 0) {
            totalRisk = new BigDecimal("100");
        }
        return totalRisk.setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal calculateCreditRisk(Integer creditScore) {
        if (creditScore >= CREDIT_SCORE_EXCELLENT) return BigDecimal.ZERO;
        else if (creditScore >= CREDIT_SCORE_GOOD) return new BigDecimal("10");
        else if (creditScore >= CREDIT_SCORE_FAIR) return new BigDecimal("25");
        else return new BigDecimal("50");
    }

    private BigDecimal calculateDTIRisk(BigDecimal monthlyIncome, BigDecimal monthlyExpense,
                                        BigDecimal existingDebt) {
        if (monthlyIncome.compareTo(BigDecimal.ZERO) == 0) return new BigDecimal("30");

        BigDecimal monthlyExistingDebt = existingDebt.divide(new BigDecimal("12"), 2, RoundingMode.HALF_UP);
        BigDecimal totalMonthlyObligations = monthlyExpense.add(monthlyExistingDebt);
        BigDecimal dti = totalMonthlyObligations
                .divide(monthlyIncome, 4, RoundingMode.HALF_UP)
                .multiply(new BigDecimal("100"));

        if (dti.compareTo(DTI_EXCELLENT_THRESHOLD) < 0) return BigDecimal.ZERO;
        else if (dti.compareTo(DTI_HIGH_THRESHOLD) < 0) return new BigDecimal("15");
        else return new BigDecimal("30");
    }

    private BigDecimal calculateLTIRisk(BigDecimal requestedAmount, BigDecimal monthlyIncome) {
        BigDecimal annualIncome = monthlyIncome.multiply(new BigDecimal("12"));
        if (annualIncome.compareTo(BigDecimal.ZERO) == 0) return new BigDecimal("20");

        BigDecimal lti = requestedAmount.divide(annualIncome, 2, RoundingMode.HALF_UP);
        if (lti.compareTo(LTI_LOW_THRESHOLD) < 0) return BigDecimal.ZERO;
        else if (lti.compareTo(LTI_HIGH_THRESHOLD) < 0) return new BigDecimal("10");
        else return new BigDecimal("20");
    }

    private RiskLevel determineRiskLevel(BigDecimal riskScore) {
        if (riskScore.compareTo(RISK_LOW_MAX) <= 0) return RiskLevel.LOW;
        else if (riskScore.compareTo(RISK_MEDIUM_MAX) <= 0) return RiskLevel.MEDIUM;
        else return RiskLevel.HIGH;
    }

    public List<Loan> getUserLoans(User requestingUser) {
        return loanRepository.findByUserId(requestingUser.getUserId());
    }

    public Loan getLoanById(Long loanId, User requestingUser) {
        Loan loan = loanRepository.findById(loanId)
                .orElseThrow(() -> new LoanNotFoundException("Loan not found: " + loanId));

        if (!loan.getUserId().equals(requestingUser.getUserId())) {
            throw new UnauthorizedException("You do not have access to this loan");
        }
        return loan;
    }
}