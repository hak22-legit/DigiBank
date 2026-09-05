package com.bank.model.repository;

import com.bank.model.entity.FraudAlert;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

public interface FraudAlertRepository {
    Optional<FraudAlert> findById(Long alertId);
    List<FraudAlert> findByUserId(Long userId);
    List<FraudAlert> findByStatus(String status);
    List<FraudAlert> findAll();
    FraudAlert save(FraudAlert alert);
    FraudAlert update(FraudAlert alert);

    /**
     * Insert within an existing transaction/connection.
     * Used so a fraud alert commits atomically with the triggering transaction
     * in Transfer service (Phase 10, 17).
     */
    FraudAlert saveWithConnection(Connection conn, FraudAlert alert) throws SQLException;
}