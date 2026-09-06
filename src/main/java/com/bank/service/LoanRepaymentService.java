package com.bank.service;

import com.bank.database.DatabaseConnection;
import com.bank.exception.*;
import com.bank.model.dto.LoanDTO;
import com.bank.model.dto.LoanMapper;
import com.bank.model.entity.*;
import com.bank.model.enums.*;
import com.bank.model.repository.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

public class LoanRepaymentService {

    private final LoanRepository loanRepository;
    private final LoanPaymentRepository loanPaymentRepository;
    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;

    public LoanRepaymentService(LoanRepository loanRepository,
                                LoanPaymentRepository loanPaymentRepository,
                                AccountRepository accountRepository,
                                TransactionRepository transactionRepository) {
        this.loanRepository = loanRepository;
        this.loanPaymentRepository = loanPaymentRepository;
        this.accountRepository = accountRepository;
        this.transactionRepository = transactionRepository;
    }

    /**
     * Makes a repayment toward an active loan (flexible amount, up to the
     * remaining outstanding balance). Each payment is split into
     * principal/interest proportionally to the loan's fixed
     * principal-to-total-obligation ratio - since flat-rate interest is
     * fixed at approval time (not accruing monthly like amortized interest),
     * a proportional split of any payment amount is a reasonable
     * simplification for this simulation.
     * When outstanding balance reaches zero, the loan is automatically
     * marked PAID_OFF.
     */
    public LoanDTO makePayment(User payer, Long loanId, Long accountId, BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new InvalidAmountException("Payment amount must be greater than zero");
        }

        Connection conn = null;
        Loan resultLoan;
        try {
            conn = DatabaseConnection.getConnection();
            conn.setAutoCommit(false);

            Loan loan = loanRepository.findByIdForUpdate(conn, loanId)
                    .orElseThrow(() -> new LoanNotFoundException("Loan not found: " + loanId));

            if (!loan.getUserId().equals(payer.getUserId())) {
                throw new UnauthorizedException("You do not have access to this loan");
            }
            if (loan.getStatus() != LoanStatus.ACTIVE) {
                throw new LoanStateException("Only ACTIVE loans can be repaid. Current status: " + loan.getStatus());
            }
            if (amount.compareTo(loan.getOutstandingBalance()) > 0) {
                throw new InvalidAmountException(
                        "Payment exceeds outstanding balance. Outstanding: " + loan.getOutstandingBalance());
            }

            Account account = accountRepository.findByIdForUpdate(conn, accountId)
                    .orElseThrow(() -> new AccountNotFoundException("Account not found: " + accountId));

            if (!account.getUserId().equals(payer.getUserId())) {
                throw new UnauthorizedException("You do not have access to this account");
            }
            if (account.getStatus() != AccountStatus.ACTIVE) {
                throw new AccountNotActiveException("Account is not ACTIVE");
            }
            if (account.getBalance().compareTo(amount) < 0) {
                throw new InsufficientBalanceException(
                        "Insufficient balance: available " + account.getBalance() + ", requested " + amount);
            }

            // Debit the paying account
            account.setBalance(account.getBalance().subtract(amount));
            accountRepository.updateWithConnection(conn, account);

            // Split this payment proportionally into principal/interest
            BigDecimal totalObligation = LoanFinanceUtil.calculateTotalObligation(loan);
            BigDecimal principalRatio = loan.getApprovedAmount()
                    .divide(totalObligation, 10, RoundingMode.HALF_UP);

            BigDecimal principalPortion = amount.multiply(principalRatio).setScale(4, RoundingMode.HALF_UP);
            BigDecimal interestPortion = amount.subtract(principalPortion);

            // Update outstanding balance, auto-complete if fully paid
            BigDecimal newOutstanding = loan.getOutstandingBalance().subtract(amount);
            if (newOutstanding.compareTo(BigDecimal.ZERO) < 0) {
                newOutstanding = BigDecimal.ZERO; // guard against rounding drift
            }
            loan.setOutstandingBalance(newOutstanding);

            if (newOutstanding.compareTo(BigDecimal.ZERO) == 0) {
                loan.setStatus(LoanStatus.PAID_OFF);
            }
            loanRepository.updateWithConnection(conn, loan);

            // Insert transaction record for the repayment
            Transaction repaymentTxn = Transaction.builder()
                    .accountId(account.getAccountId())
                    .transactionType(TransactionType.LOAN_REPAYMENT)
                    .amount(amount)
                    .currency(account.getCurrency())
                    .description("Repayment for loan #" + loanId)
                    .status(TransactionStatus.COMPLETED)
                    .build();
            Transaction savedTxn = transactionRepository.saveWithConnection(conn, repaymentTxn);

            // Insert loan_payments record
            LoanPayment payment = LoanPayment.builder()
                    .loanId(loanId)
                    .accountId(account.getAccountId())
                    .transactionId(savedTxn.getTransactionId())
                    .amount(amount)
                    .principalAmount(principalPortion)
                    .interestAmount(interestPortion)
                    .dueDate(null) // flexible schedule - no fixed due dates
                    .status(LoanPaymentStatus.COMPLETED)
                    .paymentMethod("ACCOUNT_TRANSFER")
                    .build();
            loanPaymentRepository.saveWithConnection(conn, payment);

            conn.commit();
            resultLoan = loan;

        } catch (RuntimeException | SQLException e) {
            rollbackQuietly(conn);
            if (e instanceof RuntimeException re) throw re;
            throw new RuntimeException("Loan repayment failed", e);
        } finally {
            closeQuietly(conn);
        }

        return LoanMapper.toDTO(resultLoan);
    }

    public List<LoanPayment> getPaymentHistory(Long loanId, User requestingUser) {
        Loan loan = loanRepository.findById(loanId)
                .orElseThrow(() -> new LoanNotFoundException("Loan not found: " + loanId));
        if (!loan.getUserId().equals(requestingUser.getUserId())) {
            throw new UnauthorizedException("You do not have access to this loan");
        }
        return loanPaymentRepository.findByLoanId(loanId);
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