package com.bank.model.dto;

import com.bank.model.enums.AdminRole;
import com.bank.model.enums.AdminStatus;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class AdminDTO {
    private Long adminId;
    private String username;
    private String email;
    private String fullName;
    private AdminRole role;
    private AdminStatus status;
    // passwordHash and securityAnswerHash intentionally omitted
}