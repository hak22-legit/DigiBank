package com.bank.model;

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
    private Integer logId;
    private Integer adminId;
    private String action;
    private String targetTable;
    private Integer targetId;
    private String details;
    private String ipAddress;
    private LocalDateTime createdAt;
}