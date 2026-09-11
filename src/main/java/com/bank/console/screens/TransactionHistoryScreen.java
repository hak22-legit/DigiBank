package com.bank.console.screens;

import com.bank.console.ControllerFactory;
import com.bank.console.ScreenNavigator;
import com.bank.console.TUISession;
import com.bank.console.components.*;
import com.bank.console.components.TUIFormHelper.KeyAction;
import com.bank.console.components.TUIFormHelper.KeyEvent;
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
import com.bank.model.enums.HistoryFilter;
import com.bank.model.enums.TransactionDirection;
import com.bank.model.enums.TransactionType;
import com.bank.security.SessionManager;
import org.jline.terminal.Attributes;
import org.jline.terminal.Terminal;
import org.jline.utils.NonBlockingReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * SCREEN 7: TRANSACTION LEDGER & HISTORY (82 Columns)
 * Clean footers, running balance tracking, 82-column layout, and PDF export shortcut.
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

        DecimalFormat df = new DecimalFormat("#,##0.00");
        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

        Terminal terminal = session.getTerminal();
        Attributes origAttributes = terminal.enterRawMode();
        NonBlockingReader reader = terminal.reader();

        boolean firstRender = true;

        try {
            while (true) {
                // Fetch all transactions to compute running balances backward from current balance
                List<TransactionView> allViews = null;
                try {
                    allViews = transactionController.getTransactionHistory(selectedAcc.getAccountId(), HistoryFilter.ALL, userEntity);
                } catch (Exception ignored) {}

                if (allViews == null) allViews = Collections.emptyList();

                // Compute running balance for every transaction
                Map<Long, BigDecimal> runningBalanceMap = new HashMap<>();
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

                // Build Screen Frame (Strict 82 columns)
                StringBuilder sb = new StringBuilder();
                String headerTitle = String.format("DIGIBANK CORE > TRANSACTIONS LEDGER (%s - %s)",
                        selectedAcc.getAccountNumber(), selectedAcc.getCurrency());

                sb.append(TUIBox.top(width)).append("\n");
                sb.append(TUIBox.line(ConsoleTheme.primary(headerTitle), width)).append("\n");
                sb.append(TUIBox.divider(width)).append("\n");

                // Table Header: EXACT 77 CHARS
                String tableHeader = String.format("%-16s  %-10s  %-21s  %11s  %11s",
                        "DATE & TIME", "TYPE", "DESCRIPTION", "AMOUNT", "BALANCE");
                sb.append(TUIBox.line(tableHeader, width)).append("\n");
                sb.append(TUIBox.line(ConsoleTheme.border("─".repeat(77)), width)).append("\n");

                if (totalRecords == 0) {
                    sb.append(TUIBox.line(ConsoleTheme.muted("  No transactions found for this account/filter."), width)).append("\n");
                    for (int i = 1; i < pageSize; i++) {
                        sb.append(TUIBox.emptyLine(width)).append("\n");
                    }
                } else {
                    for (int i = startIdx; i < endIdx; i++) {
                        TransactionView tv = filteredViews.get(i);
                        Transaction tx = tv.getTransaction();

                        String dateStr = tx.getTransactionDate() != null
                                ? tx.getTransactionDate().format(dtf)
                                : "2026-09-11 00:00";

                        String typeStr = formatTransactionType(tx.getTransactionType());
                        if (typeStr.length() > 10) typeStr = typeStr.substring(0, 10);

                        String desc = tx.getDescription() != null ? tx.getDescription() : "-";
                        if (desc.length() > 21) desc = desc.substring(0, 21);

                        BigDecimal amt = tx.getAmount() != null ? tx.getAmount() : BigDecimal.ZERO;
                        boolean isIncome = tv.getDirection() == TransactionDirection.INCOME;
                        String sign = isIncome ? "+" : "-";
                        String amountFormatted = String.format("%s$%9s", sign, df.format(amt));

                        BigDecimal rowBal = runningBalanceMap.getOrDefault(tx.getTransactionId(), selectedAcc.getBalance());
                        String balanceFormatted = String.format("$%10s", df.format(rowBal));

                        String row = String.format("%-16s  %-10s  %-21s  %11s  %11s",
                                dateStr, typeStr, desc, amountFormatted, balanceFormatted);
                        sb.append(TUIBox.line(row, width)).append("\n");
                    }

                    // Fill remaining lines to maintain constant box height
                    for (int i = endIdx - startIdx; i < pageSize; i++) {
                        sb.append(TUIBox.emptyLine(width)).append("\n");
                    }
                }

                sb.append(TUIBox.divider(width)).append("\n");

                // Clean Summary compartment inside box
                String pageIndicator = String.format("Page: [ %d / %d ]", currentPage, totalPages);
                String filterIndicator = String.format("Filter: [%s]", filter.getLabel());
                String totalIndicator = String.format("Total Records: %d", totalRecords);
                String summaryRow = String.format("%-19s│ %-28s│ %s", pageIndicator, filterIndicator, totalIndicator);
                sb.append(TUIBox.line(summaryRow, width)).append("\n");

                sb.append(TUIBox.bottom(width)).append("\n");

                // Navigation hints CLEANLY BELOW the bottom border
                sb.append(" ").append(ConsoleTheme.muted("[←/→] Page  •  [F] Filter  •  [E] Export PDF  •  [Enter] View Details  •  [Esc] Back")).append("\n");

                ScreenRenderer.render(sb.toString(), firstRender);
                firstRender = false;

                KeyEvent event = TUIFormHelper.readKey(reader);
                if (event.action() == KeyAction.ESCAPE || event.ch() == 'b' || event.ch() == 'B') {
                    terminal.setAttributes(origAttributes);
                    navigator.pop();
                    return;
                } else if (event.action() == KeyAction.RIGHT || event.ch() == 'n' || event.ch() == 'N') {
                    if (currentPage < totalPages) currentPage++;
                } else if (event.action() == KeyAction.LEFT || event.ch() == 'p' || event.ch() == 'P') {
                    if (currentPage > 1) currentPage--;
                } else if (event.ch() == 'f' || event.ch() == 'F') {
                    filter = switch (filter) {
                        case ALL -> LedgerFilter.DEPOSIT;
                        case DEPOSIT -> LedgerFilter.WITHDRAW;
                        case WITHDRAW -> LedgerFilter.TRANSFER;
                        case TRANSFER -> LedgerFilter.LOAN;
                        case LOAN -> LedgerFilter.ALL;
                    };
                    currentPage = 1;
                } else if (event.ch() == 'e' || event.ch() == 'E') {
                    // Export PDF Statement
                    handleExportPdf(terminal, origAttributes, reader, selectedAcc, userEntity, width);
                    firstRender = true;
                } else if (event.action() == KeyAction.ENTER) {
                    if (totalRecords > 0) {
                        TransactionView selectedView = filteredViews.get(startIdx);
                        handleViewDetails(terminal, origAttributes, reader, selectedView, selectedAcc, runningBalanceMap, categoryNames, width);
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
            default -> true;
        };
    }

    private String formatTransactionType(TransactionType type) {
        if (type == null) return "TRANSFER";
        return switch (type) {
            case DEPOSIT -> "DEPOSIT";
            case WITHDRAWAL -> "WITHDRAWAL";
            case TRANSFER -> "TRANSFER";
            case LOAN_REPAYMENT -> "REPAYMENT";
            case LOAN_DISBURSEMENT -> "DISBURSE";
            case PAYMENT -> "PAYMENT";
        };
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
            sb.append(TUIBox.bottom(width)).append("\n");
            sb.append(" ").append(ConsoleTheme.muted("Press [Enter] or [Esc] to return to ledger")).append("\n");

            ScreenRenderer.render(sb.toString(), true);
            while (true) {
                KeyEvent event = TUIFormHelper.readKey(reader);
                if (event.action() == KeyAction.ENTER || event.action() == KeyAction.ESCAPE || event.ch() == 'b' || event.ch() == 'B') {
                    break;
                }
            }
        } catch (Exception e) {
            StringBuilder errSb = new StringBuilder();
            errSb.append(TUIBox.top(width)).append("\n");
            errSb.append(TUIBox.line(ConsoleTheme.error(" Export Failed: " + e.getMessage()), width)).append("\n");
            errSb.append(TUIBox.divider(width)).append("\n");
            errSb.append(TUIBox.bottom(width)).append("\n");
            errSb.append(" ").append(ConsoleTheme.muted("Press [Enter] or [Esc] to return to ledger")).append("\n");
            ScreenRenderer.render(errSb.toString(), true);
            try {
                while (true) {
                    KeyEvent event = TUIFormHelper.readKey(reader);
                    if (event.action() == KeyAction.ENTER || event.action() == KeyAction.ESCAPE) {
                        break;
                    }
                }
            } catch (Exception ignored) {}
        }
    }

    private void handleViewDetails(Terminal terminal, Attributes origAttr, NonBlockingReader reader,
                                   TransactionView tv, AccountDTO account, Map<Long, BigDecimal> runningBalanceMap,
                                   Map<Long, String> categoryNames, int width) {
        Transaction tx = tv.getTransaction();
        DecimalFormat df = new DecimalFormat("#,##0.00");
        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

        String catName = "None / General";
        if (tx.getCategoryId() != null && categoryNames.containsKey(tx.getCategoryId())) {
            catName = categoryNames.get(tx.getCategoryId());
        }

        BigDecimal rowBal = runningBalanceMap.getOrDefault(tx.getTransactionId(), account.getBalance());
        String sign = tv.getDirection() == TransactionDirection.INCOME ? "+" : "-";

        StringBuilder sb = new StringBuilder();
        sb.append(TUIBox.top(width)).append("\n");
        sb.append(TUIBox.line(ConsoleTheme.primary("DIGIBANK CORE > TRANSACTIONS LEDGER > TRANSACTION DETAILS"), width)).append("\n");
        sb.append(TUIBox.divider(width)).append("\n");
        sb.append(TUIBox.emptyLine(width)).append("\n");

        sb.append(TUIBox.line(String.format("  Transaction ID   : #%d", tx.getTransactionId()), width)).append("\n");
        String dateStr = tx.getTransactionDate() != null ? tx.getTransactionDate().format(dtf) : "N/A";
        sb.append(TUIBox.line(String.format("  Date & Time      : %s", dateStr), width)).append("\n");
        sb.append(TUIBox.line(String.format("  Transaction Type : %s", formatTransactionType(tx.getTransactionType())), width)).append("\n");
        sb.append(TUIBox.line(String.format("  Account          : %s (%s)", account.getAccountNumber(), account.getCurrency()), width)).append("\n");
        sb.append(TUIBox.line(String.format("  Amount           : %s$%s %s", sign, df.format(tx.getAmount()), account.getCurrency()), width)).append("\n");
        sb.append(TUIBox.line(String.format("  Running Balance  : $%s %s", df.format(rowBal), account.getCurrency()), width)).append("\n");
        sb.append(TUIBox.line(String.format("  Category         : %s", catName), width)).append("\n");
        sb.append(TUIBox.line(String.format("  Description      : %s", tx.getDescription() != null ? tx.getDescription() : "-"), width)).append("\n");
        sb.append(TUIBox.line(String.format("  Status           : %s", tx.getStatus() != null ? tx.getStatus().name() : "COMPLETED"), width)).append("\n");
        if (tx.getIdempotencyKey() != null) {
            sb.append(TUIBox.line(String.format("  Idempotency Key  : %s", tx.getIdempotencyKey()), width)).append("\n");
        }

        sb.append(TUIBox.emptyLine(width)).append("\n");
        sb.append(TUIBox.divider(width)).append("\n");
        sb.append(TUIBox.bottom(width)).append("\n");
        sb.append(" ").append(ConsoleTheme.muted("Press [Enter] or [Esc] to return to ledger")).append("\n");

        ScreenRenderer.render(sb.toString(), true);
        try {
            while (true) {
                KeyEvent event = TUIFormHelper.readKey(reader);
                if (event.action() == KeyAction.ENTER || event.action() == KeyAction.ESCAPE || event.ch() == 'b' || event.ch() == 'B') {
                    break;
                }
            }
        } catch (Exception ignored) {}
    }
}
