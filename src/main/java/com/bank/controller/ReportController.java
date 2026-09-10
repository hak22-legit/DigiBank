package com.bank.controller;

import com.bank.model.dto.AccountDTO;
import com.bank.model.entity.User;
import com.bank.report.StatementReportService;
import lombok.RequiredArgsConstructor;

import java.time.LocalDateTime;

@RequiredArgsConstructor
public class ReportController {
    private final StatementReportService statementReportService;

    public String generateStatement(User user, AccountDTO account, LocalDateTime from, LocalDateTime to) {
        return statementReportService.generateStatement(user.getFullName(), account, from, to);
    }
}
