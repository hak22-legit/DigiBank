package com.bank.service;

import com.bank.database.DatabaseConnection;
import com.bank.enums.AccountStatus;
import com.bank.enums.AdminRole;
import com.bank.enums.FraudStatus;
import com.bank.exception.AccountNotFoundException;
import com.bank.exception.FraudAlertStateException;
import com.bank.exception.UnauthorizedException;
import com.bank.model.Account;
import com.bank.model.Admin;
import com.bank.model.AuditLog;
import com.bank.model.FraudAlert;
import com.bank.repository.AccountRepository;
import com.bank.repository.AuditLogRepository;
import com.bank.repository.FraudAlertRepository;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;

public class FraudInvestigationService {

    private final FraudAlertRepository fraudAlertRepository;
    private final AccountRepository accountRepository;
    private final AuditLogRepository auditLogRepository;   // ⚠️ NEW - raw repo for atomic freeze/unfreeze
    private final AuditLogService auditLogService;          // kept for investigateAlert/resolveAlert

    public FraudInvestigationService(FraudAlertRepository fraudAlertRepository,
                                     AccountRepository accountRepository,
                                     AuditLogRepository auditLogRepository,
                                     AuditLogService auditLogService) {
        this.fraudAlertRepository = fraudAlertRepository;
        this.accountRepository = accountRepository;
        this.auditLogRepository = auditLogRepository;
        this.auditLogService = auditLogService;
    }

    public List<FraudAlert> getOpenAlerts(Admin complianceOfficer) {
        assertComplianceOfficer(complianceOfficer);
        return fraudAlertRepository.findByStatus(FraudStatus.OPEN.name());
    }

    public List<FraudAlert> getAllAlerts(Admin complianceOfficer) {
        assertComplianceOfficer(complianceOfficer);
        return fraudAlertRepository.findAll();
    }

    // Unchanged from baseline - guard condition already present
    public FraudAlert investigateAlert(Admin complianceOfficer, Long alertId) {
        assertComplianceOfficer(complianceOfficer);

        FraudAlert alert = fraudAlertRepository.findById(alertId)
                .orElseThrow(() -> new IllegalArgumentException("Fraud alert not found: " + alertId));

        // Prevent double-claiming: block if another admin is already
        // investigating this alert, or if it's already been resolved.
        if (alert.getStatus() == FraudStatus.INVESTIGATING
                && alert.getInvestigatedBy() != null
                && !complianceOfficer.getAdminId().equals(alert.getInvestigatedBy())) {
            throw new FraudAlertStateException(
                    "This alert is already being investigated by admin #" + alert.getInvestigatedBy());
        }
        if (alert.getStatus() == FraudStatus.RESOLVED || alert.getStatus() == FraudStatus.CONFIRMED_FRAUD) {
            throw new FraudAlertStateException("This alert has already been resolved");
        }

        alert.setStatus(FraudStatus.INVESTIGATING);
        alert.setInvestigatedBy(complianceOfficer.getAdminId());
        FraudAlert updated = fraudAlertRepository.update(alert);

        auditLogService.log(complianceOfficer.getAdminId(), "INVESTIGATE_FRAUD", "fraud_alerts", alertId,
                "Started investigation on alert: " + alert.getDescription());

        return updated;
    }

    // Unchanged from baseline
    public FraudAlert resolveAlert(Admin complianceOfficer, Long alertId,
                                   String resolutionNotes, boolean confirmedFraud) {
        assertComplianceOfficer(complianceOfficer);

        FraudAlert alert = fraudAlertRepository.findById(alertId)
                .orElseThrow(() -> new IllegalArgumentException("Fraud alert not found: " + alertId));

        alert.setStatus(confirmedFraud ? FraudStatus.CONFIRMED_FRAUD : FraudStatus.RESOLVED);
        alert.setInvestigatedBy(complianceOfficer.getAdminId());
        alert.setResolutionNotes(resolutionNotes);
        alert.setResolvedAt(LocalDateTime.now());
        FraudAlert updated = fraudAlertRepository.update(alert);

        auditLogService.log(complianceOfficer.getAdminId(), "RESOLVE_FRAUD", "fraud_alerts", alertId,
                "Resolved as " + alert.getStatus() + ": " + resolutionNotes);

        return updated;
    }

    // ⚠️ CHANGED: now atomic (lock + update + audit log in one transaction)
    public Account freezeAccount(Admin complianceOfficer, Long accountId, String reason) {
        assertComplianceOfficer(complianceOfficer);

        Connection conn = null;
        try {
            conn = DatabaseConnection.getConnection();
            conn.setAutoCommit(false);

            Account account = accountRepository.findByIdForUpdate(conn, accountId)
                    .orElseThrow(() -> new AccountNotFoundException("Account not found: " + accountId));

            account.setStatus(AccountStatus.FROZEN);
            accountRepository.updateWithConnection(conn, account);

            AuditLog log = AuditLog.builder()
                    .adminId(complianceOfficer.getAdminId())
                    .action("FREEZE_ACCOUNT")
                    .targetTable("accounts")
                    .targetId(accountId)
                    .details("Account frozen: " + reason)
                    .ipAddress("127.0.0.1")
                    .build();
            auditLogRepository.saveWithConnection(conn, log);

            conn.commit();
            return account;

        } catch (SQLException e) {
            rollbackQuietly(conn);
            throw new RuntimeException("Database error while freezing account", e);
        } catch (RuntimeException e) {
            rollbackQuietly(conn);
            throw e;
        } finally {
            closeQuietly(conn);
        }
    }

    // ⚠️ CHANGED: now atomic, same pattern as freezeAccount
    public Account unfreezeAccount(Admin complianceOfficer, Long accountId) {
        assertComplianceOfficer(complianceOfficer);

        Connection conn = null;
        try {
            conn = DatabaseConnection.getConnection();
            conn.setAutoCommit(false);

            Account account = accountRepository.findByIdForUpdate(conn, accountId)
                    .orElseThrow(() -> new AccountNotFoundException("Account not found: " + accountId));

            account.setStatus(AccountStatus.ACTIVE);
            accountRepository.updateWithConnection(conn, account);

            AuditLog log = AuditLog.builder()
                    .adminId(complianceOfficer.getAdminId())
                    .action("UNFREEZE_ACCOUNT")
                    .targetTable("accounts")
                    .targetId(accountId)
                    .details("Account unfrozen")
                    .ipAddress("127.0.0.1")
                    .build();
            auditLogRepository.saveWithConnection(conn, log);

            conn.commit();
            return account;

        } catch (SQLException e) {
            rollbackQuietly(conn);
            throw new RuntimeException("Database error while unfreezing account", e);
        } catch (RuntimeException e) {
            rollbackQuietly(conn);
            throw e;
        } finally {
            closeQuietly(conn);
        }
    }

    private void rollbackQuietly(Connection conn) {
        if (conn != null) {
            try { conn.rollback(); } catch (SQLException ignored) {}
        }
    }

    private void closeQuietly(Connection conn) {
        if (conn != null) {
            try { conn.close(); } catch (SQLException ignored) {}
        }
    }

    private void assertComplianceOfficer(Admin admin) {
        if (admin.getRole() != AdminRole.COMPLIANCE_OFFICER) {
            throw new UnauthorizedException("Only COMPLIANCE_OFFICER can perform this action");
        }
    }
}