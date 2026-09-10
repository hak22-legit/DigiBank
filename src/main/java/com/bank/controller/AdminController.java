package com.bank.controller;

import com.bank.model.entity.*;
import com.bank.service.AdminService;
import com.bank.service.AuditLogService;
import com.bank.service.FraudInvestigationService;
import lombok.RequiredArgsConstructor;

import java.util.List;

@RequiredArgsConstructor
public class AdminController {
    private final AdminService adminService;
    private final AuditLogService auditLogService;
    private final FraudInvestigationService fraudInvestigationService;

    public List<Admin> getAllAdmins(Admin admin) {
        return adminService.getAllAdmins(admin);
    }

    public Admin createAdmin(Admin admin, String username, String email, String password, String fullName, com.bank.model.enums.AdminRole role) {
        return adminService.createAdmin(admin, username, email, password, fullName, role);
    }

    public void resetAdminPassword(Admin admin, Long targetId, String newPassword) {
        adminService.resetAdminPassword(admin, targetId, newPassword);
    }

    public void suspendAdmin(Admin admin, Long targetId) {
        adminService.suspendAdmin(admin, targetId);
    }

    public void reactivateAdmin(Admin admin, Long targetId) {
        adminService.reactivateAdmin(admin, targetId);
    }

    public List<User> getAllUsers(Admin admin) {
        return adminService.getAllUsers(admin);
    }

    public com.bank.model.SystemStats getSystemStats(Admin admin) {
        return adminService.getSystemStats(admin);
    }

    public List<FraudAlert> getAllFraudAlerts(Admin admin) {
        return adminService.getAllFraudAlerts(admin);
    }

    public com.bank.model.PagedResult<AuditLog> getAuditLogs(Admin admin, int page, int size) {
        return auditLogService.getLogsPaginated(admin, page, size);
    }

    public List<FraudAlert> getOpenFraudAlerts(Admin admin) {
        return fraudInvestigationService.getOpenAlerts(admin);
    }

    public FraudAlert investigateAlert(Admin admin, Long alertId) {
        return fraudInvestigationService.investigateAlert(admin, alertId);
    }

    public FraudAlert resolveAlert(Admin admin, Long alertId, String notes, boolean confirmed) {
        return fraudInvestigationService.resolveAlert(admin, alertId, notes, confirmed);
    }

    public Account freezeAccount(Admin admin, Long accountId, String reason) {
        return fraudInvestigationService.freezeAccount(admin, accountId, reason);
    }

    public Account unfreezeAccount(Admin admin, Long accountId) {
        return fraudInvestigationService.unfreezeAccount(admin, accountId);
    }

    public List<AuditLog> getFraudAuditLogs(Admin admin) {
        return auditLogService.getFraudRelatedLogs(admin);
    }
}
