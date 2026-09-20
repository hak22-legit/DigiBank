package com.bank.controller;

import com.bank.model.dto.AccountDTO;
import com.bank.model.dto.StatementReportData;
import com.bank.model.entity.User;
import com.bank.report.StatementReportService;
import com.bank.service.StatementExportService;

import java.time.LocalDateTime;

/**
 * Controller for statement generation and financial reporting.
 * Delegates to the enterprise OpenPDF StatementExportService with cryptographic audit verification.
 */
public class ReportController {

    private final StatementExportService statementExportService;
    private final StatementReportService legacyStatementReportService;

    public ReportController(StatementExportService statementExportService) {
        this(statementExportService, null);
    }

    public ReportController(StatementReportService legacyStatementReportService) {
        this(null, legacyStatementReportService);
    }

    public ReportController(StatementExportService statementExportService, StatementReportService legacyStatementReportService) {
        this.statementExportService = statementExportService;
        this.legacyStatementReportService = legacyStatementReportService;
    }

    /**
     * Generates an official PDF statement and returns the path to the saved file.
     */
    public String generateStatement(User user, AccountDTO account, LocalDateTime from, LocalDateTime to) {
        if (statementExportService != null) {
            return statementExportService.generateStatement(user, account, from, to);
        }
        if (legacyStatementReportService != null) {
            return legacyStatementReportService.generateStatement(user.getFullName(), account, from, to);
        }
        throw new IllegalStateException("No statement export service configured in ReportController");
    }

    /**
     * Prepares and returns the detailed statement report data, including the SHA-256 ledger fingerprint.
     */
    public StatementReportData prepareReportData(User user, AccountDTO account, LocalDateTime from, LocalDateTime to) {
        if (statementExportService != null) {
            return statementExportService.prepareReportData(user, account, from, to);
        }
        return null;
    }

    public StatementExportService getStatementExportService() {
        return statementExportService;
    }
}
