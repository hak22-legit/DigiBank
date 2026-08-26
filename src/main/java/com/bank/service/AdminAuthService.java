package com.bank.service;

import com.bank.enums.AdminStatus;
import com.bank.exception.AuthenticationException;
import com.bank.model.Admin;
import com.bank.repository.AdminRepository;
import com.bank.security.PasswordHasher;
import com.bank.security.SessionManager;

public class AdminAuthService {

    private final AdminRepository adminRepository;

    public AdminAuthService(AdminRepository adminRepository) {
        this.adminRepository = adminRepository;
    }

    public Admin login(String username, String password) {
        Admin admin = adminRepository.findByUsername(username)
                .orElseThrow(() -> new AuthenticationException("Invalid username or password"));

        if (!PasswordHasher.verify(password, admin.getPasswordHash())) {
            throw new AuthenticationException("Invalid username or password");
        }

        if (admin.getStatus() != AdminStatus.ACTIVE) {
            throw new AuthenticationException("Admin account is not active. Status: " + admin.getStatus());
        }

        SessionManager.loginAdmin(admin);
        return admin;
    }

    public void logout() {
        SessionManager.logout();
    }
}