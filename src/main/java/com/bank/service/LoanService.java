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

import com.bank.model.entity.LoanPayment;
import com.bank.model.repository.LoanPaymentRepository;
import com.bank.model.repository.LoanPaymentRepositoryImpl;
import java.util.Collections;
import java.util.Optional;

public class LoanService {
    private static final Logger logger = LoggerFactory.getLogger(LoanService.class);

    private final LoanRepository loanRepository;
    private final RiskAssessmentService riskAssessmentService;
    private final LoanPaymentRepository loanPaymentRepository;

    public LoanService(LoanRepository loanRepository, RiskAssessmentService riskAssessmentService) {
        this(loanRepository, riskAssessmentService, new LoanPaymentRepositoryImpl());
    }

    public LoanService(LoanRepository loanRepository, RiskAssessmentService riskAssessmentService,
                       LoanPaymentRepository loanPaymentRepository) {
        this.loanRepository = loanRepository;
        this.riskAssessmentService = riskAssessmentService;
        this.loanPaymentRepository = loanPaymentRepository;
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

    public Optional<LoanDTO> getActiveLoan(User user) {
        if (user == null || user.getUserId() == null) {
            return Optional.empty();
        }
        return loanRepository.findActiveLoanByUserId(user.getUserId())
                .map(LoanMapper::toDTO);
    }

    public boolean hasActiveLoan(User user) {
        return getActiveLoan(user).isPresent();
    }

    public List<LoanPayment> getRepaymentSchedule(User user) {
        if (user == null || user.getUserId() == null) {
            return Collections.emptyList();
        }
        Optional<Loan> activeLoanOpt = loanRepository.findActiveLoanByUserId(user.getUserId());
        if (activeLoanOpt.isEmpty()) {
            return Collections.emptyList();
        }
        Loan activeLoan = activeLoanOpt.get();
        if (loanPaymentRepository != null) {
            return loanPaymentRepository.findByLoanId(activeLoan.getLoanId());
        }
        return Collections.emptyList();
    }

    public List<LoanPayment> getRepaymentSchedule(Long loanId, User user) {
        if (user == null || user.getUserId() == null || loanId == null) {
            return Collections.emptyList();
        }
        Optional<Loan> loanOpt = loanRepository.findById(loanId);
        if (loanOpt.isEmpty()) {
            return Collections.emptyList();
        }
        Loan loan = loanOpt.get();
        if (!loan.getUserId().equals(user.getUserId()) || loan.getStatus() != LoanStatus.ACTIVE) {
            return Collections.emptyList();
        }
        if (loanPaymentRepository != null) {
            return loanPaymentRepository.findByLoanId(loanId);
        }
        return Collections.emptyList();
    }

    public record LoanPipelineStats(
            long applicationsInQueue,
            BigDecimal totalVolumePending,
            long approvedTodayCount,
            BigDecimal approvedTodayVolume,
            long rejectedTodayCount
    ) {}

    public LoanPipelineStats getUnderwritingPipelineStats(com.bank.model.entity.Admin admin) {
        assertLoanOfficer(admin);
        long pendingCount = loanRepository.countByStatus(LoanStatus.PENDING.name());
        BigDecimal pendingVolume = loanRepository.sumRequestedAmountByStatus(LoanStatus.PENDING.name());
        long approvedCount = loanRepository.countApprovedToday();
        BigDecimal approvedVolume = loanRepository.sumApprovedAmountToday();
        long rejectedCount = loanRepository.countRejectedToday();

        return new LoanPipelineStats(pendingCount, pendingVolume, approvedCount, approvedVolume, rejectedCount);
    }

    public List<Loan> getActiveLoanBook(com.bank.model.entity.Admin admin) {
        assertLoanOfficer(admin);
        return loanRepository.findByStatus(LoanStatus.ACTIVE.name());
    }

    public List<Loan> getCustomerBorrowingHistory(com.bank.model.entity.Admin admin, Long userId) {
        assertLoanOfficer(admin);
        return loanRepository.findByUserId(userId);
    }

    private void assertLoanOfficer(com.bank.model.entity.Admin admin) {
        if (admin == null || (admin.getRole() != com.bank.model.enums.AdminRole.LOAN_OFFICER && admin.getRole() != com.bank.model.enums.AdminRole.SUPER_ADMIN)) {
            throw new UnauthorizedException("Only LOAN_OFFICER or SUPER_ADMIN can access loan underwriting facilities");
        }
    }
}