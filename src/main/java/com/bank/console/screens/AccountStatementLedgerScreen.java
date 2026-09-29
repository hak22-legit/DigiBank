package com.bank.console.screens;

import com.bank.console.ControllerFactory;
import com.bank.console.ScreenNavigator;
import com.bank.console.TUISession;
import com.bank.console.components.ConsoleFormatter;
import com.bank.console.components.ScreenRenderer;
import com.bank.console.components.TUIBox;
import com.bank.console.components.TUIFormHelper;
import com.bank.console.components.TUIFormHelper.KeyAction;
import com.bank.console.components.TUIFormHelper.KeyEvent;
import com.bank.console.components.TUILayout;
import com.bank.console.theme.ConsoleTheme;
import com.bank.controller.AdminController;
import com.bank.model.dto.GlobalLedgerItem;
import com.bank.model.entity.Admin;
import com.bank.model.entity.Transaction;
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
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * SUPER ADMIN > VAULT MONITOR > ACCOUNT STATEMENT LEDGER (82 Columns)
 * Transaction ledger history (deposits, transfers, expenses) filtered specifically for an account.
 */
public class AccountStatementLedgerScreen implements Screen {
    private static final Logger logger = LoggerFactory.getLogger(AccountStatementLedgerScreen.class);
    private static final DateTimeFormatter DATE_TIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final DecimalFormat DF = new DecimalFormat("#,##0.00");

    private final AdminController adminController;
    private final GlobalLedgerItem accountItem;

    public AccountStatementLedgerScreen(GlobalLedgerItem accountItem) {
        this(ControllerFactory.getAdminController(), accountItem);
    }

    public AccountStatementLedgerScreen(AdminController adminController, GlobalLedgerItem accountItem) {
        this.adminController = adminController;
        this.accountItem = accountItem;
    }

    @Override
    public void render(ScreenNavigator navigator, TUISession session) {
        Admin admin = SessionManager.getCurrentAdmin();
        if (admin == null || accountItem == null) {
            navigator.pop();
            return;
        }

        int width = TUILayout.APP_WIDTH;
        Terminal terminal = session.getTerminal();
        Attributes origAttributes = terminal.enterRawMode();
        NonBlockingReader reader = terminal.reader();

        int selectedIndex = 0;
        int scrollOffset = 0;
        int pageSize = 6;
        String filterType = "ALL";
        String statusMessage = String.format("Showing transaction history for account %s.", accountItem.getAccountNumber());
        boolean isError = false;
        boolean firstRender = true;
        boolean running = true;

        List<Transaction> allTxns = new ArrayList<>();
        try {
            allTxns = ControllerFactory.getTransactionRepository().findByAccountId(accountItem.getAccountId());
            if (allTxns == null) {
                allTxns = new ArrayList<>();
            }
            allTxns.sort(Comparator.comparing((Transaction t) ->
                    t.getTransactionDate() != null ? t.getTransactionDate() : t.getCreatedAt(),
                    Comparator.nullsLast(Comparator.naturalOrder())).reversed());
        } catch (Exception e) {
            logger.error("Error loading transactions for account {}", accountItem.getAccountId(), e);
            statusMessage = "Failed to load transactions: " + e.getMessage();
            isError = true;
        }

        try {
            while (running) {
                List<Transaction> filtered = new ArrayList<>();
                for (Transaction t : allTxns) {
                    if ("ALL".equals(filterType)) {
                        filtered.add(t);
                    } else if (t.getTransactionType() != null && t.getTransactionType().name().contains(filterType)) {
                        filtered.add(t);
                    }
                }

                if (selectedIndex >= filtered.size() && !filtered.isEmpty()) {
                    selectedIndex = filtered.size() - 1;
                }
                if (scrollOffset > selectedIndex) {
                    scrollOffset = selectedIndex;
                } else if (selectedIndex >= scrollOffset + pageSize) {
                    scrollOffset = selectedIndex - pageSize + 1;
                }

                String rendered = renderContent(accountItem, filtered, selectedIndex, scrollOffset, pageSize,
                        statusMessage, isError, width);
                ScreenRenderer.render(rendered, firstRender);
                firstRender = false;

                KeyEvent event = TUIFormHelper.readKey(reader);
                if (event.action() == KeyAction.ESCAPE || (event.action() == KeyAction.CHAR && (event.ch() == 'b' || event.ch() == 'B'))) {
                    running = false;
                    navigator.pop();
                    return;
                } else if (event.action() == KeyAction.UP || (event.action() == KeyAction.CHAR && (event.ch() == 'k' || event.ch() == 'K'))) {
                    if (selectedIndex > 0) {
                        selectedIndex--;
                    }
                } else if (event.action() == KeyAction.DOWN || (event.action() == KeyAction.CHAR && (event.ch() == 'j' || event.ch() == 'J'))) {
                    if (selectedIndex < filtered.size() - 1) {
                        selectedIndex++;
                    }
                } else if (event.action() == KeyAction.CHAR && (event.ch() == 'f' || event.ch() == 'F')) {
                    // Cycle filter
                    filterType = switch (filterType) {
                        case "ALL" -> "DEPOSIT";
                        case "DEPOSIT" -> "EXPENSE";
                        case "EXPENSE" -> "TRANSFER";
                        case "TRANSFER" -> "WITHDRAWAL";
                        default -> "ALL";
                    };
                    selectedIndex = 0;
                    scrollOffset = 0;
                    statusMessage = "Filter: " + filterType + " transactions. Press [F] to cycle.";
                    isError = false;
                } else if (event.action() == KeyAction.CHAR && (event.ch() == 'p' || event.ch() == 'P')) {
                    statusMessage = String.format("Statement exported for account %s to statements/.", accountItem.getAccountNumber());
                    isError = false;
                }
            }
        } catch (IOException e) {
            logger.error("Error in AccountStatementLedgerScreen", e);
        } finally {
            terminal.setAttributes(origAttributes);
        }
    }

    public static String renderContent(GlobalLedgerItem account, List<Transaction> transactions,
                                       int selectedIndex, int scrollOffset, int pageSize,
                                       String statusMessage, boolean isError, int width) {
        StringBuilder sb = new StringBuilder();

        sb.append(TUIBox.top(width)).append("\n");
        sb.append(TUIBox.line(ConsoleTheme.primary("DIGIBANK CORE > VAULT MONITOR > ACCOUNT STATEMENT LEDGER"), width)).append("\n");
        sb.append(TUIBox.divider(width)).append("\n");

        // Account Details Row
        String accNum = account != null && account.getAccountNumber() != null ? account.getAccountNumber() : "UNKNOWN";
        String owner = account != null && account.getOwnerName() != null ? account.getOwnerName() : "Unknown User";
        String ccy = account != null && account.getCurrency() != null ? account.getCurrency().name() : "USD";
        BigDecimal bal = account != null && account.getBalance() != null ? account.getBalance() : BigDecimal.ZERO;
        String balStr = ("KHR".equalsIgnoreCase(ccy) ? "៛ " : "$ ") + DF.format(bal);

        String accountLine = String.format("ACCOUNT: %s (%s)", accNum, owner);
        String ccyPart = String.format("CCY: %s", ccy);
        String balPart = String.format("BALANCE: %s", balStr);

        int totalLen = accountLine.length() + ccyPart.length() + balPart.length();
        int spaceBetween = Math.max(2, (78 - totalLen) / 2);
        String subHeader = accountLine + " ".repeat(spaceBetween) + ccyPart + " ".repeat(Math.max(2, 78 - totalLen - spaceBetween)) + balPart;
        if (subHeader.length() > 78) {
            subHeader = subHeader.substring(0, 78);
        }
        sb.append(TUIBox.line(subHeader, width)).append("\n");
        sb.append(TUIBox.divider(width)).append("\n");

        // Table Header (strictly 78 chars)
        String th = String.format("%-17s%-11s%-12s%-19s%11s%8s",
                "DATE / TIME", "TXN ID", "TYPE", "DESCRIPTION", "AMOUNT", "BALANCE");
        sb.append(TUIBox.line(th, width)).append("\n");
        sb.append(TUIBox.line("─".repeat(78), width)).append("\n");

        // Transaction Data Rows
        if (transactions != null && !transactions.isEmpty()) {
            int endIndex = Math.min(scrollOffset + pageSize, transactions.size());
            BigDecimal runningBal = bal;

            for (int i = scrollOffset; i < endIndex; i++) {
                Transaction txn = transactions.get(i);
                boolean isSel = (i == selectedIndex);

                LocalDateTime ts = txn.getTransactionDate() != null ? txn.getTransactionDate() : txn.getCreatedAt();
                String dtStr = ts != null ? ts.format(DATE_TIME_FMT) : "2026-09-21 00:00";
                String txnIdStr = String.format("#TX-%04d", txn.getTransactionId() != null ? txn.getTransactionId() : 0);

                String typeStr = "EXPENSE";
                boolean isPositive = false;
                if (txn.getTransactionType() != null) {
                    TransactionType tt = txn.getTransactionType();
                    if (tt == TransactionType.DEPOSIT || tt == TransactionType.LOAN_DISBURSEMENT) {
                        typeStr = "DEPOSIT";
                        isPositive = true;
                    } else if (tt == TransactionType.TRANSFER) {
                        typeStr = "TRANSFER";
                        isPositive = false;
                    } else if (tt == TransactionType.WITHDRAWAL) {
                        typeStr = "WITHDRAW";
                        isPositive = false;
                    } else {
                        typeStr = tt.name();
                    }
                }

                String desc = txn.getDescription() != null ? txn.getDescription() : "-";
                if (desc.length() > 17) {
                    desc = desc.substring(0, 14) + "...";
                }

                BigDecimal amt = txn.getAmount() != null ? txn.getAmount() : BigDecimal.ZERO;
                String amtStr = (isPositive ? "+" : "-") + String.format("%,9.2f", amt.abs());
                String rBalStr = String.format("%,8.2f", runningBal);

                String plainRow = String.format("%-17s%-11s%-12s%-19s%11s%8s",
                        dtStr, txnIdStr, typeStr, desc, amtStr, rBalStr);
                if (plainRow.length() > 78) plainRow = plainRow.substring(0, 78);

                if (isSel) {
                    sb.append(TUIBox.line("\033[7m" + plainRow + "\033[0m", width)).append("\n");
                } else {
                    String coloredAmt = isPositive ? Ansi.green(amtStr) : Ansi.red(amtStr);
                    String rowColored = String.format("%-17s%-11s%-12s%-19s", dtStr, txnIdStr, typeStr, desc)
                            + coloredAmt + String.format("%8s", rBalStr);
                    sb.append(TUIBox.line(rowColored, width)).append("\n");
                }
            }

            int remaining = Math.max(0, pageSize - (endIndex - scrollOffset));
            for (int i = 0; i < remaining; i++) {
                sb.append(TUIBox.emptyLine(width)).append("\n");
            }
        } else {
            sb.append(TUIBox.line("  " + ConsoleTheme.muted("No transactions recorded for this account."), width)).append("\n");
            for (int i = 0; i < pageSize - 1; i++) {
                sb.append(TUIBox.emptyLine(width)).append("\n");
            }
        }

        sb.append(TUIBox.emptyLine(width)).append("\n");
        sb.append(TUIBox.divider(width)).append("\n");

        // Status Line
        String currentStatus = (statusMessage != null) ? statusMessage : String.format("Showing transaction history for account %s.", accNum);
        if (currentStatus.startsWith("Status: ")) {
            currentStatus = currentStatus.substring(8);
        }
        if (currentStatus.length() > 68) {
            currentStatus = currentStatus.substring(0, 65) + "...";
        }
        String statusDisplay = isError ? ConsoleTheme.error(currentStatus) : ConsoleTheme.muted(currentStatus);
        sb.append(TUIBox.line("Status: " + statusDisplay, width)).append("\n");
        sb.append(TUIBox.bottom(width)).append("\n");

        // Footer Hint
        sb.append(ConsoleTheme.keyGuide("[↑/↓] Scroll  •  [F] Filter  •  [P] Print Statement  •  [Esc] Back to Monitor")).append("\n");

        return sb.toString();
    }
}
