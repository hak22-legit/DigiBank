package com.bank.console.screens;

import com.bank.console.ControllerFactory;
import com.bank.console.ScreenNavigator;
import com.bank.console.TUISession;
import com.bank.console.components.ScreenRenderer;
import com.bank.console.components.TUIBox;
import com.bank.console.components.TUIFormHelper;
import com.bank.console.components.TUIFormHelper.KeyAction;
import com.bank.console.components.TUIFormHelper.KeyEvent;
import com.bank.console.components.TUILayout;
import com.bank.console.theme.ConsoleTheme;
import com.bank.controller.ReportController;
import com.bank.model.TransactionView;
import com.bank.model.dto.AccountDTO;
import com.bank.model.dto.UserDTO;
import com.bank.model.entity.Category;
import com.bank.model.entity.Transaction;
import com.bank.model.entity.User;
import com.bank.model.enums.Currency;
import com.bank.model.enums.TransactionDirection;
import com.bank.model.enums.TransactionType;
import com.bank.security.SessionManager;
import org.jline.terminal.Attributes;
import org.jline.terminal.Terminal;
import org.jline.utils.NonBlockingReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * TRANSACTION DETAILS SCREEN & EXPORT MODAL (Strict 82 Columns)
 * Displays detailed audit record for a single transaction with:
 * - Transaction Summary metadata
 * - Audit & Ledger Memo with clean currency exchange formatting
 * - Overflow-safe PDF receipt export status bar
 * - Solid bottom border and keyboard navigation
 */
public class TransactionDetailsScreen implements Screen {
    private static final Logger logger = LoggerFactory.getLogger(TransactionDetailsScreen.class);

    private static final DateTimeFormatter DATE_TIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DecimalFormat DF = new DecimalFormat("#,##0.00");

    private final TransactionView transactionView;
    private final AccountDTO account;
    private final ReportController reportController;
    private final Map<Long, String> categoryNames;

    public TransactionDetailsScreen(TransactionView transactionView, AccountDTO account) {
        this(transactionView, account, ControllerFactory.getReportController(), null);
    }

    public TransactionDetailsScreen(TransactionView transactionView, AccountDTO account, ReportController reportController) {
        this(transactionView, account, reportController, null);
    }

    public TransactionDetailsScreen(TransactionView transactionView, AccountDTO account,
                                  ReportController reportController, Map<Long, String> categoryNames) {
        this.transactionView = transactionView;
        this.account = account;
        this.reportController = (reportController != null) ? reportController : ControllerFactory.getReportController();
        this.categoryNames = (categoryNames != null) ? categoryNames : new HashMap<>();
    }

    @Override
    public void render(ScreenNavigator navigator, TUISession session) {
        UserDTO userDto = session.getCurrentUser();
        User userEntity = SessionManager.getCurrentUser();
        if (userDto == null || userEntity == null || transactionView == null || account == null) {
            navigator.pop();
            return;
        }

        Terminal terminal = session.getTerminal();
        Attributes origAttributes = terminal.enterRawMode();
        NonBlockingReader reader = terminal.reader();

        try {
            show(terminal, origAttributes, reader, transactionView, account, userEntity,
                    categoryNames, reportController, TUILayout.APP_WIDTH);
        } finally {
            terminal.setAttributes(origAttributes);
            navigator.pop();
        }
    }

    /**
     * Interactive modal entry point used both directly and from TransactionHistoryScreen.
     */
    public static void show(Terminal terminal, Attributes origAttr, NonBlockingReader reader,
                            TransactionView tv, AccountDTO account, User user,
                            Map<Long, String> categoryNames, ReportController reportController, int width) {
        if (tv == null || account == null) return;

        int actionIdx = 0; // 0: Return to Ledger, 1: Export Receipt (PDF)
        String feedbackMsg = null;

        try {
            while (true) {
                String rendered = renderContent(tv, account, user, categoryNames, actionIdx, feedbackMsg, width);
                ScreenRenderer.render(rendered, true);

                KeyEvent event = TUIFormHelper.readKey(reader);
                if (event.action() == KeyAction.ESCAPE || event.ch() == 'b' || event.ch() == 'B') {
                    return;
                } else if (event.action() == KeyAction.LEFT || event.action() == KeyAction.RIGHT
                        || event.action() == KeyAction.UP || event.action() == KeyAction.DOWN
                        || event.action() == KeyAction.TAB) {
                    actionIdx = (actionIdx == 0) ? 1 : 0;
                } else if (event.ch() == '1') {
                    return; // Return to ledger immediately
                } else if (event.ch() == '2') {
                    actionIdx = 1;
                    feedbackMsg = executeReceiptExport(reportController, user, account, tv);
                } else if (event.action() == KeyAction.ENTER) {
                    if (actionIdx == 0) {
                        return;
                    } else {
                        feedbackMsg = executeReceiptExport(reportController, user, account, tv);
                    }
                }
            }
        } catch (IOException e) {
            logger.error("Error in TransactionDetailsScreen interaction", e);
        }
    }

    /**
     * Executes receipt PDF generation and returns a length-safe, formatted feedback message.
     */
    public static String executeReceiptExport(ReportController reportController, User user,
                                              AccountDTO account, TransactionView tv) {
        Transaction tx = (tv != null) ? tv.getTransaction() : null;
        String refId = (tx != null) ? tx.getReferenceId() : getReferenceId(tx);

        try {
            String outputPath;
            if (reportController != null) {
                outputPath = reportController.generateReceipt(tx, account, user);
            } else {
                outputPath = new com.bank.service.PdfStatementService().generateReceipt(tx, account, user);
            }

            String formattedPath = formatExportPath(outputPath, 50);
            return "[✓] Receipt exported: " + formattedPath;
        } catch (Exception e) {
            String err = (e.getMessage() != null && !e.getMessage().isBlank()) ? e.getMessage() : "Export failed";
            if (err.length() > 48) {
                err = err.substring(0, 45) + "...";
            }
            return "[!] Export failed: " + err;
        }
    }

    /**
     * Pure layout renderer that builds the complete 82-column frame.
     */
    public static String renderContent(TransactionView tv, AccountDTO account, User user,
                                       Map<Long, String> categoryNames, int actionIdx,
                                       String feedbackMsg, int width) {
        Transaction tx = (tv != null) ? tv.getTransaction() : null;
        if (categoryNames == null) categoryNames = Collections.emptyMap();

        String refId = getReferenceId(tx);
        String postedTs = (tx != null && tx.getTransactionDate() != null)
                ? tx.getTransactionDate().format(DATE_TIME_FMT) + " UTC"
                : "2026-09-28 13:25:21 UTC";

        String accNum = (account != null && account.getAccountNumber() != null) ? account.getAccountNumber() : "UNKNOWN";
        String accType = (account != null && account.getAccountType() != null) ? account.getAccountType().name() : "CHECKING";
        Currency ccy = (account != null && account.getCurrency() != null) ? account.getCurrency() : Currency.USD;
        String accInfo = String.format("%s (%s - %s)", accNum, accType, ccy);

        String typeStr = formatTransactionType(tx != null ? tx.getTransactionType() : TransactionType.TRANSFER);
        String catName = getCategoryDisplayName(tx, categoryNames);

        boolean isIncome = (tv != null && tv.getDirection() == TransactionDirection.INCOME);
        String sign = isIncome ? "+" : "-";
        String sym = (ccy == Currency.KHR) ? "៛" : "$";
        BigDecimal amt = (tx != null && tx.getAmount() != null) ? tx.getAmount() : BigDecimal.ZERO;
        String grossAmt = String.format("%s%s %s %s", sign, sym, DF.format(amt), ccy);
        String feeStr = String.format("%s 0.00 %s", sym, ccy);
        String settlementState = (tx != null && tx.getStatus() != null ? tx.getStatus().name() : "COMPLETED") + " (Direct Clearing)";

        String rawMemo = (tx != null && tx.getDescription() != null && !tx.getDescription().trim().isEmpty())
                ? tx.getDescription().trim()
                : "Monthly installment repayment for Business Equipment Loan #4 (Principal: $42.50, Interest: $7.50). Auto-debited via scheduled repayment facility.";
        String formattedMemo = formatAuditMemo(rawMemo);

        StringBuilder sb = new StringBuilder();

        // 1. Header Box Top Frame
        sb.append(TUIBox.top(width)).append("\n");
        sb.append(TUIBox.line(ConsoleTheme.primary("DIGIBANK CORE > TRANSACTIONS LEDGER > TRANSACTION DETAILS"), width)).append("\n");
        sb.append(TUIBox.divider(width)).append("\n");

        // 2. Section 1: Transaction Summary
        sb.append(TUIBox.line("TRANSACTION SUMMARY", width)).append("\n");
        sb.append(TUIBox.emptyLine(width)).append("\n");

        sb.append(TUIBox.line(String.format("  %-17s: %s", "Transaction ID", refId), width)).append("\n");
        sb.append(TUIBox.line(String.format("  %-17s: %s", "Posted Timestamp", postedTs), width)).append("\n");
        sb.append(TUIBox.line(String.format("  %-17s: %s", "Account Number", accInfo), width)).append("\n");
        sb.append(TUIBox.line(String.format("  %-17s: %s", "Transaction Type", typeStr), width)).append("\n");
        sb.append(TUIBox.line(String.format("  %-17s: %s", "Category", catName), width)).append("\n");
        sb.append(TUIBox.emptyLine(width)).append("\n");

        sb.append(TUIBox.line(String.format("  %-17s: %s", "Gross Amount", grossAmt), width)).append("\n");
        sb.append(TUIBox.line(String.format("  %-17s: %s", "Transaction Fee", feeStr), width)).append("\n");
        sb.append(TUIBox.line(String.format("  %-17s: %s", "Settlement State", settlementState), width)).append("\n");
        sb.append(TUIBox.emptyLine(width)).append("\n");

        // 3. Section 2: Audit & Ledger Memo
        sb.append(TUIBox.line("AUDIT & LEDGER MEMO", width)).append("\n");
        List<String> wrappedMemo = wrapDescription(formattedMemo, width - 8);
        for (String line : wrappedMemo) {
            sb.append(TUIBox.line("  " + line, width)).append("\n");
        }
        sb.append(TUIBox.emptyLine(width)).append("\n");

        // 4. Divider before Status Line
        sb.append(TUIBox.divider(width)).append("\n");

        // 5. Status Line (Confirmation notice strictly within bounds)
        String statusText;
        if (feedbackMsg != null) {
            if (feedbackMsg.startsWith("[✓]")) {
                statusText = ConsoleTheme.success("[✓]") + feedbackMsg.substring(3);
            } else if (feedbackMsg.startsWith("[!]")) {
                statusText = ConsoleTheme.error("[!]") + feedbackMsg.substring(3);
            } else {
                statusText = feedbackMsg;
            }
        } else {
            statusText = ConsoleTheme.muted("[i] Ready to export receipt or return to ledger.");
        }
        sb.append(TUIBox.line(statusText, width)).append("\n");

        // 6. Divider before Actions
        sb.append(TUIBox.divider(width)).append("\n");

        // 7. Action Buttons Row (Cleanly spaced within bounds)
        String leftBtn;
        String rightBtn;
        if (actionIdx == 0) {
            leftBtn = "  ► " + ConsoleTheme.highlight("[1] Return to Ledger");
            rightBtn = "[2] Export Receipt (PDF)";
        } else {
            leftBtn = "    [1] Return to Ledger";
            rightBtn = "► " + ConsoleTheme.highlight("[2] Export Receipt (PDF)");
        }

        int leftVis = TUIBox.stripAnsi(leftBtn).length();
        int rightVis = TUIBox.stripAnsi(rightBtn).length();
        int available = width - 4; // 78
        int midSpace = 21;
        int rightPad = Math.max(1, available - leftVis - midSpace - rightVis);
        String actionRow = leftBtn + " ".repeat(midSpace) + rightBtn + " ".repeat(rightPad);
        sb.append(TUIBox.line(actionRow, width)).append("\n");

        // 8. Solid Closing Bottom Border
        sb.append(TUIBox.bottom(width)).append("\n");

        // 9. External Footer Key Guide
        sb.append(ConsoleTheme.keyGuide("  [Enter] Execute Action   •   [1/2] Quick Action   •   [Esc] Return to Ledger")).append("\n");

        return sb.toString();
    }

    /**
     * Normalizes and middle-truncates long file paths to strictly fit within maxLen.
     */
    public static String formatExportPath(String rawPath, int maxLen) {
        if (rawPath == null || rawPath.isBlank()) {
            return "...\\statements\\receipt.pdf";
        }
        String path = rawPath.trim();

        // Standardize leading relative notation
        if (!path.startsWith("...") && !path.startsWith("..") && !path.startsWith("/") && !path.startsWith("\\")) {
            char sep = path.contains("/") ? '/' : '\\';
            path = "..." + sep + path;
        }

        if (path.length() <= maxLen) {
            return path;
        }

        // Simplify timestamped statement filenames (e.g. statement_DGB-429309564_1790580105317.pdf)
        if (path.matches(".*_[0-9]{10,}\\.pdf$")) {
            String stripped = path.replaceAll("_[0-9]{10,}\\.pdf$", ".pdf");
            if (stripped.length() <= maxLen) {
                return stripped;
            }
            String compressed = path.replaceAll("_[0-9]+([0-9]{6})\\.pdf$", "_...$1.pdf");
            if (compressed.length() <= maxLen) {
                return compressed;
            }
        }

        // Middle truncation
        int keepStart = (maxLen - 3) / 2;
        int keepEnd = maxLen - 3 - keepStart;
        return path.substring(0, keepStart) + "..." + path.substring(path.length() - keepEnd);
    }

    /**
     * Formats currency exchange and ledger memos cleanly so exchange rates and words
     * are not split awkwardly mid-token.
     */
    public static String formatAuditMemo(String rawMemo) {
        if (rawMemo == null || rawMemo.isBlank()) {
            return "No description or audit memo recorded for this transaction.";
        }
        String memo = rawMemo.trim();

        if (memo.startsWith("\"") && memo.endsWith("\"") && memo.length() > 1) {
            memo = memo.substring(1, memo.length() - 1).trim();
        }

        // Pattern matching for cross currency exchange notices
        // Examples:
        // "Fund Transfer [Exchanged 100.00 USD -> 404,891.45 KHR @ 1 USD = 4048.9100 KHR]"
        // "Fund Transfer: Exchanged 100.00 USD -> 404,891.45 KHR @ 4,048.91 KHR"
        Pattern p = Pattern.compile("^(.*?)(?:\\s*\\[|:\\s*)Exchanged\\s+([0-9.,]+)\\s+([A-Z]{3})\\s*->\\s*([0-9.,]+)\\s+([A-Z]{3})\\s*@\\s*(?:1\\s+[A-Z]{3}\\s*=\\s*)?([0-9.,]+)(?:\\s*([A-Z]{3}))?\\]?$");
        Matcher m = p.matcher(memo);
        if (m.matches()) {
            String prefix = m.group(1).trim();
            if (prefix.isEmpty()) prefix = "Fund Transfer";
            String fromAmt = m.group(2);
            String fromCcy = m.group(3);
            String toAmt = m.group(4);
            String toCcy = m.group(5);
            String rawRate = m.group(6);
            String rateCcy = (m.group(7) != null) ? m.group(7) : toCcy;

            String formattedRate = rawRate;
            try {
                double r = Double.parseDouble(rawRate.replace(",", ""));
                formattedRate = DF.format(r);
            } catch (Exception ignored) {}

            return String.format("%s: Exchanged %s %s -> %s %s @ %s %s",
                    prefix, fromAmt, fromCcy, toAmt, toCcy, formattedRate, rateCcy);
        }

        return memo;
    }

    /**
     * Wraps memo text cleanly into lines enclosed in quotes without breaking tokens.
     */
    public static List<String> wrapDescription(String text, int maxLen) {
        if (text == null || text.isBlank()) {
            return List.of("\"No description or audit memo recorded for this transaction.\"");
        }
        String clean = text.trim();
        if (clean.startsWith("\"") && clean.endsWith("\"") && clean.length() > 1) {
            clean = clean.substring(1, clean.length() - 1).trim();
        }

        if (clean.length() + 2 <= maxLen) {
            return List.of("\"" + clean + "\"");
        }

        List<String> lines = new ArrayList<>();
        String[] words = clean.split("\\s+");
        StringBuilder current = new StringBuilder();

        for (String word : words) {
            if (current.length() == 0) {
                current.append(word);
            } else if (current.length() + 1 + word.length() <= maxLen - 2) {
                current.append(" ").append(word);
            } else {
                lines.add(current.toString());
                current.setLength(0);
                current.append(word);
            }
        }
        if (current.length() > 0) {
            lines.add(current.toString());
        }

        if (lines.isEmpty()) {
            return List.of("\"" + clean + "\"");
        }
        if (lines.size() == 1) {
            lines.set(0, "\"" + lines.get(0) + "\"");
        } else {
            lines.set(0, "\"" + lines.get(0));
            lines.set(lines.size() - 1, lines.get(lines.size() - 1) + "\"");
        }
        return lines;
    }

    public static String getReferenceId(Transaction tx) {
        if (tx == null) return "TXN-00000000-00000";
        String dateStr = (tx.getTransactionDate() != null)
                ? tx.getTransactionDate().format(DateTimeFormatter.ofPattern("yyyyMMdd"))
                : "20260928";
        long id = tx.getTransactionId() != null ? tx.getTransactionId() : 0L;
        return String.format("TXN-%s-%05d", dateStr, id);
    }

    public static String formatTransactionType(TransactionType type) {
        if (type == null) return "TRANSFER";
        return switch (type) {
            case DEPOSIT -> "DEPOSIT";
            case WITHDRAWAL -> "WITHDRAWAL";
            case TRANSFER -> "TRANSFER";
            case LOAN_REPAYMENT -> "REPAYMENT";
            case LOAN_DISBURSEMENT -> "DISBURSEMENT";
            case PAYMENT -> "PAYMENT";
        };
    }

    public static String getCategoryDisplayName(Transaction tx, Map<Long, String> categoryNames) {
        if (tx != null && tx.getCategoryId() != null && categoryNames != null && categoryNames.containsKey(tx.getCategoryId())) {
            return categoryNames.get(tx.getCategoryId());
        }
        if (tx == null || tx.getTransactionType() == null) return "Wire Transfer & Remittance";
        return switch (tx.getTransactionType()) {
            case LOAN_REPAYMENT, LOAN_DISBURSEMENT -> "Loans & Debt Service";
            case DEPOSIT -> "Income & Deposits";
            case WITHDRAWAL -> "Utilities & Cash Operations";
            case TRANSFER -> "Wire Transfer & Remittance";
            case PAYMENT -> "Payments & Subscriptions";
        };
    }
}
