package com.bank.service;

import com.bank.model.enums.AdminRole;
import com.bank.exception.UnauthorizedException;
import com.bank.model.entity.Admin;
import com.bank.model.entity.AuditLog;
import com.bank.model.PagedResult;
import com.bank.model.repository.AuditLogRepository;

import java.util.List;

public class AuditLogService {

    private final AuditLogRepository auditLogRepository;

    public AuditLogService(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    public void log(Long adminId, String action, String targetTable, Long targetId, String details) {
        AuditLog entry = AuditLog.builder()
                .adminId(adminId)
                .action(action)
                .targetTable(targetTable)
                .targetId(targetId)
                .details(details)
                .ipAddress("127.0.0.1")
                .build();

        auditLogRepository.save(entry);
    }

    public PagedResult<AuditLog> getLogsPaginated(Admin requestingAdmin, int page, int pageSize) {
        assertSuperAdmin(requestingAdmin);

        if (page < 1) page = 1;
        if (pageSize < 1) pageSize = 10;

        long totalItems = auditLogRepository.countAll();
        int totalPages = (int) Math.ceil((double) totalItems / pageSize);
        int offset = (page - 1) * pageSize;

        List<AuditLog> items = auditLogRepository.findPaginated(offset, pageSize);

        return PagedResult.<AuditLog>builder()
                .items(items)
                .currentPage(page)
                .pageSize(pageSize)
                .totalItems(totalItems)
                .totalPages(Math.max(totalPages, 1))
                .build();
    }

    public List<AuditLog> getLogsForAdmin(Admin requestingAdmin, Long targetAdminId) {
        assertSuperAdmin(requestingAdmin);
        return auditLogRepository.findByAdminId(targetAdminId);
    }

    public List<AuditLog> getFraudRelatedLogs(Admin requestingAdmin) {
        assertRole(requestingAdmin, AdminRole.SUPER_ADMIN, AdminRole.COMPLIANCE_OFFICER);

        List<String> fraudActions = List.of(
                "INVESTIGATE_FRAUD", "RESOLVE_FRAUD", "FREEZE_ACCOUNT", "UNFREEZE_ACCOUNT");
        return auditLogRepository.findAll().stream()
                .filter(log -> fraudActions.contains(log.getAction()))
                .toList();
    }

    private void assertSuperAdmin(Admin admin) {
        if (admin.getRole() != AdminRole.SUPER_ADMIN) {
            throw new UnauthorizedException("Only SUPER_ADMIN can perform this action");
        }
    }

    private void assertRole(Admin admin, AdminRole... allowed) {
        for (AdminRole role : allowed) {
            if (admin.getRole() == role) return;
        }
        throw new UnauthorizedException("You do not have permission to perform this action");
    }
}