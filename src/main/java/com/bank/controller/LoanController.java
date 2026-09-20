package com.bank.controller;

import com.bank.model.dto.LoanDTO;
import com.bank.model.entity.Loan;
import com.bank.model.entity.LoanPayment;
import com.bank.model.entity.User;
import com.bank.service.LoanApprovalService;
import com.bank.service.LoanRepaymentService;
import com.bank.service.LoanService;
import lombok.RequiredArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@RequiredArgsConstructor
public class LoanController {
    private final LoanService loanService;
    private final LoanApprovalService loanApprovalService;
    private final LoanRepaymentService loanRepaymentService;

    public LoanDTO applyForLoan(User user, BigDecimal amount, BigDecimal income, BigDecimal expense, BigDecimal debt, Integer creditScore, Integer term) {
        return loanService.applyForLoan(user, amount, income, expense, debt, creditScore, term);
    }

    public List<LoanDTO> getUserLoans(User user) {
        return loanService.getUserLoans(user);
    }

    public LoanDTO repayLoan(User user, Long loanId, Long accountId, BigDecimal amount) {
        return loanRepaymentService.makePayment(user, loanId, accountId, amount);
    }

    public List<LoanPayment> getPaymentHistory(Long loanId, User user) {
        return loanRepaymentService.getPaymentHistory(loanId, user);
    }

    public List<LoanPayment> getRepaymentSchedule(User user) {
        return loanService.getRepaymentSchedule(user);
    }

    public java.util.Optional<LoanDTO> getActiveLoan(User user) {
        return loanService.getActiveLoan(user);
    }

    public List<Loan> getPendingLoans(com.bank.model.entity.Admin admin) {
        return loanApprovalService.getPendingLoans(admin);
    }

    public Loan approveLoan(com.bank.model.entity.Admin admin, Long loanId, Long accountId, BigDecimal amount, BigDecimal rate, Integer term) {
        return loanApprovalService.approveLoan(admin, loanId, accountId, amount, rate, term);
    }

    public Loan rejectLoan(com.bank.model.entity.Admin admin, Long loanId, String reason) {
        return loanApprovalService.rejectLoan(admin, loanId, reason);
    }

    public com.bank.service.LoanService.LoanPipelineStats getUnderwritingPipelineStats(com.bank.model.entity.Admin admin) {
        return loanService.getUnderwritingPipelineStats(admin);
    }

    public List<Loan> getActiveLoanBook(com.bank.model.entity.Admin admin) {
        return loanService.getActiveLoanBook(admin);
    }

    public List<Loan> getCustomerBorrowingHistory(com.bank.model.entity.Admin admin, Long userId) {
        return loanService.getCustomerBorrowingHistory(admin, userId);
    }
}
