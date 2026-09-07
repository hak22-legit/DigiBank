package com.bank.report;

import com.bank.model.dto.TransactionLineDTO;
import com.bank.model.dto.AccountDTO;
import com.bank.model.entity.Transaction;
import com.bank.model.repository.TransactionRepository;
import net.sf.jasperreports.engine.*;
import net.sf.jasperreports.engine.data.JRBeanCollectionDataSource;

import java.io.File;
import java.io.InputStream;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Generates PDF bank statements using JasperReports.
 * Kept separate from core banking services - report generation must never
 * touch or block financial transaction logic (see master prompt Phase 24 rule).
 */
public class StatementReportService {

    private static final String TEMPLATE_PATH = "/reports/BankStatement.jrxml";
    private static final String OUTPUT_DIR = "statements";

    private final TransactionRepository transactionRepository;

    public StatementReportService(TransactionRepository transactionRepository) {
        this.transactionRepository = transactionRepository;
    }

    /**
     * Generates a PDF statement for the given account and period.
     * Returns the path to the generated PDF file.
     */
    public String generateStatement(String customerName, AccountDTO account, LocalDateTime from, LocalDateTime to) {
        try {
            List<Transaction> transactions =
                    transactionRepository.findByAccountIdAndDateRange(account.getAccountId(), from, to);

            // Compute opening balance = current balance minus all txns in the period
            // (this is a simplification appropriate for the simulation; a fully
            // accurate ledger would track balance-as-of-date directly)
            BigDecimal netChange = transactions.stream()
                    .map(t -> signedAmount(t, account.getAccountId()))
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal closingBalance = account.getBalance();
            BigDecimal openingBalance = closingBalance.subtract(netChange);

            // Build the running-balance line items
            List<TransactionLineDTO> lines = new ArrayList<>();
            BigDecimal running = openingBalance;
            for (Transaction t : transactions) {
                running = running.add(signedAmount(t, account.getAccountId()));
                lines.add(new TransactionLineDTO(
                        t.getTransactionDate(),
                        t.getDescription(),
                        t.getTransactionType().toString(),
                        t.getAmount(),
                        running
                ));
            }

            Map<String, Object> parameters = new HashMap<>();
            parameters.put("CUSTOMER_NAME", customerName);
            parameters.put("ACCOUNT_NUMBER", account.getAccountNumber());
            parameters.put("STATEMENT_PERIOD",
                    from.format(DateTimeFormatter.ISO_LOCAL_DATE) + " to " + to.format(DateTimeFormatter.ISO_LOCAL_DATE));
            parameters.put("OPENING_BALANCE", openingBalance);
            parameters.put("CLOSING_BALANCE", closingBalance);

            try (InputStream templateStream = getClass().getResourceAsStream(TEMPLATE_PATH)) {
                if (templateStream == null) {
                    throw new RuntimeException("Statement template not found: " + TEMPLATE_PATH);
                }
                JasperReport report = JasperCompileManager.compileReport(templateStream);
                JRBeanCollectionDataSource dataSource = new JRBeanCollectionDataSource(lines);
                JasperPrint print = JasperFillManager.fillReport(report, parameters, dataSource);

                File outputDir = new File(OUTPUT_DIR);
                if (!outputDir.exists()) outputDir.mkdirs();

                String fileName = "statement_" + account.getAccountNumber() + "_" +
                        System.currentTimeMillis() + ".pdf";
                String outputPath = OUTPUT_DIR + File.separator + fileName;

                JasperExportManager.exportReportToPdfFile(print, outputPath);
                return outputPath;
            }
        } catch (JRException | java.io.IOException e) {
            throw new RuntimeException("Failed to generate statement PDF", e);
        }
    }

    /** TRANSFER rows: negative if this account is the sender, positive if the receiver. */
    private BigDecimal signedAmount(Transaction t, Long accountId) {
        boolean isOutgoing = t.getAccountId().equals(accountId) &&
                (t.getRelatedAccountId() != null || isDebitType(t.getTransactionType().toString()));
        return isOutgoing ? t.getAmount().negate() : t.getAmount();
    }

    private boolean isDebitType(String type) {
        return type.equals("WITHDRAWAL") || type.equals("TRANSFER") ||
                type.equals("LOAN_REPAYMENT");
    }
}