package com.bank.model.dto;

import com.bank.model.enums.UserStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserDirectoryItem {
    private Long userId;
    private String username;
    private String fullName;
    private UserStatus status;
    private int accountCount;
    private BigDecimal totalBalance;
}
