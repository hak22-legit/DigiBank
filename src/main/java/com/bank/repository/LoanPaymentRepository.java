package com.bank.repository;

import com.bank.model.LoanPayment;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

public interface LoanPaymentRepository {
    Optional<LoanPayment> findById(Long paymentId);
    List<LoanPayment> findByLoanId(Long loanId);
    List<LoanPayment> findAll();
    LoanPayment save(LoanPayment payment);
    boolean deleteById(Long paymentId);

    /**
     * Insert within an existing transaction/connection (Phase 21 ACID repayment flow).
     */
    LoanPayment saveWithConnection(Connection conn, LoanPayment payment) throws SQLException;
}