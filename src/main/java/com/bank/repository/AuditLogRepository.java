package com.bank.repository;

import com.bank.model.AuditLog;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

public interface AuditLogRepository {
    Optional<AuditLog> findById(Long logId);
    List<AuditLog> findByAdminId(Long adminId);
    List<AuditLog> findAll();
    AuditLog save(AuditLog log);

    /**
     * Insert within an existing transaction/connection.
     * Used so an audit record commits atomically with the action it logs
     * (e.g. APPROVE_LOAN, FREEZE_ACCOUNT) in Phase 18-21.
     */
    AuditLog saveWithConnection(Connection conn, AuditLog log) throws SQLException;
}