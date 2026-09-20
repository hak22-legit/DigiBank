package com.bank.model.repository;

import com.bank.model.entity.Loan;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

public interface LoanRepository {
    Optional<Loan> findById(Long loanId);
    List<Loan> findByUserId(Long userId);
    List<Loan> findByStatus(String status);
    List<Loan> findByUserIdAndStatus(Long userId, String status);
    Optional<Loan> findActiveLoanByUserId(Long userId);
    List<Loan> findAll();
    Loan save(Loan loan);
    boolean deleteById(Long loanId);

    Optional<Loan> findByIdForUpdate(Connection conn, Long loanId) throws SQLException;
    void updateWithConnection(Connection conn, Loan loan) throws SQLException;

    /**
     * Insert within an existing transaction/connection (Phase 21 repayment
     * flow reuses this same pattern established for other entities).
     */
    Loan saveWithConnection(Connection conn, Loan loan) throws SQLException;

    long countByStatus(String status);
    java.math.BigDecimal sumRequestedAmountByStatus(String status);
    long countApprovedToday();
    java.math.BigDecimal sumApprovedAmountToday();
    long countRejectedToday();
}