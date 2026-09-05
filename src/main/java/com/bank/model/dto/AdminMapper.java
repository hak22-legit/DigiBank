package com.bank.model.dto;

import com.bank.model.entity.Admin;

public class AdminMapper {
    public static AdminDTO toDTO(Admin admin) {
        return AdminDTO.builder()
                .adminId(admin.getAdminId())
                .username(admin.getUsername())
                .email(admin.getEmail())
                .fullName(admin.getFullName())
                .role(admin.getRole())
                .status(admin.getStatus())
                .build();
    }
}