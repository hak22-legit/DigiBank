package com.bank.model.entity;

import com.bank.model.enums.AdminRole;
import com.bank.model.enums.AdminStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Admin {
    private Long adminId;
    private String username;
    private String email;
    private String passwordHash;
    private String fullName;
    private AdminRole role;
    private AdminStatus status;
    private String securityQuestion;
    private String securityAnswerHash;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}