package com.bank.model.dto;

import com.bank.model.enums.UserStatus;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class UserDTO {
    private Long userId;
    private String username;
    private String email;
    private String fullName;
    private String phone;
    private UserStatus status;
    // passwordHash intentionally omitted - never exposed outside the service layer
}