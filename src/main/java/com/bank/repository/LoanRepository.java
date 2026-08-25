package com.bank.repository;

import com.bank.model.Loan;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

public interface LoanRepository {
    Optional<Loan> findById(Long loanId);
    List<Loan> findByUserId(Long userId);
    List<Loan> findByStatus(String status);
    List<Loan> findAll();
    Loan save(Loan loan);
    boolean deleteById(Long loanId);

    /**
     * Locks the loan row for update within an existing transaction.
     * Used by LoanRepayment service (Phase 21) alongside account locking.
     */
    Optional<Loan> findByIdForUpdate(Connection conn, Long loanId) throws SQLException;

    /**
     * Updates loan status/outstanding balance within an existing transaction/connection.
     */
    void updateWithConnection(Connection conn, Loan loan) throws SQLException;
}