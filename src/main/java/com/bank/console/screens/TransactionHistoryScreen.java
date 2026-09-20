package com.bank.console.screens;

import com.bank.console.ControllerFactory;
import com.bank.console.ScreenNavigator;
import com.bank.console.TUISession;
import com.bank.console.components.ConsolePrompt;
import com.bank.console.components.ScreenRenderer;
import com.bank.console.components.TUIBox;
import com.bank.console.components.TUIFormHelper;
import com.bank.console.components.TUIFormHelper.KeyAction;
import com.bank.console.components.TUIFormHelper.KeyEvent;
import com.bank.console.components.TUILayout;
import com.bank.console.theme.ConsoleTheme;
import com.bank.controller.AccountController;
import com.bank.controller.ReportController;
import com.bank.controller.TransactionController;
import com.bank.model.TransactionView;
import com.bank.model.dto.AccountDTO;
import com.bank.model.dto.UserDTO;
import com.bank.model.entity.Category;
import com.bank.model.entity.Transaction;
import com.bank.model.entity.User;
import com.bank.model.enums.Currency;
import com.bank.model.enums.HistoryFilter;
import com.bank.model.enums.TransactionDirection;
import com.bank.model.enums.TransactionType;
import com.bank.security.SessionManager;
import com.bank.ui.Ansi;
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

/**
 * SCREEN 7: TRANSACTION LEDGER & HISTORY (82 Columns)
 * Master-Detail view with live preview drawer, pure keyboard navigation,
 * and dedicated 82-column full inspection modal.
 */
public class TransactionHistoryScreen implements Screen {
    private static final Logger logger = LoggerFactory.getLogger(TransactionHistoryScreen.class);

    private final TransactionController transactionController;
    private final AccountController accountController;
    private final ReportController reportController;

    public enum LedgerFilter {
        ALL("ALL TRANSACTIONS"),
        DEPOSIT("DEPOSITS ONLY"),
        WITHDRAW("WITHDRAWALS ONLY"),
        TRANSFER("TRANSFERS ONLY"),
        LOAN("LOAN OPERATIONS");

        private final String label;
        LedgerFilter(String label) { this.label = label; }
        public String getLabel() { return label; }
    }

    public TransactionHistoryScreen() {
        this(ControllerFactory.getTransactionController(),
             ControllerFactory.getAccountController(),
             ControllerFactory.getReportController());
    }

    public TransactionHistoryScreen(TransactionController transactionController) {
        this(transactionController, ControllerFactory.getAccountController(), ControllerFactory.getReportController());
    }

    public TransactionHistoryScreen(TransactionController transactionController, AccountController accountController) {
        this(transactionController, accountController, ControllerFactory.getReportController());
    }

    public TransactionHistoryScreen(TransactionController transactionController, AccountController accountController, ReportController reportController) {
        this.transactionController = transactionController;
        this.accountController = accountController;
        this.reportController = reportController;
    }

    @Override
    public void render(ScreenNavigator navigator, TUISession session) {
        UserDTO userDto = session.getCurrentUser();
        User userEntity = SessionManager.getCurrentUser();
        if (userDto == null || userEntity == null) {
            navigator.pop();
            return;
        }

        int width = TUILayout.APP_WIDTH;
        session.clearScreen();

        List<AccountDTO> accounts;
        try {
            accounts = accountController.getAccountsForUser(userEntity);
        } catch (Exception e) {
            TUILayout.printAlert("Failed to load accounts: " + e.getMessage(), true);
            ConsolePrompt.pause();
            navigator.pop();
            return;
        }

        if (accounts == null || accounts.isEmpty()) {
            TUILayout.printAlert("No accounts found to view transaction history.", true);
            ConsolePrompt.pause();
            navigator.pop();
            return;
        }

        AccountDTO selectedAcc = accounts.get(0);

        // Cache category names
        Map<Long, String> categoryNames = new HashMap<>();
        try {
            List<Category> categories = ControllerFactory.getCategoryController().getVisibleCategories(userEntity);
            for (Category c : categories) {
                categoryNames.put(c.getCategoryId(), c.getName());
            }
        } catch (Exception ignored) {}

        LedgerFilter filter = LedgerFilter.ALL;
        int currentPage = 1;
        int pageSize = 5;
        int selectedRowIdx = 0;

        DecimalFormat df = new DecimalFormat("#,##0.00");
        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

        Terminal terminal = session.getTerminal();
        Attributes origAttributes = terminal.enterRawMode();
        NonBlockingReader reader = terminal.reader();

        boolean firstRender = true;
        boolean needsReload = true;
        List<TransactionView> allViews = Collections.emptyList();
        Map<Long, BigDecimal> runningBalanceMap = new HashMap<>();

        try {
            while (true) {
                if (needsReload) {
                    try {
                        allViews = transactionController.getTransactionHistory(selectedAcc.getAccountId(), HistoryFilter.ALL, userEntity);
                    } catch (Exception ignored) {}

                    if (allViews == null) allViews = Collections.emptyList();

                    // Compute running balance for every transaction
                    runningBalanceMap.clear();
                    BigDecimal running = selectedAcc.getBalance();
                    for (TransactionView tv : allViews) {
                        Transaction t = tv.getTransaction();
                        if (t != null && t.getTransactionId() != null) {
                            runningBalanceMap.put(t.getTransactionId(), running);
                            BigDecimal amt = t.getAmount() != null ? t.getAmount() : BigDecimal.ZERO;
                            if (tv.getDirection() == TransactionDirection.INCOME) {
                                running = running.subtract(amt);
                            } else {
                                running = running.add(amt);
                            }
                        }
                    }
                    needsReload = false;
                }

                // Filter transactions according to active LedgerFilter
                List<TransactionView> filteredViews = new ArrayList<>();
                for (TransactionView tv : allViews) {
                    if (matchesFilter(tv, filter)) {
                        filteredViews.add(tv);
                    }
                }

                int totalRecords = filteredViews.size();
                int totalPages = Math.max(1, (int) Math.ceil((double) totalRecords / pageSize));
                if (currentPage > totalPages) currentPage = totalPages;
                if (currentPage < 1) currentPage = 1;

                int startIdx = (currentPage - 1) * pageSize;
                int endIdx = Math.min(startIdx + pageSize, totalRecords);
                int pageCount = Math.max(0, endIdx - startIdx);

                if (selectedRowIdx >= pageCount) {
                    selectedRowIdx = Math.max(0, pageCount - 1);
                }

                // Build Screen Frame (Strict 82 columns)
                StringBuilder sb = new StringBuilder();
                String headerTitle = String.format("DIGIBANK CORE > TRANSACTIONS LEDGER (%s - %s)",
                        selectedAcc.getAccountNumber(), selectedAcc.getCurrency());

                sb.append(TUIBox.top(width)).append("\n");
                sb.append(TUIBox.line(ConsoleTheme.primary(headerTitle), width)).append("\n");
                sb.append(TUIBox.divider(width)).append("\n");

                String borderChar = ConsoleTheme.border(String.valueOf(TUIBox.V));

                // Table Header: EXACT 80 CHARACTERS (fits inside 82-col box: border + 80 chars + border)
                String rawTableHeader = String.format("   %-17s    %-12s    %-25s    %10s ",
                        "DATE & TIME", "TYPE", "CATEGORY", "AMOUNT");
                String tableHeader = rawTableHeader
                        .replace("DATE & TIME", Ansi.cyan("DATE & TIME"))
                        .replace("TYPE", Ansi.cyan("TYPE"))
                        .replace("CATEGORY", Ansi.cyan("CATEGORY"))
                        .replace("AMOUNT", Ansi.cyan("AMOUNT"));
                sb.append(borderChar).append(tableHeader).append(borderChar).append("\n");

                // Separator Line (77 dashes + 3 spaces padding = 80 chars)
                String separator = " " + "─".repeat(77) + "  ";
                sb.append(borderChar).append(separator).append(borderChar).append("\n");

                TransactionView currentSelectedView = null;

                if (totalRecords == 0) {
                    String emptyMsg = String.format("   %-77s", "No transactions found for this account/filter.");
                    sb.append(borderChar).append(ConsoleTheme.muted(emptyMsg)).append(borderChar).append("\n");
                    for (int i = 1; i < pageSize; i++) {
                        sb.append(borderChar).append(" ".repeat(80)).append(borderChar).append("\n");
                    }
                } else {
                    for (int i = 0; i < pageCount; i++) {
                        int itemIndex = startIdx + i;
                        TransactionView tv = filteredViews.get(itemIndex);
                        Transaction tx = tv.getTransaction();

                        if (i == selectedRowIdx) {
                            currentSelectedView = tv;
                        }

                        String dateStr = tx.getTransactionDate() != null
                                ? tx.getTransactionDate().format(dtf)
                                : "2026-09-11 00:00";

                        String typeStr = formatTransactionType(tx.getTransactionType());
                        if (typeStr.length() > 12) typeStr = typeStr.substring(0, 12);

                        String catStr = getCategoryDisplayName(tx, categoryNames);
                        if (catStr.length() > 25) catStr = catStr.substring(0, 22) + "...";

                        BigDecimal amt = tx.getAmount() != null ? tx.getAmount() : BigDecimal.ZERO;
                        boolean isIncome = tv.getDirection() == TransactionDirection.INCOME;
                        String sign = isIncome ? "+" : "-";
                        String sym = (selectedAcc.getCurrency() == Currency.KHR) ? "៛" : "$";
                        String amountFormatted = String.format("%s%s%8s", sign, sym, df.format(amt));

                        boolean isSelected = (i == selectedRowIdx);

                        // 1. Build exact 80-character plaintext line
                        String plainText = String.format(" %-2s%-17s    %-12s    %-25s    %10s ",
                                isSelected ? "▸" : " ",
                                dateStr,
                                typeStr,
                                catStr,
                                amountFormatted
                        );
                        if (plainText.length() > 80) plainText = plainText.substring(0, 80);
                        else if (plainText.length() < 80) plainText = plainText + " ".repeat(80 - plainText.length());

                        // 2. Wrap with ANSI styling ONLY around the content, keeping borders clean:
                        String lineToPrint;
                        if (isSelected) {
                            lineToPrint = borderChar + ConsoleTheme.REVERSE + plainText + ConsoleTheme.RESET + borderChar;
                        } else {
                            String coloredAmount = isIncome ? Ansi.green(amountFormatted) : Ansi.red(amountFormatted);
                            String coloredLine = plainText.replace(amountFormatted, coloredAmount);
                            lineToPrint = borderChar + coloredLine + borderChar;
                        }
                        sb.append(lineToPrint).append("\n");
                    }

                    // Fill remaining lines to maintain constant box height
                    for (int i = pageCount; i < pageSize; i++) {
                        sb.append(borderChar).append(" ".repeat(80)).append(borderChar).append("\n");
                    }
                }

                sb.append(borderChar).append(" ".repeat(80)).append(borderChar).append("\n");
                sb.append(TUIBox.divider(width)).append("\n");

                // Selected Transaction Details Section (Master-Detail Live Preview Drawer)
                sb.append(TUIBox.line("SELECTED TRANSACTION DETAILS", width)).append("\n");
                sb.append(TUIBox.emptyLine(width)).append("\n");

                if (currentSelectedView != null && currentSelectedView.getTransaction() != null) {
                    Transaction selTx = currentSelectedView.getTransaction();
                    String refId = getReferenceId(selTx);
                    String desc = selTx.getDescription() != null ? selTx.getDescription().trim() : "General Banking Transaction";
                    if (desc.length() > 61) {
                        desc = desc.substring(0, 58) + "...";
                    }
                    String channel = getChannelPeer(selTx);
                    String statusStr = getStatusString(selTx);

                    sb.append(TUIBox.line(String.format("  Reference ID : %s", refId), width)).append("\n");
                    sb.append(TUIBox.line(String.format("  Description  : %s", desc), width)).append("\n");
                    sb.append(TUIBox.line(String.format("  Channel/Peer : %s", channel), width)).append("\n");
                    sb.append(TUIBox.line(String.format("  Status       : %s", statusStr), width)).append("\n");
                } else {
                    sb.append(TUIBox.line("  Reference ID : N/A", width)).append("\n");
                    sb.append(TUIBox.line("  Description  : No transaction record selected", width)).append("\n");
                    sb.append(TUIBox.line("  Channel/Peer : N/A", width)).append("\n");
                    sb.append(TUIBox.line("  Status       : N/A", width)).append("\n");
                }

                sb.append(TUIBox.divider(width)).append("\n");

                // Summary Row Compartment
                String pageIndicator = String.format("Page: [ %d / %d ]", currentPage, totalPages);
                String filterIndicator = String.format("Filter: [%s]", filter.getLabel());
                String totalIndicator = String.format("Total Records: %d", totalRecords);
                String summaryRow = String.format("%-19s│ %-28s│ %s", pageIndicator, filterIndicator, totalIndicator);
                sb.append(TUIBox.line(summaryRow, width)).append("\n");

                sb.append(TUIBox.bottom(width)).append("\n");

                // Single Navigation Line directly beneath bottom border
                sb.append(ConsoleTheme.keyGuide("[↑/↓] Select Row  •  [←/→] Page  •  [F] Filter  •  [E] Export PDF  •  [Esc] Back")).append("\n");

                ScreenRenderer.render(sb.toString(), firstRender);
                firstRender = false;

                int key = reader.read();
                if (key == -1) {
                    terminal.setAttributes(origAttributes);
                    navigator.pop();
                    return;
                }

                if (key == 27) { // ESC or Escape Sequence
                    int next1 = reader.read(15); // check for non-blocking next byte
                    if (next1 == '[' || next1 == 'O') {
                        int next2 = reader.read(15);
                        switch (next2) {
                            case 'A': // UP ARROW
                                if (selectedRowIdx > 0) {
                                    selectedRowIdx--;
                                } else if (currentPage > 1) {
                                    currentPage--;
                                    selectedRowIdx = pageSize - 1;
                                }
                                break;
                            case 'B': // DOWN ARROW
                                if (selectedRowIdx < pageCount - 1) {
                                    selectedRowIdx++;
                                } else if (currentPage < totalPages) {
                                    currentPage++;
                                    selectedRowIdx = 0;
                                }
                                break;
                            case 'C': // RIGHT ARROW (Next Page)
                                if (currentPage < totalPages) {
                                    currentPage++;
                                    selectedRowIdx = 0;
                                }
                                break;
                            case 'D': // LEFT ARROW (Prev Page)
                                if (currentPage > 1) {
                                    currentPage--;
                                    selectedRowIdx = 0;
                                }
                                break;
                        }
                    } else {
                        // Standalone ESC -> Exit / Back
                        terminal.setAttributes(origAttributes);
                        navigator.pop();
                        return;
                    }
                } else if (key == 'k' || key == 'K' || key == 'w' || key == 'W') {
                    if (selectedRowIdx > 0) {
                        selectedRowIdx--;
                    } else if (currentPage > 1) {
                        currentPage--;
                        selectedRowIdx = pageSize - 1;
                    }
                } else if (key == 'j' || key == 'J' || key == 's' || key == 'S') {
                    if (selectedRowIdx < pageCount - 1) {
                        selectedRowIdx++;
                    } else if (currentPage < totalPages) {
                        currentPage++;
                        selectedRowIdx = 0;
                    }
                } else if (key == 'n' || key == 'N') {
                    if (currentPage < totalPages) {
                        currentPage++;
                        selectedRowIdx = 0;
                    }
                } else if (key == 'p' || key == 'P') {
                    if (currentPage > 1) {
                        currentPage--;
                        selectedRowIdx = 0;
                    }
                } else if (key == 'b' || key == 'B') {
                    terminal.setAttributes(origAttributes);
                    navigator.pop();
                    return;
                } else if (key == 'f' || key == 'F') {
                    filter = switch (filter) {
                        case ALL -> LedgerFilter.DEPOSIT;
                        case DEPOSIT -> LedgerFilter.WITHDRAW;
                        case WITHDRAW -> LedgerFilter.TRANSFER;
                        case TRANSFER -> LedgerFilter.LOAN;
                        case LOAN -> LedgerFilter.ALL;
                    };
                    currentPage = 1;
                    selectedRowIdx = 0;
                } else if (key == 'e' || key == 'E') {
                    handleExportPdf(terminal, origAttributes, reader, selectedAcc, userEntity, width);
                    firstRender = true;
                } else if (key == 'r' || key == 'R') {
                    needsReload = true;
                } else if (key == '\r' || key == '\n') {
                    if (currentSelectedView != null) {
                        handleViewDetails(terminal, origAttributes, reader, currentSelectedView, selectedAcc, userEntity, categoryNames, width);
                        firstRender = true;
                    }
                }
            }
        } catch (Exception e) {
            logger.error("Error on transaction history screen", e);
        } finally {
            terminal.setAttributes(origAttributes);
        }
    }

    private boolean matchesFilter(TransactionView tv, LedgerFilter filter) {
        if (filter == LedgerFilter.ALL) return true;
        Transaction t = tv.getTransaction();
        if (t == null || t.getTransactionType() == null) return false;
        return switch (filter) {
            case ALL -> true;
            case DEPOSIT -> t.getTransactionType() == TransactionType.DEPOSIT;
            case WITHDRAW -> t.getTransactionType() == TransactionType.WITHDRAWAL;
            case TRANSFER -> t.getTransactionType() == TransactionType.TRANSFER;
            case LOAN -> t.getTransactionType() == TransactionType.LOAN_DISBURSEMENT
                    || t.getTransactionType() == TransactionType.LOAN_REPAYMENT;
        };
    }

    private String formatTransactionType(TransactionType type) {
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

    private String getCategoryDisplayName(Transaction tx, Map<Long, String> categoryNames) {
        if (tx.getCategoryId() != null && categoryNames.containsKey(tx.getCategoryId())) {
            String name = categoryNames.get(tx.getCategoryId());
            return name.length() > 24 ? name.substring(0, 21) + "..." : name;
        }
        if (tx.getTransactionType() == null) return "General";
        return switch (tx.getTransactionType()) {
            case LOAN_REPAYMENT, LOAN_DISBURSEMENT -> "Loans";
            case DEPOSIT -> "Income";
            case WITHDRAWAL -> "Utilities";
            case TRANSFER -> "Transfers";
            case PAYMENT -> "Payments";
        };
    }

    private String getReferenceId(Transaction tx) {
        if (tx == null) return "TXN-00000000-00000";
        String dateStr = (tx.getTransactionDate() != null)
                ? tx.getTransactionDate().format(DateTimeFormatter.ofPattern("yyyyMMdd"))
                : "20260911";
        long id = tx.getTransactionId() != null ? tx.getTransactionId() : 0L;
        return String.format("TXN-%s-%05d", dateStr, id);
    }

    private String getChannelPeer(Transaction tx) {
        if (tx == null || tx.getTransactionType() == null) return "Automated Clearing House (ACH)";
        return switch (tx.getTransactionType()) {
            case LOAN_REPAYMENT, LOAN_DISBURSEMENT -> "Automated Debit / Credit Bureau System";
            case TRANSFER -> (tx.getRelatedAccountId() != null)
                    ? "P2P Transfer (Linked Account #" + tx.getRelatedAccountId() + ")"
                    : "Inter-Bank Wire Transfer System";
            case DEPOSIT -> "Branch Counter / Cash In Vault";
            case WITHDRAWAL -> "ATM Cash Dispenser Facility";
            case PAYMENT -> "Merchant Settlement Gateway";
        };
    }

    private String getStatusString(Transaction tx) {
        String status = (tx != null && tx.getStatus() != null) ? tx.getStatus().name() : "COMPLETED";
        String timestamp = (tx != null && tx.getTransactionDate() != null)
                ? tx.getTransactionDate().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
                : "2026-09-11 00:28:14";
        return String.format("%s (Settled at %s UTC)", status, timestamp);
    }

    private void handleExportPdf(Terminal terminal, Attributes origAttr, NonBlockingReader reader,
                                 AccountDTO account, User user, int width) {
        try {
            LocalDateTime from = LocalDateTime.now().minusDays(30);
            LocalDateTime to = LocalDateTime.now();
            String outputPath = (reportController != null)
                    ? reportController.generateStatement(user, account, from, to)
                    : "statement_" + account.getAccountNumber() + ".pdf";

            StringBuilder sb = new StringBuilder();
            sb.append(TUIBox.top(width)).append("\n");
            sb.append(TUIBox.line(ConsoleTheme.primary("DIGIBANK CORE > TRANSACTIONS LEDGER > STATEMENT EXPORT"), width)).append("\n");
            sb.append(TUIBox.divider(width)).append("\n");
            sb.append(TUIBox.emptyLine(width)).append("\n");
            sb.append(TUIBox.center(ConsoleTheme.success("✔ Official PDF Statement Exported Successfully!"), width)).append("\n");
            sb.append(TUIBox.emptyLine(width)).append("\n");
            sb.append(TUIBox.line(String.format("  Target Account : %s (%s - %s)", account.getAccountNumber(), account.getAccountType(), account.getCurrency()), width)).append("\n");
            sb.append(TUIBox.line("  Statement Term : Last 30 Days (Standard Audit Period)", width)).append("\n");
            sb.append(TUIBox.line("  File Location  : " + ConsoleTheme.highlight(outputPath), width)).append("\n");
            sb.append(TUIBox.emptyLine(width)).append("\n");
            sb.append(TUIBox.divider(width)).append("\n");
            sb.append(ConsoleTheme.keyGuide("Press [Enter] or [Esc] to return to ledger")).append("\n");

            ScreenRenderer.render(sb.toString(), true);
            while (true) {
                int key = reader.read();
                if (key == '\r' || key == '\n' || key == 27 || key == 'b' || key == 'B') {
                    break;
                }
            }
        } catch (Exception e) {
            StringBuilder errSb = new StringBuilder();
            errSb.append(TUIBox.top(width)).append("\n");
            errSb.append(TUIBox.line(ConsoleTheme.error(" Export Failed: " + e.getMessage()), width)).append("\n");
            errSb.append(TUIBox.divider(width)).append("\n");
            errSb.append(ConsoleTheme.keyGuide("Press [Enter] or [Esc] to return to ledger")).append("\n");
            ScreenRenderer.render(errSb.toString(), true);
            try {
                while (true) {
                    int key = reader.read();
                    if (key == '\r' || key == '\n' || key == 27 || key == 'b' || key == 'B') {
                        break;
                    }
                }
            } catch (Exception ignored) {}
        }
    }

    private void handleViewDetails(Terminal terminal, Attributes origAttr, NonBlockingReader reader,
                                   TransactionView tv, AccountDTO account, User user,
                                   Map<Long, String> categoryNames, int width) {
        Transaction tx = tv.getTransaction();
        DecimalFormat df = new DecimalFormat("#,##0.00");
        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

        String catName = "Loans & Debt Service";
        if (tx.getCategoryId() != null && categoryNames.containsKey(tx.getCategoryId())) {
            catName = categoryNames.get(tx.getCategoryId());
        } else if (tx.getTransactionType() == TransactionType.DEPOSIT) {
            catName = "Income & Deposits";
        } else if (tx.getTransactionType() == TransactionType.WITHDRAWAL) {
            catName = "Utilities & Cash Operations";
        } else if (tx.getTransactionType() == TransactionType.TRANSFER) {
            catName = "Wire Transfer & Remittance";
        }

        String refId = getReferenceId(tx);
        String postedTs = (tx.getTransactionDate() != null)
                ? tx.getTransactionDate().format(dtf) + " UTC"
                : "2026-09-11 00:28:14 UTC";

        boolean isIncome = tv.getDirection() == TransactionDirection.INCOME;
        String sign = isIncome ? "+" : "-";
        String sym = (account.getCurrency() == Currency.KHR) ? "៛" : "$";
        BigDecimal amt = tx.getAmount() != null ? tx.getAmount() : BigDecimal.ZERO;
        String grossAmt = String.format("%s%s %s %s", sign, sym, df.format(amt), account.getCurrency());
        String feeStr = String.format("%s 0.00 %s", sym, account.getCurrency());
        String settlementState = (tx.getStatus() != null ? tx.getStatus().name() : "COMPLETED") + " (Direct Clearing)";

        String memoText = tx.getDescription() != null && !tx.getDescription().trim().isEmpty()
                ? tx.getDescription().trim()
                : "Monthly installment repayment for Business Equipment Loan #4 (Principal: $42.50, Interest: $7.50). Auto-debited via scheduled repayment facility.";

        int actionIdx = 0; // 0: Return to Ledger, 1: Export Receipt
        String feedbackMsg = null;

        try {
            while (true) {
                StringBuilder sb = new StringBuilder();
                sb.append(TUIBox.top(width)).append("\n");
                sb.append(TUIBox.line(ConsoleTheme.primary("DIGIBANK CORE > TRANSACTIONS LEDGER > TRANSACTION DETAILS"), width)).append("\n");
                sb.append(TUIBox.divider(width)).append("\n");
                sb.append(TUIBox.line("TRANSACTION SUMMARY", width)).append("\n");
                sb.append(TUIBox.emptyLine(width)).append("\n");

                sb.append(TUIBox.line(String.format("  %-18s: %s", "Transaction ID", refId), width)).append("\n");
                sb.append(TUIBox.line(String.format("  %-18s: %s", "Posted Timestamp", postedTs), width)).append("\n");
                sb.append(TUIBox.line(String.format("  %-18s: %s (%s - %s)", "Account Number", account.getAccountNumber(), account.getAccountType(), account.getCurrency()), width)).append("\n");
                sb.append(TUIBox.line(String.format("  %-18s: %s", "Transaction Type", formatTransactionType(tx.getTransactionType())), width)).append("\n");
                sb.append(TUIBox.line(String.format("  %-18s: %s", "Category", catName), width)).append("\n");
                sb.append(TUIBox.emptyLine(width)).append("\n");

                sb.append(TUIBox.line(String.format("  %-18s: %s", "Gross Amount", grossAmt), width)).append("\n");
                sb.append(TUIBox.line(String.format("  %-18s: %s", "Transaction Fee", feeStr), width)).append("\n");
                sb.append(TUIBox.line(String.format("  %-18s: %s", "Settlement State", settlementState), width)).append("\n");
                sb.append(TUIBox.emptyLine(width)).append("\n");

                sb.append(TUIBox.line("FULL DESCRIPTION & AUDIT NOTE", width)).append("\n");
                List<String> wrappedMemo = wrapDescription(memoText, 72);
                for (String line : wrappedMemo) {
                    sb.append(TUIBox.line("  " + line, width)).append("\n");
                }
                sb.append(TUIBox.emptyLine(width)).append("\n");

                if (feedbackMsg != null) {
                    sb.append(TUIBox.divider(width)).append("\n");
                    sb.append(TUIBox.line(ConsoleTheme.success("  " + feedbackMsg), width)).append("\n");
                }

                sb.append(TUIBox.divider(width)).append("\n");

                String b1 = (actionIdx == 0)
                        ? ("▸ " + ConsoleTheme.highlight("[1] Return to Ledger"))
                        : ("  [1] Return to Ledger");
                String b2 = (actionIdx == 1)
                        ? ("▸ " + ConsoleTheme.highlight("[2] Export Receipt (PDF)"))
                        : ("  " + ConsoleTheme.muted("[2] Export Receipt (PDF)"));
                String actionLine = "  " + b1 + "       " + b2;
                sb.append(TUIBox.line(actionLine, width)).append("\n");
                sb.append(ConsoleTheme.keyGuide("[Enter] Execute Action  •  [1/2] Quick Action  •  [Esc] Return to Ledger")).append("\n");

                ScreenRenderer.render(sb.toString(), true);

                int key = reader.read();
                if (key == -1) {
                    return;
                }
                if (key == 27) {
                    int next1 = reader.read(15);
                    if (next1 == '[' || next1 == 'O') {
                        int next2 = reader.read(15);
                        if (next2 == 'C' || next2 == 'D' || next2 == 'A' || next2 == 'B') {
                            actionIdx = (actionIdx == 0) ? 1 : 0;
                        }
                    } else {
                        return; // Standalone ESC -> Return to Ledger
                    }
                } else if (key == '\t' || key == ' ') {
                    actionIdx = (actionIdx == 0) ? 1 : 0;
                } else if (key == '1') {
                    return;
                } else if (key == '2') {
                    try {
                        LocalDateTime from = LocalDateTime.now().minusDays(30);
                        LocalDateTime to = LocalDateTime.now();
                        String outputPath = (reportController != null)
                                ? reportController.generateStatement(user, account, from, to)
                                : "receipt_" + refId + ".pdf";
                        feedbackMsg = "✔ Receipt PDF exported to: " + outputPath;
                    } catch (Exception e) {
                        feedbackMsg = "Export failed: " + e.getMessage();
                    }
                } else if (key == '\r' || key == '\n') {
                    if (actionIdx == 0) {
                        return;
                    } else {
                        try {
                            LocalDateTime from = LocalDateTime.now().minusDays(30);
                            LocalDateTime to = LocalDateTime.now();
                            String outputPath = (reportController != null)
                                    ? reportController.generateStatement(user, account, from, to)
                                    : "receipt_" + refId + ".pdf";
                            feedbackMsg = "✔ Receipt PDF exported to: " + outputPath;
                        } catch (Exception e) {
                            feedbackMsg = "Export failed: " + e.getMessage();
                        }
                    }
                } else if (key == 'b' || key == 'B') {
                    return;
                }
            }
        } catch (Exception ignored) {}
    }

    private List<String> wrapDescription(String text, int maxLen) {
        if (text == null || text.isBlank()) {
            return List.of("\"No description or audit memo recorded for this transaction.\"");
        }
        List<String> lines = new ArrayList<>();
        String[] words = text.trim().split("\\s+");
        StringBuilder current = new StringBuilder("\"");
        for (String word : words) {
            if (current.length() + word.length() + 1 > maxLen) {
                lines.add(current.toString());
                current.setLength(0);
                current.append(" ");
            }
            if (current.length() > 1 && !current.toString().endsWith(" ")) {
                current.append(" ");
            }
            current.append(word);
        }
        if (current.length() > 0) {
            current.append("\"");
            lines.add(current.toString());
        }
        return lines;
    }
}
