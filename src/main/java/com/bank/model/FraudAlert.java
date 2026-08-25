package com.bank.model;

import com.bank.enums.FraudStatus;
import com.bank.enums.RiskLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FraudAlert {
    private Integer alertId;
    private Integer userId;
    private Integer transactionId;
    private Integer resolvedBy;
    private RiskLevel riskLevel;
    private String reason;
    private FraudStatus status;
    private LocalDateTime createdAt;
    private LocalDateTime resolvedAt;
}