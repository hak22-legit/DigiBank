package com.bank.controller;

import com.bank.model.dto.AccountDTO;
import com.bank.model.dto.StatementReportData;
import com.bank.model.entity.User;
import com.bank.model.entity.Account;
import com.bank.model.entity.Transaction;
import com.bank.report.StatementReportService;
import com.bank.service.PdfStatementService;
import com.bank.service.StatementExportService;

import java.io.File;
import java.time.LocalDateTime;

/**
 * Controller for statement generation and financial reporting.
 * Delegates to the enterprise OpenPDF StatementExportService and PdfStatementService.
 */
public class ReportController {

    private final StatementExportService statementExportService;
    private final StatementReportService legacyStatementReportService;
    private final PdfStatementService pdfStatementService;

    public ReportController(StatementExportService statementExportService) {
        this(statementExportService, null, new PdfStatementService());
    }

    public ReportController(StatementReportService legacyStatementReportService) {
        this(null, legacyStatementReportService, new PdfStatementService());
    }

    public ReportController(StatementExportService statementExportService, StatementReportService legacyStatementReportService) {
        this(statementExportService, legacyStatementReportService, new PdfStatementService());
    }

    public ReportController(StatementExportService statementExportService,
                            StatementReportService legacyStatementReportService,
                            PdfStatementService pdfStatementService) {
        this.statementExportService = statementExportService;
        this.legacyStatementReportService = legacyStatementReportService;
        this.pdfStatementService = (pdfStatementService != null) ? pdfStatementService : new PdfStatementService();
    }

    /**
     * Generates a single transaction receipt voucher PDF and returns the path to the saved file.
     */
    public String generateReceipt(Transaction transaction, AccountDTO account, User user) {
        if (pdfStatementService != null) {
            return pdfStatementService.generateReceipt(transaction, account, user);
        }
        throw new IllegalStateException("No PDF statement service configured in ReportController");
    }

    public File generateTransactionReceiptPdf(Transaction transaction, AccountDTO account, User user) {
        if (pdfStatementService != null) {
            return pdfStatementService.generateTransactionReceiptPdf(transaction, account, user);
        }
        throw new IllegalStateException("No PDF statement service configured in ReportController");
    }

    public File generateTransactionReceiptPdf(Transaction transaction, Account account, User user) {
        if (pdfStatementService != null) {
            return pdfStatementService.generateTransactionReceiptPdf(transaction, account, user);
        }
        throw new IllegalStateException("No PDF statement service configured in ReportController");
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

    public PdfStatementService getPdfStatementService() {
        return pdfStatementService;
    }
}
