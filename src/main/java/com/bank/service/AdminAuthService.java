package com.bank.service;

import com.bank.model.dto.AdminDTO;
import com.bank.model.dto.AdminMapper;
import com.bank.model.enums.AdminStatus;
import com.bank.exception.AuthenticationException;
import com.bank.model.entity.Admin;
import com.bank.model.repository.AdminRepository;
import com.bank.security.PasswordHasher;
import com.bank.security.SessionManager;

public class AdminAuthService {

    private final AdminRepository adminRepository;
    private final AuditLogService auditLogService;

    public AdminAuthService(AdminRepository adminRepository, AuditLogService auditLogService) {
        this.adminRepository = adminRepository;
        this.auditLogService = auditLogService;
    }

    public AdminDTO login(String username, String password) {
        Admin admin = adminRepository.findByUsername(username)
                .orElseThrow(() -> new AuthenticationException("Invalid username or password"));

        if (!PasswordHasher.verify(password, admin.getPasswordHash())) {
            throw new AuthenticationException("Invalid username or password");
        }
        if (admin.getStatus() != AdminStatus.ACTIVE) {
            throw new AuthenticationException("Admin account is not active. Status: " + admin.getStatus());
        }

        SessionManager.loginAdmin(admin); // Session ទុក Entity ពេញលេញ
        auditLogService.log(admin.getAdminId(), "LOGIN", "admins", admin.getAdminId(),
                "Admin logged in: " + admin.getUsername());

        return AdminMapper.toDTO(admin);
    }

    public void logout() {
        Admin admin = SessionManager.getCurrentAdmin();
        if (admin != null) {
            auditLogService.log(admin.getAdminId(), "LOGOUT", "admins", admin.getAdminId(),
                    "Admin logged out: " + admin.getUsername());
        }
        SessionManager.logout();
    }

    public void changePassword(Admin admin, String currentPassword, String newPassword) {
        if (!PasswordHasher.verify(currentPassword, admin.getPasswordHash())) {
            throw new AuthenticationException("Current password is incorrect");
        }

        admin.setPasswordHash(PasswordHasher.hash(newPassword));
        adminRepository.save(admin);

        auditLogService.log(admin.getAdminId(), "CHANGE_PASSWORD", "admins", admin.getAdminId(),
                "Admin changed their own password");
    }
}