package com.bank.model.entity;

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
public class AuditLog {
    private Long logId;
    private Long adminId;
    private String action;
    private String targetTable;
    private Long targetId;
    private String details;
    private String ipAddress;
    private LocalDateTime createdAt;
}