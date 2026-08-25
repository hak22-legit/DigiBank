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
    private Long alertId;
    private Long userId;
    private Long accountId;
    private Long transactionId;
    private RiskLevel riskLevel;
    private String description;
    private FraudStatus status;
    private Long investigatedBy;
    private LocalDateTime resolvedAt;
    private String resolutionNotes;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}