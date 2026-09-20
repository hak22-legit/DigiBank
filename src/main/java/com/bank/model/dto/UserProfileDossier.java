package com.bank.model.dto;

import com.bank.model.entity.Account;
import com.bank.model.enums.UserStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserProfileDossier {
    private Long userId;
    private String username;
    private String fullName;
    private String email;
    private String phone;
    private UserStatus status;
    private int failedLoginAttempts;
    private LocalDate registrationDate;
    private String kycVerificationLevel;
    private String securityMode;
    private List<Account> linkedAccounts;
}
