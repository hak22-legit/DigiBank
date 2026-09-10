package com.bank.console.screens;

import com.bank.console.ControllerFactory;
import com.bank.console.ScreenNavigator;
import com.bank.console.TUISession;
import com.bank.console.components.ConsoleFormatter;
import com.bank.console.components.TUIBox;
import com.bank.console.components.TUILayout;
import com.bank.console.theme.ConsoleTheme;
import com.bank.controller.AccountController;
import com.bank.controller.TransactionController;
import com.bank.model.TransactionView;
import com.bank.model.dto.AccountDTO;
import com.bank.model.dto.UserDTO;
import com.bank.model.entity.Category;
import com.bank.model.entity.Transaction;
import com.bank.model.entity.User;
import com.bank.model.enums.HistoryFilter;
import com.bank.model.enums.TransactionDirection;
import com.bank.model.enums.TransactionStatus;
import com.bank.security.SessionManager;
import org.jline.terminal.Attributes;
import org.jline.terminal.Terminal;
import org.jline.utils.NonBlockingReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * SCREEN 7: TRANSACTION LEDGER & HISTORY (82 Columns)
 * Non-blocking raw single-key interception (N/P/F/B/Esc), in-place filter cycling, and zero dual-prompts.
 */
public class TransactionHistoryScreen implements Screen {
    private static final Logger logger = LoggerFactory.getLogger(TransactionHistoryScreen.class);

    private final TransactionController transactionController;
    private final AccountController accountController;

    public TransactionHistoryScreen() {
        this(ControllerFactory.getTransactionController(), ControllerFactory.getAccountController());
    }

    public TransactionHistoryScreen(TransactionController transactionController) {
        this(transactionController, ControllerFactory.getAccountController());
    }

    public TransactionHistoryScreen(TransactionController transactionController, AccountController accountController) {
        this.transactionController = transactionController;
        this.accountController = accountController;
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
            System.out.println(" Failed to load accounts: " + e.getMessage());
            navigator.pop();
            return;
        }

        if (accounts == null || accounts.isEmpty()) {
            System.out.println(" No accounts found to view transaction history.");
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

        HistoryFilter filter = HistoryFilter.ALL;
        int currentPage = 1;
        int pageSize = 5;

        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

        Terminal terminal = session.getTerminal();
        Attributes origAttributes = terminal.enterRawMode();
        NonBlockingReader reader = terminal.reader();

        boolean firstRender = true;

        try {
            while (true) {
                List<TransactionView> txViews = null;
                try {
                    txViews = transactionController.getTransactionHistory(selectedAcc.getAccountId(), filter, userEntity);
                } catch (Exception ignored) {}

                int totalRecords = txViews != null ? txViews.size() : 0;
                int totalPages = Math.max(1, (int) Math.ceil((double) totalRecords / pageSize));
                if (currentPage > totalPages) currentPage = totalPages;
                if (currentPage < 1) currentPage = 1;

                int startIdx = (currentPage - 1) * pageSize;
                int endIdx = Math.min(startIdx + pageSize, totalRecords);

                // Build Screen Frame
                StringBuilder sb = new StringBuilder();
                if (firstRender) {
                    sb.append(ConsoleTheme.CLEAR_SCREEN);
                } else {
                    sb.append("\u001B[H"); // Cursor Home
                }

                String headerTitle = String.format("DIGIBANK CORE > TRANSACTIONS LEDGER (%s - %s)",
                        selectedAcc.getAccountNumber(), selectedAcc.getCurrency());

                sb.append(TUIBox.top(width)).append("\n");
                sb.append(TUIBox.line(ConsoleTheme.primary(headerTitle), width)).append("\n");
                sb.append(TUIBox.divider(width)).append("\n");
                sb.append(TUIBox.line("DATE & TIME        TYPE         CATEGORY       DESCRIPTION     AMOUNT    STAT", width)).append("\n");
                sb.append(TUIBox.line("─────────────────  ───────────  ─────────────  ──────────────  ────────  ────", width)).append("\n");

                if (totalRecords == 0) {
                    sb.append(TUIBox.line(ConsoleTheme.muted("  No transactions found for this account/filter."), width)).append("\n");
                    for (int i = 1; i < pageSize; i++) {
                        sb.append(TUIBox.emptyLine(width)).append("\n");
                    }
                } else {
                    for (int i = startIdx; i < endIdx; i++) {
                        TransactionView tv = txViews.get(i);
                        Transaction tx = tv.getTransaction();

                        String dateStr = tx.getTransactionDate() != null ? tx.getTransactionDate().format(dtf) : "2026-09-08 12:00";
                        String typeStr = tx.getTransactionType() != null ? tx.getTransactionType().name() : "TRANSFER";
                        if (typeStr.length() > 11) typeStr = typeStr.substring(0, 11);

                        String catName = "Other";
                        if (tx.getCategoryId() != null && categoryNames.containsKey(tx.getCategoryId())) {
                            catName = categoryNames.get(tx.getCategoryId());
                        } else if (tx.getDescription() != null) {
                            String dLower = tx.getDescription().toLowerCase();
                            if (dLower.contains("bill") || dLower.contains("water") || dLower.contains("electric")) catName = "Bills";
                            else if (dLower.contains("food") || dLower.contains("market")) catName = "Food";
                            else if (dLower.contains("movie") || dLower.contains("entertain")) catName = "Entertainment";
                        }
                        if (catName.length() > 13) catName = catName.substring(0, 13);

                        String desc = tx.getDescription() != null ? tx.getDescription() : "-";
                        if (desc.length() > 14) desc = desc.substring(0, 14);

                        BigDecimal amt = tx.getAmount() != null ? tx.getAmount() : BigDecimal.ZERO;
                        boolean isIncome = tv.getDirection() == TransactionDirection.INCOME;
                        String sign = isIncome ? "+" : "-";
                        String amountFormatted = String.format("%s$%s", sign, ConsoleFormatter.formatCurrency(amt).replace("$", "").trim());
                        if (amountFormatted.length() > 9) amountFormatted = amountFormatted.substring(0, 9);

                        String statStr = (tx.getStatus() == TransactionStatus.COMPLETED) ? "COMP" : "PEND";

                        String row = String.format("%-17s  %-11s  %-13s  %-14s  %9s  %-4s",
                                dateStr, typeStr, catName, desc, amountFormatted, statStr);
                        sb.append(TUIBox.line(row, width)).append("\n");
                    }

                    // Fill remaining lines to maintain constant box height
                    for (int i = endIdx - startIdx; i < pageSize; i++) {
                        sb.append(TUIBox.emptyLine(width)).append("\n");
                    }
                }

                sb.append(TUIBox.divider(width)).append("\n");
                String filterBadge = ConsoleTheme.highlight("[" + filter.name() + "]");
                sb.append(TUIBox.line(String.format("Ledger: Page %d of %d | Filter: %s", currentPage, totalPages, filterBadge), width)).append("\n");
                sb.append(TUIBox.emptyLine(width)).append("\n");
                sb.append(TUIBox.line(" [N] Next Page   [P] Previous Page   [F] Cycle Filter   [B/Esc] Back", width)).append("\n");
                sb.append(TUIBox.bottom(width)).append("\n");
                sb.append(ConsoleTheme.muted("  [N] Next Page  •  [P] Prev Page  •  [F] Cycle Filter  •  [Esc] Back")).append("\n");

                System.out.print(sb.toString());
                System.out.flush();
                firstRender = false;

                // Non-blocking single key interception
                int ch = reader.read();

                if (ch == 27) { // ESC or Escape Sequence
                    int next = reader.read(60);
                    if (next == -2 || next == -1) {
                        // Bare ESC -> Exit
                        terminal.setAttributes(origAttributes);
                        navigator.pop();
                        return;
                    }
                    if (next == '[' || next == 'O') {
                        int code = reader.read();
                        if (code == 'C' || code == 'B') { // Right or Down -> Next page
                            if (currentPage < totalPages) currentPage++;
                        } else if (code == 'D' || code == 'A') { // Left or Up -> Prev page
                            if (currentPage > 1) currentPage--;
                        }
                    }
                } else if (ch == 'n' || ch == 'N') { // Next Page
                    if (currentPage < totalPages) currentPage++;
                } else if (ch == 'p' || ch == 'P') { // Previous Page
                    if (currentPage > 1) currentPage--;
                } else if (ch == 'f' || ch == 'F') { // Cycle Filter
                    filter = switch (filter) {
                        case ALL -> HistoryFilter.INCOME;
                        case INCOME -> HistoryFilter.OUTCOME;
                        case OUTCOME -> HistoryFilter.ALL;
                    };
                    currentPage = 1;
                } else if (ch == 'b' || ch == 'B' || ch == '0' || ch == '\r' || ch == '\n') { // Back
                    if (ch == 'b' || ch == 'B' || ch == '0') {
                        terminal.setAttributes(origAttributes);
                        navigator.pop();
                        return;
                    }
                } else if (ch == 3) { // Ctrl+C
                    session.clearScreen();
                    System.exit(0);
                }
            }
        } catch (IOException e) {
            logger.error("Error reading key on transaction history screen", e);
        } finally {
            terminal.setAttributes(origAttributes);
        }
    }
}
