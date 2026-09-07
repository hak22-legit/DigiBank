package com.bank.service;

import com.bank.model.dto.LoanDTO;
import com.bank.model.dto.LoanMapper;
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

    private final LoanRepository loanRepository;
    private final RiskAssessmentService riskAssessmentService;

    public LoanService(LoanRepository loanRepository, RiskAssessmentService riskAssessmentService) {
        this.loanRepository = loanRepository;
        this.riskAssessmentService = riskAssessmentService;
    }

    /**
     * Submit a loan application with deterministic risk assessment.
     * Takes the authenticated applicant directly (not a raw userId) so the
     * loan can never be created under someone else's identity.
     */
    public LoanDTO applyForLoan(User applicant, BigDecimal requestedAmount,
                                BigDecimal monthlyIncome, BigDecimal monthlyExpense,
                                BigDecimal existingDebt, Integer creditScore,
                                Integer termMonths) {
        validateLoanApplication(requestedAmount, monthlyIncome, creditScore, termMonths);

        RiskAssessmentResult assessment = riskAssessmentService.assess(
                requestedAmount, monthlyIncome, monthlyExpense, existingDebt, creditScore, termMonths);
        BigDecimal riskScore = assessment.getRiskScore();
        RiskLevel riskLevel = assessment.getRiskLevel();

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
        logger.info("Loan application created: ID={}, Risk={}, Prediction={}, Status={}",
                savedLoan.getLoanId(), riskLevel, assessment.getPrediction(), savedLoan.getStatus());

        return LoanMapper.toDTO(savedLoan);
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


    public List<LoanDTO> getUserLoans(User requestingUser) {
        List<Loan> loans = loanRepository.findByUserId(requestingUser.getUserId());
        return LoanMapper.toDTOList(loans);
    }

    public LoanDTO getLoanById(Long loanId, User requestingUser) {
        Loan loan = loanRepository.findById(loanId)
                .orElseThrow(() -> new LoanNotFoundException("Loan not found: " + loanId));

        if (!loan.getUserId().equals(requestingUser.getUserId())) {
            throw new UnauthorizedException("You do not have access to this loan");
        }
        return LoanMapper.toDTO(loan);
    }
}