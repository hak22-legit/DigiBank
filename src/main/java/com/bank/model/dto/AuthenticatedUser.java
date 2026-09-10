package com.bank.model.dto;

import com.bank.model.enums.AdminRole;
import com.bank.model.enums.UserRole;
import lombok.Builder;
import lombok.Getter;

/**
 * Universal DTO representing an authenticated subject across all user roles (Customer, Staff, Admin).
 * Encapsulates role determination and role-specific DTO projections.
 */
@Getter
@Builder
public class AuthenticatedUser {
    private final Long id;
    private final String username;
    private final String email;
    private final String fullName;
    private final UserRole role;
    private final String specificRole;
    private final UserDTO userDTO;
    private final AdminDTO adminDTO;

    public static AuthenticatedUser fromCustomer(UserDTO user) {
        return AuthenticatedUser.builder()
                .id(user.getUserId())
                .username(user.getUsername())
                .email(user.getEmail())
                .fullName(user.getFullName())
                .role(UserRole.CUSTOMER)
                .specificRole("CUSTOMER")
                .userDTO(user)
                .adminDTO(null)
                .build();
    }

    public static AuthenticatedUser fromAdmin(AdminDTO admin) {
        UserRole userRole = admin.getRole() == AdminRole.SUPER_ADMIN ? UserRole.ADMIN : UserRole.STAFF;
        return AuthenticatedUser.builder()
                .id(admin.getAdminId())
                .username(admin.getUsername())
                .email(admin.getEmail())
                .fullName(admin.getFullName())
                .role(userRole)
                .specificRole(admin.getRole().name())
                .userDTO(null)
                .adminDTO(admin)
                .build();
    }

    public boolean isCustomer() {
        return role == UserRole.CUSTOMER;
    }

    public boolean isStaff() {
        return role == UserRole.STAFF;
    }

    public boolean isAdmin() {
        return role == UserRole.ADMIN;
    }
}
