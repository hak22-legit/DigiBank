package com.bank.service;

import com.bank.database.DatabaseConnection;
import com.bank.exception.*;
import com.bank.model.entity.Account;
import com.bank.model.entity.Admin;
import com.bank.model.entity.Loan;
import com.bank.model.entity.Transaction;
import com.bank.model.enums.*;
import com.bank.model.repository.AccountRepository;
import com.bank.model.repository.LoanRepository;
import com.bank.model.repository.TransactionRepository;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;

public class LoanApprovalService {

    private final LoanRepository loanRepository;
    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;
    private final AuditLogService auditLogService;

    public LoanApprovalService(LoanRepository loanRepository,
                               AccountRepository accountRepository,
                               TransactionRepository transactionRepository,
                               AuditLogService auditLogService) {
        this.loanRepository = loanRepository;
        this.accountRepository = accountRepository;
        this.transactionRepository = transactionRepository;
        this.auditLogService = auditLogService;
    }

    public List<Loan> getPendingLoans(Admin loanOfficer) {
        assertLoanOfficer(loanOfficer);
        return loanRepository.findByStatus(LoanStatus.PENDING.name());
    }

    /**
     * Approves a loan and disburses the approved amount into the borrower's
     * chosen account in one ACID transaction: lock account -> credit balance
     * -> insert LOAN_DISBURSEMENT transaction -> activate loan -> commit.
     * The disbursement account is chosen HERE (at approval time), not at
     * application time, since the loan officer makes the final call.
     */
    public Loan approveLoan(Admin loanOfficer, Long loanId, Long disbursementAccountId,
                            BigDecimal approvedAmount, BigDecimal interestRate, Integer termMonths) {
        assertLoanOfficer(loanOfficer);

        if (approvedAmount == null || approvedAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new InvalidAmountException("Approved amount must be greater than zero");
        }

        Connection conn = null;
        Loan resultLoan;
        try {
            conn = DatabaseConnection.getConnection();
            conn.setAutoCommit(false);

            Loan loan = loanRepository.findByIdForUpdate(conn, loanId)
                    .orElseThrow(() -> new LoanNotFoundException("Loan not found: " + loanId));

            if (loan.getStatus() != LoanStatus.PENDING) {
                throw new LoanStateException("Only PENDING loans can be approved. Current status: " + loan.getStatus());
            }

            Account account = accountRepository.findByIdForUpdate(conn, disbursementAccountId)
                    .orElseThrow(() -> new AccountNotFoundException("Account not found: " + disbursementAccountId));

            if (!account.getUserId().equals(loan.getUserId())) {
                throw new UnauthorizedException("Disbursement account must belong to the loan applicant");
            }
            if (account.getStatus() != AccountStatus.ACTIVE) {
                throw new AccountNotActiveException("Disbursement account is not ACTIVE");
            }

            // Credit the approved amount into the borrower's account
            account.setBalance(account.getBalance().add(approvedAmount));
            accountRepository.updateWithConnection(conn, account);

            Transaction disbursement = Transaction.builder()
                    .accountId(account.getAccountId())
                    .transactionType(TransactionType.LOAN_DISBURSEMENT)
                    .amount(approvedAmount)
                    .currency(account.getCurrency())
                    .description("Loan disbursement for loan #" + loanId)
                    .status(TransactionStatus.COMPLETED)
                    .build();
            transactionRepository.saveWithConnection(conn, disbursement);

            loan.setAccountId(disbursementAccountId);
            loan.setApprovedAmount(approvedAmount);
            loan.setInterestRate(interestRate);
            loan.setTermMonths(termMonths);
            loan.setStatus(LoanStatus.ACTIVE);
            loan.setApprovedBy(loanOfficer.getAdminId());
            loan.setApprovedAt(LocalDateTime.now());
            loan.setOutstandingBalance(approvedAmount);

            loanRepository.updateWithConnection(conn, loan);

            conn.commit();
            resultLoan = loan;

        } catch (RuntimeException | SQLException e) {
            rollbackQuietly(conn);
            if (e instanceof RuntimeException re) throw re;
            throw new RuntimeException("Loan approval failed", e);
        } finally {
            closeQuietly(conn);
        }

        auditLogService.log(loanOfficer.getAdminId(), "APPROVE_LOAN", "loans", loanId,
                "Approved loan: amount=" + approvedAmount + ", rate=" + interestRate + "%, term=" + termMonths + "mo");

        return resultLoan;
    }

    public Loan rejectLoan(Admin loanOfficer, Long loanId, String rejectionReason) {
        assertLoanOfficer(loanOfficer);

        Loan loan = loanRepository.findById(loanId)
                .orElseThrow(() -> new LoanNotFoundException("Loan not found: " + loanId));

        if (loan.getStatus() != LoanStatus.PENDING) {
            throw new LoanStateException("Only PENDING loans can be rejected. Current status: " + loan.getStatus());
        }

        loan.setStatus(LoanStatus.REJECTED);
        loan.setApprovedBy(loanOfficer.getAdminId());
        loan.setApprovedAt(LocalDateTime.now());
        loan.setRejectionReason(rejectionReason);

        Loan updated = loanRepository.save(loan);

        auditLogService.log(loanOfficer.getAdminId(), "REJECT_LOAN", "loans", loanId,
                "Rejected loan: " + rejectionReason);

        return updated;
    }

    private void assertLoanOfficer(Admin admin) {
        if (admin.getRole() != AdminRole.LOAN_OFFICER) {
            throw new UnauthorizedException("Only LOAN_OFFICER can perform this action");
        }
    }

    private void rollbackQuietly(Connection conn) {
        if (conn != null) {
            try { conn.rollback(); } catch (SQLException ignored) { }
        }
    }

    private void closeQuietly(Connection conn) {
        if (conn != null) {
            try { conn.setAutoCommit(true); conn.close(); } catch (SQLException ignored) { }
        }
    }
}