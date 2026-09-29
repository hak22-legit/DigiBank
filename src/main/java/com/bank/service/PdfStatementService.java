package com.bank.service;

import com.bank.console.screens.TransactionDetailsScreen;
import com.bank.model.dto.AccountDTO;
import com.bank.model.entity.Account;
import com.bank.model.entity.Transaction;
import com.bank.model.entity.User;
import com.bank.model.enums.Currency;
import com.bank.model.repository.AccountRepository;
import com.bank.util.AuditCryptoUtil;
import com.lowagie.text.*;
import com.lowagie.text.Font;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Optional;

/**
 * Enterprise-Grade Single Transaction Receipt and PDF Statement Service.
 * Generates compact, professional, single-voucher payment receipts with:
 * - Title: "DIGIBANK OFFICIAL TRANSACTION RECEIPT"
 * - Receipt Ref: transaction.getReferenceId() (e.g. TXN-20260928-00092)
 * - Status: COMPLETED / SETTLED
 * - Source & Destination: Account numbers & holder name
 * - Amount: Gross amount, Fee ($0.00), Net settled
 * - Audit & Ledger Memo with clean cross-currency formatting
 * - Generated timestamp & digital SHA-256 verification stamp
 * - Output File: "statements/receipt_" + transaction.getReferenceId() + ".pdf"
 */
public class PdfStatementService {
    private static final Logger logger = LoggerFactory.getLogger(PdfStatementService.class);

    private static final String OUTPUT_DIR = "statements";
    private static final DateTimeFormatter TS_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DecimalFormat DF = new DecimalFormat("#,##0.00");

    // Corporate Color Palette matching DigiBank Visual Standards
    private static final Color COLOR_PRIMARY_NAVY = new Color(11, 25, 44);   // #0B192C
    private static final Color COLOR_ACCENT_BLUE   = new Color(2, 132, 199);  // #0284C7
    private static final Color COLOR_DARK_TEXT     = new Color(15, 23, 42);   // #0F172A
    private static final Color COLOR_MUTED_TEXT    = new Color(100, 116, 139);// #64748B
    private static final Color COLOR_LIGHT_BORDER  = new Color(226, 232, 240);// #E2E8F0
    private static final Color COLOR_CARD_BG       = new Color(248, 250, 252);// #F8FAFC
    private static final Color COLOR_CREDIT_GREEN  = new Color(22, 163, 74);  // #16A34A
    private static final Color COLOR_CYAN_LOGO     = new Color(56, 189, 248);  // #38BDF8

    private final AccountRepository accountRepository;

    public PdfStatementService() {
        this(null);
    }

    public PdfStatementService(AccountRepository accountRepository) {
        this.accountRepository = accountRepository;
    }

    /**
     * Generates a compact, professional single-voucher receipt PDF for an Account entity.
     */
    public File generateTransactionReceiptPdf(Transaction transaction, Account account, User user) {
        String accNum = (account != null && account.getAccountNumber() != null) ? account.getAccountNumber() : "UNKNOWN";
        String accType = (account != null && account.getAccountType() != null) ? account.getAccountType().name() : "CHECKING";
        Currency ccy = (account != null && account.getCurrency() != null) ? account.getCurrency() : Currency.USD;
        return generateTransactionReceiptFile(transaction, accNum, accType, ccy, user);
    }

    /**
     * Overload for AccountDTO (used across console screens and controllers).
     */
    public File generateTransactionReceiptPdf(Transaction transaction, AccountDTO account, User user) {
        String accNum = (account != null && account.getAccountNumber() != null) ? account.getAccountNumber() : "UNKNOWN";
        String accType = (account != null && account.getAccountType() != null) ? account.getAccountType().name() : "CHECKING";
        Currency ccy = (account != null && account.getCurrency() != null) ? account.getCurrency() : Currency.USD;
        return generateTransactionReceiptFile(transaction, accNum, accType, ccy, user);
    }

    /**
     * Generates receipt and returns the relative path string (e.g. statements/receipt_TXN-20260928-00091.pdf).
     */
    public String generateReceipt(Transaction transaction, AccountDTO account, User user) {
        File file = generateTransactionReceiptPdf(transaction, account, user);
        return file.getPath();
    }

    private File generateTransactionReceiptFile(Transaction transaction, String accountNumber,
                                                String accountType, Currency currency, User user) {
        byte[] pdfBytes = generateTransactionReceiptPdfBytes(transaction, accountNumber, accountType, currency, user);

        File dir = new File(OUTPUT_DIR);
        if (!dir.exists()) {
            dir.mkdirs();
        }

        String refId = (transaction != null) ? transaction.getReferenceId() : "TXN-00000000-00000";
        String fileName = "receipt_" + refId + ".pdf";
        File outputFile = new File(dir, fileName);

        try (FileOutputStream fos = new FileOutputStream(outputFile)) {
            fos.write(pdfBytes);
        } catch (IOException e) {
            throw new RuntimeException("Failed to write receipt PDF file: " + outputFile.getAbsolutePath(), e);
        }

        return outputFile;
    }

    /**
     * Generates the raw PDF bytes for a single transaction receipt voucher.
     */
    public byte[] generateTransactionReceiptPdfBytes(Transaction transaction, String accountNumber,
                                                     String accountType, Currency currency, User user) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        Document document = new Document(PageSize.A4, 36, 36, 36, 36);

        try {
            PdfWriter.getInstance(document, baos);
            document.open();

            String refId = (transaction != null) ? transaction.getReferenceId() : "TXN-00000000-00000";
            String tsStr = (transaction != null && transaction.getTransactionDate() != null)
                    ? transaction.getTransactionDate().format(TS_FMT) + " UTC"
                    : LocalDateTime.now().format(TS_FMT) + " UTC";

            // 1. Header Section with Brand Badge & Title
            document.add(buildHeaderSection(refId, tsStr));
            document.add(buildDivider(1.5f, COLOR_PRIMARY_NAVY, 6f, 10f));

            // 2. Settlement Highlight Card (Amount, Fee, Status)
            document.add(buildFinancialSummaryCard(transaction, currency));
            document.add(new Paragraph(" ", FontFactory.getFont(FontFactory.HELVETICA, 6)));

            // 3. Origin & Destination Parties Card
            document.add(buildPartiesCard(transaction, accountNumber, accountType, currency, user));
            document.add(new Paragraph(" ", FontFactory.getFont(FontFactory.HELVETICA, 6)));

            // 4. Audit & Ledger Memo Card
            document.add(buildMemoCard(transaction));
            document.add(new Paragraph(" ", FontFactory.getFont(FontFactory.HELVETICA, 6)));

            // 5. Cryptographic Audit & Verification Stamp
            document.add(buildVerificationCard(transaction, refId, tsStr));

            document.close();
            return baos.toByteArray();
        } catch (DocumentException e) {
            throw new RuntimeException("Error rendering single transaction receipt PDF", e);
        }
    }

    // =========================================================================
    // OpenPDF Section Builders
    // =========================================================================

    private Element buildHeaderSection(String refId, String timestamp) throws DocumentException {
        PdfPTable headerTable = new PdfPTable(2);
        headerTable.setWidthPercentage(100);
        headerTable.setWidths(new float[]{55f, 45f});

        // Left Cell: Logo Badge + Corporate Identity
        PdfPCell leftCell = new PdfPCell();
        leftCell.setBorder(Rectangle.NO_BORDER);
        leftCell.setPadding(0);

        PdfPTable brandTable = new PdfPTable(2);
        brandTable.setWidthPercentage(100);
        brandTable.setWidths(new float[]{12f, 88f});

        PdfPCell badgeCell = new PdfPCell();
        badgeCell.setBorder(Rectangle.NO_BORDER);
        badgeCell.setFixedHeight(30f);
        badgeCell.setCellEvent(new LogoCellEvent());
        badgeCell.setPadding(0);
        brandTable.addCell(badgeCell);

        PdfPCell titleCell = new PdfPCell();
        titleCell.setBorder(Rectangle.NO_BORDER);
        titleCell.setPaddingLeft(8f);

        Font nameFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 17, COLOR_PRIMARY_NAVY);
        Paragraph brandName = new Paragraph("DIGIBANK", nameFont);
        brandName.setLeading(17f);
        titleCell.addElement(brandName);

        Font metaFont = FontFactory.getFont(FontFactory.HELVETICA, 7.5f, COLOR_MUTED_TEXT);
        Paragraph metaLine = new Paragraph("National Bank of Cambodia Reg: #NBC-2024-88 • SWIFT: DGBKKHPP", metaFont);
        metaLine.setLeading(10f);
        titleCell.addElement(metaLine);

        brandTable.addCell(titleCell);
        leftCell.addElement(brandTable);
        headerTable.addCell(leftCell);

        // Right Cell: Receipt Title & Identifier
        PdfPCell rightCell = new PdfPCell();
        rightCell.setBorder(Rectangle.NO_BORDER);
        rightCell.setPadding(0);
        rightCell.setHorizontalAlignment(Element.ALIGN_RIGHT);

        Font receiptTitleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 13f, COLOR_ACCENT_BLUE);
        Paragraph rTitle = new Paragraph("DIGIBANK OFFICIAL TRANSACTION RECEIPT", receiptTitleFont);
        rTitle.setAlignment(Element.ALIGN_RIGHT);
        rTitle.setLeading(14f);
        rightCell.addElement(rTitle);

        Font refFont = FontFactory.getFont(FontFactory.COURIER_BOLD, 9f, COLOR_PRIMARY_NAVY);
        Paragraph refPara = new Paragraph("Receipt Ref: " + refId, refFont);
        refPara.setAlignment(Element.ALIGN_RIGHT);
        refPara.setLeading(12f);
        rightCell.addElement(refPara);

        Font tsFont = FontFactory.getFont(FontFactory.HELVETICA, 8f, COLOR_MUTED_TEXT);
        Paragraph tsPara = new Paragraph("Settled: " + timestamp, tsFont);
        tsPara.setAlignment(Element.ALIGN_RIGHT);
        tsPara.setLeading(11f);
        rightCell.addElement(tsPara);

        headerTable.addCell(rightCell);
        return headerTable;
    }

    private Element buildFinancialSummaryCard(Transaction tx, Currency currency) throws DocumentException {
        PdfPTable card = new PdfPTable(1);
        card.setWidthPercentage(100);

        PdfPCell cell = new PdfPCell();
        cell.setBorder(Rectangle.NO_BORDER);
        cell.setCellEvent(new RoundedCellEvent(COLOR_LIGHT_BORDER, COLOR_CARD_BG, 6f));
        cell.setPadding(12f);

        PdfPTable content = new PdfPTable(3);
        content.setWidthPercentage(100);
        content.setWidths(new float[]{45f, 25f, 30f});

        // 1. Amount
        PdfPCell amtCell = new PdfPCell();
        amtCell.setBorder(Rectangle.NO_BORDER);

        Font labelFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8f, COLOR_MUTED_TEXT);
        amtCell.addElement(new Paragraph("SETTLEMENT AMOUNT", labelFont));

        BigDecimal amt = (tx != null && tx.getAmount() != null) ? tx.getAmount() : BigDecimal.ZERO;
        if (tx != null && tx.isCrossCurrency()) {
            if (currency == tx.getDestinationCurrency()) {
                amt = tx.getDestinationAmount();
            } else if (currency == tx.getCurrency()) {
                amt = tx.getAmount();
            }
        }
        String sym = (currency == Currency.KHR) ? "KHR " : "$ ";
        String amtFormatted = sym + DF.format(amt) + " " + currency;

        Font amtFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 16f, COLOR_PRIMARY_NAVY);
        Paragraph amtPara = new Paragraph(amtFormatted, amtFont);
        amtPara.setLeading(18f);
        amtCell.addElement(amtPara);
        content.addCell(amtCell);

        // 2. Fee
        PdfPCell feeCell = new PdfPCell();
        feeCell.setBorder(Rectangle.NO_BORDER);
        feeCell.addElement(new Paragraph("TRANSACTION FEE", labelFont));

        Font feeFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12f, COLOR_PRIMARY_NAVY);
        Paragraph feePara = new Paragraph(sym + "0.00 " + currency, feeFont);
        feePara.setLeading(15f);
        feeCell.addElement(feePara);

        Font feeSubFont = FontFactory.getFont(FontFactory.HELVETICA, 7.5f, COLOR_MUTED_TEXT);
        Paragraph feeSub = new Paragraph("DigiBank Zero-Fee Direct", feeSubFont);
        feeSub.setLeading(10f);
        feeCell.addElement(feeSub);
        content.addCell(feeCell);

        // 3. Status Badge
        PdfPCell statusCell = new PdfPCell();
        statusCell.setBorder(Rectangle.NO_BORDER);
        statusCell.setHorizontalAlignment(Element.ALIGN_RIGHT);

        Paragraph statusLbl = new Paragraph("EXECUTION STATUS", labelFont);
        statusLbl.setAlignment(Element.ALIGN_RIGHT);
        statusCell.addElement(statusLbl);

        String statusStr = (tx != null && tx.getStatus() != null) ? tx.getStatus().name() : "COMPLETED";
        Font statusFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12f, COLOR_CREDIT_GREEN);
        Paragraph statusPara = new Paragraph("✓ " + statusStr + " / SETTLED", statusFont);
        statusPara.setAlignment(Element.ALIGN_RIGHT);
        statusPara.setLeading(15f);
        statusCell.addElement(statusPara);

        Paragraph directPara = new Paragraph("Direct Automated Clearing", feeSubFont);
        directPara.setAlignment(Element.ALIGN_RIGHT);
        directPara.setLeading(10f);
        statusCell.addElement(directPara);
        content.addCell(statusCell);

        cell.addElement(content);
        card.addCell(cell);
        return card;
    }

    private Element buildPartiesCard(Transaction tx, String accountNumber, String accountType,
                                     Currency currency, User user) throws DocumentException {
        PdfPTable table = new PdfPTable(2);
        table.setWidthPercentage(100);
        table.setWidths(new float[]{50f, 50f});

        Font headerFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8.5f, COLOR_MUTED_TEXT);
        Font titleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10f, COLOR_PRIMARY_NAVY);
        Font bodyFont = FontFactory.getFont(FontFactory.HELVETICA, 8.5f, COLOR_DARK_TEXT);
        Font courierFont = FontFactory.getFont(FontFactory.COURIER_BOLD, 9.5f, COLOR_PRIMARY_NAVY);

        // Left Card: Originating Account
        PdfPCell leftCell = new PdfPCell();
        leftCell.setBorder(Rectangle.NO_BORDER);
        leftCell.setCellEvent(new RoundedCellEvent(COLOR_LIGHT_BORDER, Color.WHITE, 6f));
        leftCell.setPadding(10f);

        leftCell.addElement(new Paragraph("SOURCE / ORIGINATING ACCOUNT", headerFont));

        Paragraph srcAcc = new Paragraph(accountNumber + " (" + accountType + " - " + currency + ")", courierFont);
        srcAcc.setLeading(13f);
        leftCell.addElement(srcAcc);

        String holderName = (user != null && user.getFullName() != null) ? user.getFullName() : "VALUED DIGIBANK CLIENT";
        Paragraph holderPara = new Paragraph("Account Holder: " + holderName, titleFont);
        holderPara.setLeading(12f);
        leftCell.addElement(holderPara);

        String clientId = (user != null && user.getUserId() != null) ? String.format("#USR-%02d", user.getUserId()) : "#USR-01";
        Paragraph clientPara = new Paragraph("Client Dossier: " + clientId + " • Verified KYC Status", bodyFont);
        clientPara.setLeading(11f);
        leftCell.addElement(clientPara);
        table.addCell(leftCell);

        // Right Card: Destination / Beneficiary
        PdfPCell rightCell = new PdfPCell();
        rightCell.setBorder(Rectangle.NO_BORDER);
        rightCell.setCellEvent(new RoundedCellEvent(COLOR_LIGHT_BORDER, Color.WHITE, 6f));
        rightCell.setPadding(10f);

        rightCell.addElement(new Paragraph("DESTINATION / SETTLEMENT CHANNEL", headerFont));

        String destAccNum = resolveDestinationAccount(tx);
        Paragraph destAcc = new Paragraph(destAccNum, courierFont);
        destAcc.setLeading(13f);
        rightCell.addElement(destAcc);

        String typeLabel = (tx != null && tx.getTransactionType() != null) ? tx.getTransactionType().name() : "TRANSFER";
        Paragraph channelPara = new Paragraph("Operation: " + typeLabel + " Clearing", titleFont);
        channelPara.setLeading(12f);
        rightCell.addElement(channelPara);

        Paragraph clearingPara = new Paragraph("Clearing: NBC Direct Fast Settlement Network", bodyFont);
        clearingPara.setLeading(11f);
        rightCell.addElement(clearingPara);
        table.addCell(rightCell);

        return table;
    }

    private Element buildMemoCard(Transaction tx) {
        PdfPTable table = new PdfPTable(1);
        table.setWidthPercentage(100);

        PdfPCell cell = new PdfPCell();
        cell.setBorder(Rectangle.NO_BORDER);
        cell.setCellEvent(new RoundedCellEvent(COLOR_LIGHT_BORDER, COLOR_CARD_BG, 6f));
        cell.setPadding(10f);

        Font headerFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8.5f, COLOR_MUTED_TEXT);
        cell.addElement(new Paragraph("AUDIT & LEDGER MEMO", headerFont));

        String raw = (tx != null && tx.getDescription() != null && !tx.getDescription().isBlank())
                ? tx.getDescription().trim()
                : "Standard banking transaction processed via DigiBank core ledger.";
        String cleanMemo = TransactionDetailsScreen.formatAuditMemo(raw);

        Font memoFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9.5f, COLOR_PRIMARY_NAVY);
        Paragraph memoPara = new Paragraph("\"" + cleanMemo + "\"", memoFont);
        memoPara.setLeading(13f);
        cell.addElement(memoPara);

        table.addCell(cell);
        return table;
    }

    private Element buildVerificationCard(Transaction tx, String refId, String timestamp) throws DocumentException {
        PdfPTable table = new PdfPTable(1);
        table.setWidthPercentage(100);

        PdfPCell cell = new PdfPCell();
        cell.setBorder(Rectangle.NO_BORDER);
        cell.setCellEvent(new RoundedCellEvent(COLOR_LIGHT_BORDER, Color.WHITE, 6f));
        cell.setPadding(10f);

        PdfPTable inner = new PdfPTable(2);
        inner.setWidthPercentage(100);
        inner.setWidths(new float[]{65f, 35f});

        // Left: Verification hash & compliance
        PdfPCell left = new PdfPCell();
        left.setBorder(Rectangle.NO_BORDER);

        Font headerFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8f, COLOR_MUTED_TEXT);
        left.addElement(new Paragraph("DIGITAL INTEGRITY & AUDIT VERIFICATION", headerFont));

        String auditPayload = String.format("RECEIPT:%s|AMT:%s|TIME:%s",
                refId,
                tx != null && tx.getAmount() != null ? tx.getAmount().toPlainString() : "0.00",
                timestamp);
        String sha256 = AuditCryptoUtil.sha256(auditPayload);

        Font hashFont = FontFactory.getFont(FontFactory.COURIER, 7.5f, COLOR_DARK_TEXT);
        Paragraph hashPara = new Paragraph("SHA-256 Stamp: " + sha256, hashFont);
        hashPara.setLeading(10f);
        left.addElement(hashPara);

        Font noticeFont = FontFactory.getFont(FontFactory.HELVETICA, 7.5f, COLOR_MUTED_TEXT);
        Paragraph noticePara = new Paragraph("Digitally signed & verified by DigiBank Core Banking Engine. Legal voucher under Cambodian Electronic Commerce Law.", noticeFont);
        noticePara.setLeading(9f);
        left.addElement(noticePara);
        inner.addCell(left);

        // Right: Customer Support & Corporate Sign-off
        PdfPCell right = new PdfPCell();
        right.setBorder(Rectangle.NO_BORDER);
        right.setHorizontalAlignment(Element.ALIGN_RIGHT);

        Paragraph rightTitle = new Paragraph("DIGIBANK OPERATIONS", headerFont);
        rightTitle.setAlignment(Element.ALIGN_RIGHT);
        right.addElement(rightTitle);

        Font sealFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8.5f, COLOR_ACCENT_BLUE);
        Paragraph sealPara = new Paragraph("ELECTRONICALLY CERTIFIED", sealFont);
        sealPara.setAlignment(Element.ALIGN_RIGHT);
        sealPara.setLeading(11f);
        right.addElement(sealPara);

        Paragraph hotlinePara = new Paragraph("Support: support@digibank.kh • +855 23 999 888", noticeFont);
        hotlinePara.setAlignment(Element.ALIGN_RIGHT);
        hotlinePara.setLeading(9f);
        right.addElement(hotlinePara);
        inner.addCell(right);

        cell.addElement(inner);
        table.addCell(cell);
        return table;
    }

    private Paragraph buildDivider(float thickness, Color color, float before, float after) {
        Paragraph p = new Paragraph();
        p.setSpacingBefore(before);
        p.setSpacingAfter(after);
        PdfPTable table = new PdfPTable(1);
        table.setWidthPercentage(100);
        PdfPCell c = new PdfPCell();
        c.setBorder(Rectangle.BOTTOM);
        c.setBorderWidthBottom(thickness);
        c.setBorderColorBottom(color);
        c.setFixedHeight(1f);
        c.setPadding(0);
        table.addCell(c);
        p.add(table);
        return p;
    }

    private String resolveDestinationAccount(Transaction tx) {
        if (tx == null) return "Direct Settlement";
        if (tx.getRelatedAccountId() != null) {
            if (accountRepository != null) {
                try {
                    Optional<Account> relAcc = accountRepository.findById(tx.getRelatedAccountId());
                    if (relAcc.isPresent()) {
                        return relAcc.get().getAccountNumber();
                    }
                } catch (Exception ignored) {}
            }
            return "Beneficiary Account #" + tx.getRelatedAccountId();
        }
        if (tx.getTransactionType() != null) {
            return switch (tx.getTransactionType()) {
                case DEPOSIT -> "Branch Counter / Cash Vault";
                case WITHDRAWAL -> "ATM Cash Dispenser Facility";
                case LOAN_DISBURSEMENT, LOAN_REPAYMENT -> "Credit Bureau / Loan Facility";
                case PAYMENT -> "Merchant Settlement Gateway";
                default -> "Direct Clearing";
            };
        }
        return "Direct Settlement";
    }

    // =========================================================================
    // Canvas Cell Events (Borders, Rounded Cards, Brand Badge)
    // =========================================================================

    private static class RoundedCellEvent implements PdfPCellEvent {
        private final Color borderColor;
        private final Color bgColor;
        private final float radius;

        RoundedCellEvent(Color borderColor, Color bgColor, float radius) {
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
