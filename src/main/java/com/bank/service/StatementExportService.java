package com.bank.service;

import com.bank.model.dto.AccountDTO;
import com.bank.model.dto.StatementReportData;
import com.bank.model.dto.StatementTransactionItem;
import com.bank.model.entity.Transaction;
import com.bank.model.entity.User;
import com.bank.model.enums.TransactionType;
import com.bank.model.repository.TransactionRepository;
import com.bank.util.AuditCryptoUtil;
import com.lowagie.text.*;
import com.lowagie.text.Font;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.*;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Enterprise-Grade PDF Account Statement Generator using OpenPDF.
 * Strictly adheres to official banking statement standards:
 * - Clean visual hierarchy and typography
 * - Dual account identification and balance consolidation cards
 * - Detailed transaction ledger with audit codes and memos
 * - Deterministic SHA-256 cryptographic audit integrity fingerprint
 */
public class StatementExportService {

    private static final String OUTPUT_DIR = "statements";

    // Corporate Color Palette matching DigiBank Visual Standards
    private static final Color COLOR_PRIMARY_NAVY = new Color(11, 25, 44);   // #0B192C
    private static final Color COLOR_ACCENT_BLUE   = new Color(2, 132, 199);  // #0284C7
    private static final Color COLOR_DARK_TEXT     = new Color(15, 23, 42);   // #0F172A
    private static final Color COLOR_MUTED_TEXT    = new Color(100, 116, 139);// #64748B
    private static final Color COLOR_LIGHT_BORDER  = new Color(226, 232, 240);// #E2E8F0
    private static final Color COLOR_ROW_ALT       = new Color(248, 250, 252);// #F8FAFC
    private static final Color COLOR_DEBIT_RED     = new Color(220, 38, 38);  // #DC2626
    private static final Color COLOR_CREDIT_GREEN  = new Color(22, 163, 74);  // #16A34A
    private static final Color COLOR_CYAN_LOGO     = new Color(56, 189, 248);  // #38BDF8

    private final TransactionRepository transactionRepository;

    public StatementExportService(TransactionRepository transactionRepository) {
        this.transactionRepository = transactionRepository;
    }

    /**
     * Generates a PDF statement on disk for the given User and AccountDTO,
     * returning the relative output path.
     */
    public String generateStatement(User user, AccountDTO account, LocalDateTime from, LocalDateTime to) {
        StatementReportData reportData = prepareReportData(user, account, from, to);
        return saveStatementToFile(reportData, account.getAccountNumber());
    }

    /**
     * Overload for generating statement with raw customer name (for legacy controller compatibility).
     */
    public String generateStatement(String customerName, AccountDTO account, LocalDateTime from, LocalDateTime to) {
        StatementReportData reportData = prepareReportData(customerName, null, account, from, to);
        return saveStatementToFile(reportData, account.getAccountNumber());
    }

    /**
     * Generates and returns the raw PDF bytes for a populated StatementReportData.
     */
    public byte[] generateStatementPdfBytes(StatementReportData data) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        Document document = new Document(PageSize.A4, 28, 28, 28, 28);

        try {
            PdfWriter.getInstance(document, baos);
            document.open();

            // 1. Header Section
            document.add(buildHeaderSection(data));
            document.add(buildDivider(1f, COLOR_PRIMARY_NAVY, 4f, 10f));

            // 2. Summary Dual-Cards (Account Holder & Balance Consolidation)
            document.add(buildSummaryCards(data));
            document.add(new Paragraph(" ", FontFactory.getFont(FontFactory.HELVETICA, 4)));

            // 3. Transaction Posting Ledger Table
            document.add(buildTransactionTable(data));

            // 4. Regulatory Disclosures & SHA-256 Audit Integrity
            document.add(buildFooterAndAuditSection(data));

            document.close();
            return baos.toByteArray();
        } catch (DocumentException e) {
            throw new RuntimeException("Error rendering PDF statement", e);
        }
    }

    /**
     * Prepares report data by querying transactions, calculating ledger balances,
     * formatting memos/codes, and generating the SHA-256 audit fingerprint.
     */
    public StatementReportData prepareReportData(User user, AccountDTO account, LocalDateTime from, LocalDateTime to) {
        String customerName = (user != null && user.getFullName() != null) ? user.getFullName() : "VALUED CUSTOMER";
        Long userId = (user != null) ? user.getUserId() : null;
        return prepareReportData(customerName, userId, account, from, to);
    }

    public StatementReportData prepareReportData(String customerName, Long userId, AccountDTO account, LocalDateTime from, LocalDateTime to) {
        List<Transaction> dbTransactions = (transactionRepository != null && account.getAccountId() != null)
                ? transactionRepository.findByAccountIdAndDateRange(account.getAccountId(), from, to)
                : new ArrayList<>();

        String customerIdStr = (userId != null)
                ? String.format("#USR-%02d", userId)
                : "#USR-01";

        String currencyCode = (account.getCurrency() != null) ? account.getCurrency().name() : "USD";
        String currencySymbol = "KHR".equalsIgnoreCase(currencyCode) ? "KHR " : "$ ";

        // Compute net credits and debits
        BigDecimal totalCredits = BigDecimal.ZERO;
        int creditCount = 0;
        BigDecimal totalDebits = BigDecimal.ZERO;
        int debitCount = 0;

        List<StatementTransactionItem> items = new ArrayList<>();

        for (Transaction t : dbTransactions) {
            boolean isCredit = isCreditTransaction(t, account.getAccountId());
            BigDecimal amt = t.getAmount() != null ? t.getAmount().abs() : BigDecimal.ZERO;

            BigDecimal debAmt = null;
            BigDecimal credAmt = null;
            if (isCredit) {
                credAmt = amt;
                totalCredits = totalCredits.add(amt);
                creditCount++;
            } else {
                debAmt = amt;
                totalDebits = totalDebits.add(amt);
                debitCount++;
            }

            LocalDate postDate = t.getTransactionDate() != null
                    ? t.getTransactionDate().toLocalDate()
                    : LocalDate.now();

            String code = deriveTransactionCode(t, isCredit);
            String title = deriveTransactionTitle(t, isCredit);
            String memo = deriveTransactionMemo(t, isCredit);

            items.add(StatementTransactionItem.builder()
                    .postingDate(postDate)
                    .valueDate(postDate)
                    .title(title)
                    .memo(memo)
                    .code(code)
                    .debitAmount(debAmt)
                    .creditAmount(credAmt)
                    .runningBalance(BigDecimal.ZERO) // Calculated below
                    .build());
        }

        // Ledger Balance Math:
        // Opening + Credits - Debits = Closing
        BigDecimal closingBalance = (account.getBalance() != null) ? account.getBalance() : BigDecimal.ZERO;
        BigDecimal openingBalance = closingBalance.subtract(totalCredits).add(totalDebits);

        // Compute running balances forwards
        BigDecimal running = openingBalance;
        for (StatementTransactionItem item : items) {
            if (item.getCreditAmount() != null) {
                running = running.add(item.getCreditAmount());
            }
            if (item.getDebitAmount() != null) {
                running = running.subtract(item.getDebitAmount());
            }
            item.setRunningBalance(running);
        }

        LocalDate stmtDate = LocalDate.now();
        LocalDate startDate = (from != null) ? from.toLocalDate() : stmtDate.minusDays(30);
        LocalDate endDate = (to != null) ? to.toLocalDate() : stmtDate;

        String accountStructure = (account.getAccountType() != null)
                ? formatAccountType(account.getAccountType().name())
                : "Checking Account";

        StatementReportData data = StatementReportData.builder()
                .customerName(customerName)
                .customerId(customerIdStr)
                .assignedBranch("Head Office")
                .accountNumber(account.getAccountNumber() != null ? account.getAccountNumber() : "DGB-000000000")
                .accountStructure(accountStructure)
                .currency(currencyCode)
                .currencySymbol(currencySymbol)
                .standingStatus("ACTIVE / Cleared")
                .statementDate(stmtDate)
                .startDate(startDate)
                .endDate(endDate)
                .openingBalance(openingBalance)
                .closingBalance(closingBalance)
                .totalCredits(totalCredits)
                .creditCount(creditCount)
                .totalDebits(totalDebits)
                .debitCount(debitCount)
                .transactions(items)
                .build();

        // Calculate and embed cryptographic audit fingerprint
        String hash = AuditCryptoUtil.calculateLedgerHash(data);
        data.setSha256Hash(hash);

        return data;
    }

    private String saveStatementToFile(StatementReportData reportData, String accountNumber) {
        byte[] pdfBytes = generateStatementPdfBytes(reportData);

        File dir = new File(OUTPUT_DIR);
        if (!dir.exists()) {
            dir.mkdirs();
        }

        String safeAcc = (accountNumber != null) ? accountNumber : "account";
        String fileName = "statement_" + safeAcc + "_" + System.currentTimeMillis() + ".pdf";
        File outputFile = new File(dir, fileName);

        try (FileOutputStream fos = new FileOutputStream(outputFile)) {
            fos.write(pdfBytes);
        } catch (IOException e) {
            throw new RuntimeException("Failed to write PDF file: " + outputFile.getAbsolutePath(), e);
        }

        return outputFile.getPath();
    }

    // =========================================================================
    // OpenPDF Component Builders
    // =========================================================================

    private Element buildHeaderSection(StatementReportData data) throws DocumentException {
        PdfPTable headerTable = new PdfPTable(2);
        headerTable.setWidthPercentage(100);
        headerTable.setWidths(new float[]{62f, 38f});

        // Left Cell: Logo Badge + Corporate Identity
        PdfPCell leftCell = new PdfPCell();
        leftCell.setBorder(Rectangle.NO_BORDER);
        leftCell.setPadding(0);

        PdfPTable brandTable = new PdfPTable(2);
        brandTable.setWidthPercentage(100);
        brandTable.setWidths(new float[]{9f, 91f});

        // Logo Badge
        PdfPCell badgeCell = new PdfPCell();
        badgeCell.setBorder(Rectangle.NO_BORDER);
        badgeCell.setFixedHeight(28f);
        badgeCell.setCellEvent(new LogoCellEvent());
        badgeCell.setPadding(0);
        brandTable.addCell(badgeCell);

        // Corporate Metadata
        PdfPCell titleCell = new PdfPCell();
        titleCell.setBorder(Rectangle.NO_BORDER);
        titleCell.setPaddingLeft(6f);

        Font nameFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 16, COLOR_PRIMARY_NAVY);
        Paragraph brandName = new Paragraph("DIGIBANK", nameFont);
        brandName.setLeading(16f);
        titleCell.addElement(brandName);

        Font metaFont = FontFactory.getFont(FontFactory.HELVETICA, 7.5f, COLOR_MUTED_TEXT);
        Paragraph metaLine1 = new Paragraph("National Bank of Cambodia Reg: #NBC-2024-88 • SWIFT: DGBKKHPP", metaFont);
        metaLine1.setLeading(9f);
        titleCell.addElement(metaLine1);

        Paragraph metaLine2 = new Paragraph("Head Office: 242 Monivong Blvd, Phnom Penh, Kingdom of Cambodia", metaFont);
        metaLine2.setLeading(9f);
        titleCell.addElement(metaLine2);

        brandTable.addCell(titleCell);
        leftCell.addElement(brandTable);
        headerTable.addCell(leftCell);

        // Right Cell: Statement of Account Title & Period Metadata
        PdfPCell rightCell = new PdfPCell();
        rightCell.setBorder(Rectangle.NO_BORDER);
        rightCell.setPadding(0);
        rightCell.setHorizontalAlignment(Element.ALIGN_RIGHT);

        Font titleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 14, COLOR_ACCENT_BLUE);
        Paragraph stmtTitle = new Paragraph("STATEMENT OF ACCOUNT", titleFont);
        stmtTitle.setAlignment(Element.ALIGN_RIGHT);
        stmtTitle.setLeading(14f);
        rightCell.addElement(stmtTitle);

        Font periodFont = FontFactory.getFont(FontFactory.COURIER, 8f, COLOR_MUTED_TEXT);
        String periodText = String.format("Period: %s - %s",
                data.getStartDate() != null ? data.getStartDate() : "",
                data.getEndDate() != null ? data.getEndDate() : "");
        Paragraph periodPara = new Paragraph(periodText, periodFont);
        periodPara.setAlignment(Element.ALIGN_RIGHT);
        periodPara.setLeading(11f);
        rightCell.addElement(periodPara);

        Font curFont = FontFactory.getFont(FontFactory.HELVETICA, 8f, COLOR_MUTED_TEXT);
        String curText = String.format("Currency: %s (%s)  |  Page 1 of 1",
                data.getCurrency() != null ? data.getCurrency() : "USD",
                data.getCurrencySymbol() != null ? data.getCurrencySymbol().trim() : "$");
        Paragraph curPara = new Paragraph(curText, curFont);
        curPara.setAlignment(Element.ALIGN_RIGHT);
        curPara.setLeading(10f);
        rightCell.addElement(curPara);

        headerTable.addCell(rightCell);
        return headerTable;
    }

    private Element buildSummaryCards(StatementReportData data) throws DocumentException {
        PdfPTable cardsTable = new PdfPTable(2);
        cardsTable.setWidthPercentage(100);
        cardsTable.setWidths(new float[]{49.5f, 49.5f});
        cardsTable.setSpacingBefore(4f);
        cardsTable.setSpacingAfter(4f);

        // Left Card: ACCOUNT HOLDER
        PdfPCell leftCard = new PdfPCell();
        leftCard.setBorder(Rectangle.NO_BORDER);
        leftCard.setCellEvent(new RoundedCellEvent(COLOR_LIGHT_BORDER, Color.WHITE, 6f));
        leftCard.setPadding(9f);

        Font cardHeaderFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8f, COLOR_MUTED_TEXT);
        Paragraph leftTitle = new Paragraph("ACCOUNT HOLDER", cardHeaderFont);
        leftTitle.setLeading(9f);
        leftCard.addElement(leftTitle);

        Font custNameFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10.5f, COLOR_PRIMARY_NAVY);
        Paragraph custPara = new Paragraph(data.getCustomerName() != null ? data.getCustomerName() : "CUSTOMER", custNameFont);
        custPara.setLeading(13f);
        leftCard.addElement(custPara);

        Font subFont = FontFactory.getFont(FontFactory.HELVETICA, 7.5f, COLOR_MUTED_TEXT);
        String subText = String.format("Client %s • Assigned: %s",
                data.getCustomerId() != null ? data.getCustomerId() : "#USR-01",
                data.getAssignedBranch() != null ? data.getAssignedBranch() : "Head Office");
        Paragraph subPara = new Paragraph(subText, subFont);
        subPara.setLeading(10f);
        leftCard.addElement(subPara);

        leftCard.addElement(buildDivider(0.5f, COLOR_LIGHT_BORDER, 4f, 5f));

        // Details grid inside left card
        PdfPTable leftGrid = new PdfPTable(2);
        leftGrid.setWidthPercentage(100);
        leftGrid.setWidths(new float[]{42f, 58f});

        addCardFieldRow(leftGrid, "Account Number :", data.getAccountNumber(), true, false);
        addCardFieldRow(leftGrid, "Account Structure :", data.getAccountStructure(), false, false);
        addCardFieldRow(leftGrid, "Status / Standing :", data.getStandingStatus(), false, false);
        addCardFieldRow(leftGrid, "Statement Date :",
                data.getStatementDate() != null ? data.getStatementDate().toString() : "", false, false);

        leftCard.addElement(leftGrid);
        cardsTable.addCell(leftCard);

        // Right Card: BALANCE CONSOLIDATION
        PdfPCell rightCard = new PdfPCell();
        rightCard.setBorder(Rectangle.NO_BORDER);
        rightCard.setCellEvent(new RoundedCellEvent(COLOR_LIGHT_BORDER, Color.WHITE, 6f));
        rightCard.setPadding(9f);

        Paragraph rightTitle = new Paragraph("BALANCE CONSOLIDATION", cardHeaderFont);
        rightTitle.setLeading(9f);
        rightCard.addElement(rightTitle);

        Paragraph spacer = new Paragraph(" ", FontFactory.getFont(FontFactory.HELVETICA, 4f));
        rightCard.addElement(spacer);

        PdfPTable rightGrid = new PdfPTable(2);
        rightGrid.setWidthPercentage(100);
        rightGrid.setWidths(new float[]{55f, 45f});

        DecimalFormat df = new DecimalFormat("#,##0.00");
        String sym = data.getCurrencySymbol() != null ? data.getCurrencySymbol().trim() + " " : "$ ";

        String openStr = sym + df.format(data.getOpeningBalance() != null ? data.getOpeningBalance() : BigDecimal.ZERO);
        addCardFieldRow(rightGrid, "Opening Ledger Balance :", openStr, true, true);

        String credStr = "+ " + sym + df.format(data.getTotalCredits() != null ? data.getTotalCredits() : BigDecimal.ZERO);
        String credLabel = String.format("Total Deposits / Credits (%d) :", data.getCreditCount());
        addCardFieldRow(rightGrid, credLabel, credStr, true, true);

        String debStr = "- " + sym + df.format(data.getTotalDebits() != null ? data.getTotalDebits() : BigDecimal.ZERO);
        String debLabel = String.format("Total Payments / Debits (%d) :", data.getDebitCount());
        addCardFieldRow(rightGrid, debLabel, debStr, true, true);

        rightCard.addElement(rightGrid);
        rightCard.addElement(buildDivider(0.5f, COLOR_LIGHT_BORDER, 3f, 4f));

        // Closing Balance Row
        PdfPTable closeGrid = new PdfPTable(2);
        closeGrid.setWidthPercentage(100);
        closeGrid.setWidths(new float[]{50f, 50f});

        PdfPCell cLabelCell = new PdfPCell(new Phrase("Closing Cleared Balance :", FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8.5f, COLOR_PRIMARY_NAVY)));
        cLabelCell.setBorder(Rectangle.NO_BORDER);
        cLabelCell.setPadding(1f);
        closeGrid.addCell(cLabelCell);

        String closeStr = sym + df.format(data.getClosingBalance() != null ? data.getClosingBalance() : BigDecimal.ZERO) + " " + data.getCurrency();
        PdfPCell cValCell = new PdfPCell(new Phrase(closeStr, FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9.5f, COLOR_ACCENT_BLUE)));
        cValCell.setBorder(Rectangle.NO_BORDER);
        cValCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
        cValCell.setPadding(1f);
        closeGrid.addCell(cValCell);

        rightCard.addElement(closeGrid);
        cardsTable.addCell(rightCard);

        return cardsTable;
    }

    private void addCardFieldRow(PdfPTable table, String label, String value, boolean isMonospace, boolean alignRight) {
        Font labelFont = FontFactory.getFont(FontFactory.HELVETICA, 7.5f, COLOR_MUTED_TEXT);
        PdfPCell labelCell = new PdfPCell(new Phrase(label, labelFont));
        labelCell.setBorder(Rectangle.NO_BORDER);
        labelCell.setPaddingTop(1.5f);
        labelCell.setPaddingBottom(1.5f);
        labelCell.setPaddingLeft(0);
        table.addCell(labelCell);

        Font valFont = isMonospace
                ? FontFactory.getFont(FontFactory.COURIER_BOLD, 8f, COLOR_DARK_TEXT)
                : FontFactory.getFont(FontFactory.HELVETICA_BOLD, 7.5f, COLOR_DARK_TEXT);

        PdfPCell valCell = new PdfPCell(new Phrase(value != null ? value : "", valFont));
        valCell.setBorder(Rectangle.NO_BORDER);
        valCell.setPaddingTop(1.5f);
        valCell.setPaddingBottom(1.5f);
        valCell.setPaddingRight(0);
        if (alignRight) {
            valCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
        }
        table.addCell(valCell);
    }

    private Element buildTransactionTable(StatementReportData data) throws DocumentException {
        PdfPTable table = new PdfPTable(6);
        table.setWidthPercentage(100);
        table.setWidths(new float[]{12.5f, 43.5f, 10f, 11f, 11f, 12f});
        table.setHeaderRows(1);
        table.setSpacingBefore(6f);

        // Header Cells
        String[] headers = {
                "POSTING DATE",
                "TRANSACTION PARTICULARS / AUDIT MEMO",
                "CODE",
                "DEBIT (-)",
                "CREDIT (+)",
                "BALANCE"
        };
        int[] alignments = {
                Element.ALIGN_LEFT,
                Element.ALIGN_LEFT,
                Element.ALIGN_CENTER,
                Element.ALIGN_RIGHT,
                Element.ALIGN_RIGHT,
                Element.ALIGN_RIGHT
        };

        Font headerFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 7.5f, Color.WHITE);

        for (int i = 0; i < headers.length; i++) {
            PdfPCell hCell = new PdfPCell(new Phrase(headers[i], headerFont));
            hCell.setBackgroundColor(COLOR_PRIMARY_NAVY);
            hCell.setHorizontalAlignment(alignments[i]);
            hCell.setVerticalAlignment(Element.ALIGN_MIDDLE);
            hCell.setPaddingTop(6f);
            hCell.setPaddingBottom(6f);
            hCell.setPaddingLeft(4f);
            hCell.setPaddingRight(4f);
            hCell.setBorder(Rectangle.NO_BORDER);
            table.addCell(hCell);
        }

        DecimalFormat df = new DecimalFormat("#,##0.00");
        String sym = data.getCurrencySymbol() != null ? data.getCurrencySymbol().trim() + " " : "$ ";

        List<StatementTransactionItem> items = data.getTransactions();
        if (items == null || items.isEmpty()) {
            PdfPCell emptyCell = new PdfPCell(new Phrase("No transactions recorded during this statement cycle.",
                    FontFactory.getFont(FontFactory.HELVETICA, 8f, COLOR_MUTED_TEXT)));
            emptyCell.setColspan(6);
            emptyCell.setHorizontalAlignment(Element.ALIGN_CENTER);
            emptyCell.setPadding(16f);
            emptyCell.setBorder(Rectangle.BOTTOM);
            emptyCell.setBorderColor(COLOR_LIGHT_BORDER);
            table.addCell(emptyCell);
            return table;
        }

        for (int row = 0; row < items.size(); row++) {
            StatementTransactionItem item = items.get(row);
            Color rowBg = (row % 2 == 1) ? COLOR_ROW_ALT : Color.WHITE;

            // 1. Posting Date
            PdfPCell dateCell = new PdfPCell(new Phrase(item.getPostingDate() != null ? item.getPostingDate().toString() : "",
                    FontFactory.getFont(FontFactory.COURIER, 7.5f, COLOR_DARK_TEXT)));
            styleBodyCell(dateCell, rowBg, Element.ALIGN_LEFT);
            table.addCell(dateCell);

            // 2. Particulars & Audit Memo
            PdfPCell descCell = new PdfPCell();
            styleBodyCell(descCell, rowBg, Element.ALIGN_LEFT);

            Font titleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 7.5f, COLOR_DARK_TEXT);
            Paragraph titlePara = new Paragraph(item.getTitle() != null ? item.getTitle() : "Banking Transaction", titleFont);
            titlePara.setLeading(9f);
            descCell.addElement(titlePara);

            if (item.getMemo() != null && !item.getMemo().isEmpty()) {
                Font memoFont = FontFactory.getFont(FontFactory.HELVETICA, 6.5f, COLOR_MUTED_TEXT);
                Paragraph memoPara = new Paragraph(item.getMemo(), memoFont);
                memoPara.setLeading(8f);
                descCell.addElement(memoPara);
            }
            table.addCell(descCell);

            // 3. Transaction Code
            PdfPCell codeCell = new PdfPCell(new Phrase(item.getCode() != null ? item.getCode() : "TX",
                    FontFactory.getFont(FontFactory.COURIER_BOLD, 7.5f, COLOR_MUTED_TEXT)));
            styleBodyCell(codeCell, rowBg, Element.ALIGN_CENTER);
            table.addCell(codeCell);

            // 4. Debit (-)
            String debText = item.getDebitAmount() != null
                    ? sym + df.format(item.getDebitAmount())
                    : "-";
            Font debFont = (item.getDebitAmount() != null)
                    ? FontFactory.getFont(FontFactory.COURIER_BOLD, 7.5f, COLOR_DEBIT_RED)
                    : FontFactory.getFont(FontFactory.COURIER, 7.5f, COLOR_MUTED_TEXT);
            PdfPCell debCell = new PdfPCell(new Phrase(debText, debFont));
            styleBodyCell(debCell, rowBg, Element.ALIGN_RIGHT);
            table.addCell(debCell);

            // 5. Credit (+)
            String credText = item.getCreditAmount() != null
                    ? sym + df.format(item.getCreditAmount())
                    : "-";
            Font credFont = (item.getCreditAmount() != null)
                    ? FontFactory.getFont(FontFactory.COURIER_BOLD, 7.5f, COLOR_CREDIT_GREEN)
                    : FontFactory.getFont(FontFactory.COURIER, 7.5f, COLOR_MUTED_TEXT);
            PdfPCell credCell = new PdfPCell(new Phrase(credText, credFont));
            styleBodyCell(credCell, rowBg, Element.ALIGN_RIGHT);
            table.addCell(credCell);

            // 6. Running Balance
            String balText = sym + df.format(item.getRunningBalance() != null ? item.getRunningBalance() : BigDecimal.ZERO);
            PdfPCell balCell = new PdfPCell(new Phrase(balText,
                    FontFactory.getFont(FontFactory.COURIER_BOLD, 7.5f, COLOR_DARK_TEXT)));
            styleBodyCell(balCell, rowBg, Element.ALIGN_RIGHT);
            table.addCell(balCell);
        }

        return table;
    }

    private void styleBodyCell(PdfPCell cell, Color bg, int alignment) {
        cell.setBackgroundColor(bg);
        cell.setHorizontalAlignment(alignment);
        cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        cell.setPaddingTop(5f);
        cell.setPaddingBottom(5f);
        cell.setPaddingLeft(4f);
        cell.setPaddingRight(4f);
        cell.setBorder(Rectangle.BOTTOM);
        cell.setBorderColor(COLOR_LIGHT_BORDER);
        cell.setBorderWidth(0.5f);
    }

    private Element buildFooterAndAuditSection(StatementReportData data) throws DocumentException {
        PdfPTable footerWrapper = new PdfPTable(1);
        footerWrapper.setWidthPercentage(100);
        footerWrapper.setSpacingBefore(12f);

        // 1. Regulatory Disclosure Paragraph
        PdfPCell regCell = new PdfPCell();
        regCell.setBorder(Rectangle.NO_BORDER);
        regCell.setPadding(0);

        Font regHeaderFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 7.5f, COLOR_DARK_TEXT);
        Paragraph regTitle = new Paragraph("REGULATORY NOTICE & AUDIT COMPLIANCE:", regHeaderFont);
        regTitle.setLeading(9f);
        regCell.addElement(regTitle);

        Font regBodyFont = FontFactory.getFont(FontFactory.HELVETICA, 6.5f, COLOR_MUTED_TEXT);
        Paragraph regBody1 = new Paragraph("Please examine this statement immediately. In case of discrepancy, notify DigiBank Audit Compliance within 30 days of the generation date.", regBodyFont);
        regBody1.setLeading(8f);
        regCell.addElement(regBody1);

        Paragraph regBody2 = new Paragraph("Deposits are covered under National Bank of Cambodia Reserve mandates. Non-transferable legal document of record.", regBodyFont);
        regBody2.setLeading(8f);
        regCell.addElement(regBody2);

        footerWrapper.addCell(regCell);

        // Spacing before crypto box
        PdfPCell spacerCell = new PdfPCell(new Phrase(" ", FontFactory.getFont(FontFactory.HELVETICA, 4f)));
        spacerCell.setBorder(Rectangle.NO_BORDER);
        footerWrapper.addCell(spacerCell);

        // 2. Cryptographic Proof of Integrity Box
        PdfPCell cryptoCard = new PdfPCell();
        cryptoCard.setBorder(Rectangle.NO_BORDER);
        cryptoCard.setCellEvent(new RoundedCellEvent(COLOR_LIGHT_BORDER, COLOR_ROW_ALT, 5f));
        cryptoCard.setPadding(8f);

        PdfPTable cryptoInner = new PdfPTable(2);
        cryptoInner.setWidthPercentage(100);
        cryptoInner.setWidths(new float[]{72f, 28f});

        // Left: Label + SHA-256 Hash
        PdfPCell leftCrypto = new PdfPCell();
        leftCrypto.setBorder(Rectangle.NO_BORDER);
        leftCrypto.setPadding(0);

        Font cryptoLabelFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 7f, COLOR_DARK_TEXT);
        Paragraph cryptoLabel = new Paragraph("CRYPTOGRAPHIC PROOF OF RECORD INTEGRITY (SHA-256):", cryptoLabelFont);
        cryptoLabel.setLeading(8f);
        leftCrypto.addElement(cryptoLabel);

        Font hashFont = FontFactory.getFont(FontFactory.COURIER_BOLD, 7.5f, COLOR_ACCENT_BLUE);
        Paragraph hashPara = new Paragraph(data.getSha256Hash() != null ? data.getSha256Hash() : "", hashFont);
        hashPara.setLeading(9f);
        leftCrypto.addElement(hashPara);
        cryptoInner.addCell(leftCrypto);

        // Right: DIGIBANK LEDGER ENGINE badge
        PdfPCell rightCrypto = new PdfPCell();
        rightCrypto.setBorder(Rectangle.NO_BORDER);
        rightCrypto.setPadding(0);
        rightCrypto.setHorizontalAlignment(Element.ALIGN_RIGHT);
        rightCrypto.setVerticalAlignment(Element.ALIGN_MIDDLE);

        Font engineFont = FontFactory.getFont(FontFactory.COURIER_BOLD, 8f, COLOR_PRIMARY_NAVY);
        Paragraph enginePara = new Paragraph("DIGIBANK LEDGER ENGINE", engineFont);
        enginePara.setAlignment(Element.ALIGN_RIGHT);
        rightCrypto.addElement(enginePara);
        cryptoInner.addCell(rightCrypto);

        cryptoCard.addElement(cryptoInner);
        footerWrapper.addCell(cryptoCard);

        return footerWrapper;
    }

    private Element buildDivider(float height, Color color, float spaceBefore, float spaceAfter) {
        PdfPTable divTable = new PdfPTable(1);
        divTable.setWidthPercentage(100);
        divTable.setSpacingBefore(spaceBefore);
        divTable.setSpacingAfter(spaceAfter);

        PdfPCell divCell = new PdfPCell();
        divCell.setFixedHeight(height);
        divCell.setBackgroundColor(color);
        divCell.setBorder(Rectangle.NO_BORDER);
        divTable.addCell(divCell);

        return divTable;
    }

    // =========================================================================
    // Helper Business Logic
    // =========================================================================

    private boolean isCreditTransaction(Transaction t, Long accountId) {
        if (t.getTransactionType() == TransactionType.DEPOSIT ||
                t.getTransactionType() == TransactionType.LOAN_DISBURSEMENT) {
            return true;
        }
        if (t.getTransactionType() == TransactionType.TRANSFER) {
            // Outgoing if accountId is the primary sender, Incoming if related receiver
            return !accountId.equals(t.getAccountId()) && accountId.equals(t.getRelatedAccountId());
        }
        return false;
    }

    private String deriveTransactionCode(Transaction t, boolean isCredit) {
        if (t.getDescription() != null) {
            String lower = t.getDescription().toLowerCase();
            if (lower.contains("wire")) return "WRE_IN";
            if (lower.contains("salary") || lower.contains("ach")) return "ACH_CR";
            if (lower.contains("atm") || lower.contains("cash")) return isCredit ? "CSH_DEP" : "CSH_WDL";
            if (lower.contains("edc") || lower.contains("bill") || lower.contains("utility")) return "BIL_PAY";
            if (lower.contains("internal") || lower.contains("transfer") || lower.contains("savings") || lower.contains("lease")) return "INT_TX";
        }
        if (t.getTransactionType() == null) return "TX";
        return switch (t.getTransactionType()) {
            case DEPOSIT -> "ACH_CR";
            case WITHDRAWAL -> "CSH_WDL";
            case TRANSFER -> "INT_TX";
            case PAYMENT -> "BIL_PAY";
            case LOAN_DISBURSEMENT -> "LN_DISB";
            case LOAN_REPAYMENT -> "LN_REPAY";
        };
    }

    private String deriveTransactionTitle(Transaction t, boolean isCredit) {
        if (t.getDescription() != null && !t.getDescription().trim().isEmpty()) {
            String desc = t.getDescription().trim();
            if (desc.contains("\n")) {
                return desc.substring(0, desc.indexOf("\n")).trim();
            }
            if (desc.contains("•")) {
                return desc.substring(0, desc.indexOf("•")).trim();
            }
            return desc;
        }

        if (t.getTransactionType() == null) return "Bank Transaction";
        return switch (t.getTransactionType()) {
            case DEPOSIT -> "ACH Direct Settlement";
            case WITHDRAWAL -> "ATM Cash Dispense";
            case TRANSFER -> isCredit ? "Inward Fund Transfer" : "Internal Allocation Transfer";
            case PAYMENT -> "Utility Direct Debit Settlement";
            case LOAN_DISBURSEMENT -> "Digital Loan Credit Facility";
            case LOAN_REPAYMENT -> "Installment Amortization Repayment";
        };
    }

    private String deriveTransactionMemo(Transaction t, boolean isCredit) {
        if (t.getDescription() != null && !t.getDescription().trim().isEmpty()) {
            String desc = t.getDescription().trim();
            if (desc.contains("\n")) {
                return desc.substring(desc.indexOf("\n") + 1).replace("\n", " • ").trim();
            }
            if (desc.contains("•")) {
                return desc.substring(desc.indexOf("•") + 1).trim();
            }
        }

        Long txId = t.getTransactionId() != null ? t.getTransactionId() : 1001L;
        String trace = String.format("#TX-%06d", txId);
        if (t.getIdempotencyKey() != null) {
            trace += " • Key: " + t.getIdempotencyKey().toString().substring(0, 8).toUpperCase();
        }

        if (t.getTransactionType() == null) return "Trace " + trace;
        return switch (t.getTransactionType()) {
            case DEPOSIT -> "Automated Clearing House • Trace " + trace;
            case WITHDRAWAL -> "Loc: Phnom Penh Gateway • Trace " + trace;
            case TRANSFER -> isCredit
                    ? "Beneficiary Credit • Trace " + trace
                    : "Outgoing Remittance • Trace " + trace;
            case PAYMENT -> "Merchant Direct Debit • Trace " + trace;
            case LOAN_DISBURSEMENT -> "Facility Ref #LN-APP-" + txId;
            case LOAN_REPAYMENT -> "Amortization Reference " + trace;
        };
    }

    private String formatAccountType(String rawType) {
        if (rawType == null) return "Checking Account";
        return switch (rawType.toUpperCase()) {
            case "SAVINGS" -> "Savings Account";
            case "CHECKING" -> "Checking Account";
            case "FIXED_DEPOSIT" -> "Fixed Term Deposit";
            default -> rawType;
        };
    }

    /**
     * CellEvent for rendering rounded rectangular cards with custom borders and background colors.
     */
    private static class RoundedCellEvent implements PdfPCellEvent {
        private final Color borderColor;
        private final Color bgColor;
        private final float radius;

        public RoundedCellEvent(Color borderColor, Color bgColor, float radius) {
            this.borderColor = borderColor;
            this.bgColor = bgColor;
            this.radius = radius;
        }

        @Override
        public void cellLayout(PdfPCell cell, Rectangle position, PdfContentByte[] canvases) {
            PdfContentByte cb = canvases[PdfPTable.BACKGROUNDCANVAS];
            cb.roundRectangle(
                    position.getLeft() + 1f,
                    position.getBottom() + 1f,
                    position.getWidth() - 2f,
                    position.getHeight() - 2f,
                    radius
            );
            if (bgColor != null && borderColor != null) {
                cb.setColorFill(bgColor);
                cb.setColorStroke(borderColor);
                cb.setLineWidth(0.75f);
                cb.fillStroke();
            } else if (bgColor != null) {
                cb.setColorFill(bgColor);
                cb.fill();
            } else if (borderColor != null) {
                cb.setColorStroke(borderColor);
                cb.setLineWidth(0.75f);
                cb.stroke();
            }
        }
    }

    /**
     * Custom CellEvent to draw the official DigiBank logo icon:
     * A navy rounded rectangle containing a centered cyan "D".
     */
    private static class LogoCellEvent implements PdfPCellEvent {
        @Override
        public void cellLayout(PdfPCell cell, Rectangle position, PdfContentByte[] canvases) {
            PdfContentByte bgCb = canvases[PdfPTable.BACKGROUNDCANVAS];
            float size = Math.min(position.getWidth(), position.getHeight());
            float x = position.getLeft() + (position.getWidth() - size) / 2f;
            float y = position.getBottom() + (position.getHeight() - size) / 2f;

            bgCb.roundRectangle(x, y, size, size, 5f);
            bgCb.setColorFill(COLOR_PRIMARY_NAVY);
            bgCb.fill();

            PdfContentByte textCb = canvases[PdfPTable.TEXTCANVAS];
            textCb.beginText();
            try {
                BaseFont bf = BaseFont.createFont(BaseFont.HELVETICA_BOLD, BaseFont.WINANSI, BaseFont.NOT_EMBEDDED);
                textCb.setFontAndSize(bf, size * 0.68f);
                textCb.setColorFill(COLOR_CYAN_LOGO);
                textCb.showTextAligned(Element.ALIGN_CENTER, "D", x + size / 2f, y + size * 0.24f, 0);
            } catch (Exception ignored) {
            } finally {
                textCb.endText();
            }
        }
    }
}
