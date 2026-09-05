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

    /**
     * Returns the security question for a username, WITHOUT requiring login -
     * needed so a locked-out admin (e.g. SUPER_ADMIN with no one above them)
     * can start the recovery flow.
     */
    public String getSecurityQuestion(String username) {
        Admin admin = adminRepository.findByUsername(username)
                .orElseThrow(() -> new AuthenticationException("Admin not found"));

        if (admin.getSecurityQuestion() == null) {
            throw new AuthenticationException(
                    "No security question set up for this account - contact another admin for recovery");
        }
        return admin.getSecurityQuestion();
    }

    /**
     * Completes self-service password recovery: verifies the security
     * answer, and if correct, sets a new password - no current password
     * or another admin's involvement required.
     */
    public void recoverPasswordWithSecurityAnswer(String username, String answer, String newPassword) {
        Admin admin = adminRepository.findByUsername(username)
                .orElseThrow(() -> new AuthenticationException("Admin not found"));

        if (admin.getSecurityAnswerHash() == null
                || !PasswordHasher.verify(answer.trim().toLowerCase(), admin.getSecurityAnswerHash())) {
            throw new AuthenticationException("Security answer is incorrect");
        }

        admin.setPasswordHash(PasswordHasher.hash(newPassword));
        adminRepository.save(admin);

        auditLogService.log(admin.getAdminId(), "PASSWORD_RECOVERY", "admins", admin.getAdminId(),
                "Password recovered via security question (self-service, no login required)");
    }

    /**
     * Lets a logged-in admin set or change their own recovery question -
     * self-service setup, not something another admin can do for them.
     */
    public void setSecurityQuestion(Admin admin, String question, String answer) {
        admin.setSecurityQuestion(question);
        admin.setSecurityAnswerHash(PasswordHasher.hash(answer.trim().toLowerCase()));
        adminRepository.save(admin);

        auditLogService.log(admin.getAdminId(), "SET_SECURITY_QUESTION", "admins", admin.getAdminId(),
                "Admin set/updated their security question");
    }
}