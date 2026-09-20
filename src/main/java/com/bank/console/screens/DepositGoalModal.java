package com.bank.console.screens;

import com.bank.console.ControllerFactory;
import com.bank.console.components.ScreenRenderer;
import com.bank.console.components.TUIBox;
import com.bank.console.components.TUIFormHelper;
import com.bank.console.components.TUIFormHelper.KeyAction;
import com.bank.console.components.TUIFormHelper.KeyEvent;
import com.bank.console.components.TUILayout;
import com.bank.console.theme.ConsoleTheme;
import com.bank.controller.AccountController;
import com.bank.controller.SavingGoalController;
import com.bank.database.DatabaseConnection;
import com.bank.model.dto.AccountDTO;
import com.bank.model.entity.Account;
import com.bank.model.entity.SavingGoal;
import com.bank.model.entity.Transaction;
import com.bank.model.entity.User;
import com.bank.model.enums.TransactionStatus;
import com.bank.model.enums.TransactionType;
import org.jline.terminal.Attributes;
import org.jline.terminal.Terminal;
import org.jline.utils.NonBlockingReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.sql.Connection;
import java.text.DecimalFormat;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Interactive 82-column modal to deposit funds contextually into a selected Savings Goal.
 */
public class DepositGoalModal {
    private static final Logger logger = LoggerFactory.getLogger(DepositGoalModal.class);

    public static boolean depositToGoal(Terminal terminal, Attributes origAttr, NonBlockingReader reader,
                                        SavingGoal goal, List<AccountDTO> accounts,
                                        SavingGoalController savingGoalController,
                                        AccountController accountController,
                                        User user, int width) {
        if (goal == null || accounts == null || accounts.isEmpty()) {
            return false;
        }

        DecimalFormat df = new DecimalFormat("#,##0.00");
        AccountDTO sourceAccount = accounts.get(0);

        // Calculate a reasonable default deposit: min of $100 or remaining
        BigDecimal target = goal.getTargetAmount() != null ? goal.getTargetAmount() : BigDecimal.ZERO;
        BigDecimal current = goal.getCurrentAmount() != null ? goal.getCurrentAmount() : BigDecimal.ZERO;
        BigDecimal remaining = target.subtract(current).max(BigDecimal.ZERO);
        BigDecimal defaultAmt = remaining.compareTo(BigDecimal.ZERO) > 0
                ? remaining.min(new BigDecimal("100.00"))
                : new BigDecimal("50.00");

        StringBuilder amountBuf = new StringBuilder(df.format(defaultAmt).replace(",", ""));

        // Fields:
        // 0: Debit Source Account (modal selector)
        // 1: Deposit Amount
        // 2: Action Bar ([1] Authorize & Transfer Funds, [2] Cancel & Return)
        int focusedField = 1; // Default focus to amount
        int actionIdx = 0;    // 0: Authorize, 1: Cancel

        String statusMessage = "Ready. Enter deposit amount and confirm transfer.";
        boolean isErrorStatus = false;
        boolean firstRender = true;

        try {
            while (true) {
                BigDecimal depAmt = parseDecimal(amountBuf.toString(), BigDecimal.ZERO);
                BigDecimal newSaved = current.add(depAmt);
                int pct = (target.compareTo(BigDecimal.ZERO) > 0)
                        ? (int) Math.round((newSaved.doubleValue() / target.doubleValue()) * 100)
                        : 0;
                pct = Math.max(0, pct);
                int filledSlots = Math.min(16, (pct * 16) / 100);
                filledSlots = Math.max(0, filledSlots);
                int emptySlots = Math.max(0, 16 - filledSlots);
                String bar = "█".repeat(filledSlots) + "░".repeat(emptySlots);

                BigDecimal updatedBal = sourceAccount.getBalance().subtract(depAmt);

                StringBuilder sb = new StringBuilder();
                sb.append(TUIBox.top(width)).append("\n");
                sb.append(TUIBox.line(ConsoleTheme.primary("DIGIBANK CORE > SAVINGS GOALS > DEPOSIT TO GOAL"), width)).append("\n");
                sb.append(TUIBox.divider(width)).append("\n");
                sb.append(TUIBox.line("TARGET & FUNDING DETAILS", width)).append("\n");
                sb.append(TUIBox.emptyLine(width)).append("\n");

                int curPct = (target.compareTo(BigDecimal.ZERO) > 0)
                        ? (int) Math.round((current.doubleValue() / target.doubleValue()) * 100)
                        : 0;
                String destRow = String.format("%s (Target: $%s | Current: $%s - %d%%)",
                        goal.getName(), df.format(target), df.format(current), curPct);
                sb.append(TUIFormHelper.formatFieldRow("Target Goal", destRow, false, 24, 46)).append("\n");

                String accDisplay = String.format("%s (%s - Bal: $%s %s)",
                        sourceAccount.getAccountNumber(),
                        sourceAccount.getAccountType(),
                        df.format(sourceAccount.getBalance()),
                        sourceAccount.getCurrency());
                sb.append(TUIFormHelper.formatFieldRow("Debit Source Account", accDisplay, focusedField == 0, 24, 46)).append("\n");
                sb.append(TUIFormHelper.formatFieldRow("Deposit Amount", "$ " + amountBuf.toString(), focusedField == 1, 24, 46)).append("\n");
                sb.append(TUIBox.emptyLine(width)).append("\n");

                sb.append(TUIBox.divider(width)).append("\n");
                sb.append(TUIBox.line("POST-DEPOSIT IMPACT PREVIEW", width)).append("\n");
                sb.append(TUIBox.emptyLine(width)).append("\n");

                String progRow = String.format("  New Goal Progress    : $ %s / $ %s   [%s] %3d%%",
                        df.format(newSaved), df.format(target), bar, pct);
                String balRow = String.format("  Updated Account Bal  : $ %s %s (%s)",
                        df.format(updatedBal), sourceAccount.getCurrency(), sourceAccount.getAccountType());
                sb.append(TUIBox.line(progRow, width)).append("\n");
                sb.append(TUIBox.line(balRow, width)).append("\n");

                sb.append(TUIBox.divider(width)).append("\n");
                sb.append(TUIBox.line("ACTION", width)).append("\n");
                sb.append(TUIBox.emptyLine(width)).append("\n");

                String a1 = "[1] Authorize & Transfer Funds";
                String a2 = "[2] Cancel & Return";
                String act1 = (focusedField == 2 && actionIdx == 0) ? "▸ " + ConsoleTheme.highlight(a1) : "  " + a1;
                String act2 = (focusedField == 2 && actionIdx == 1) ? "▸ " + ConsoleTheme.highlight(a2) : "  " + a2;
                sb.append(TUIBox.line("  " + act1 + "                " + act2, width)).append("\n");

                sb.append(TUIBox.divider(width)).append("\n");

                String safeStatus = statusMessage;
                if (safeStatus.length() > 68) {
                    safeStatus = safeStatus.substring(0, 65) + "...";
                }
                String statusLine = isErrorStatus ? ConsoleTheme.error(safeStatus) : safeStatus;
                sb.append(TUIBox.line("Status: " + statusLine, width)).append("\n");
                sb.append(TUIBox.bottom(width)).append("\n");
                sb.append(ConsoleTheme.keyGuide("[Tab/↓] Next Field  •  [Enter] Confirm/Select  •  [1/2] Action  •  [Esc] Cancel")).append("\n");

                ScreenRenderer.render(sb.toString(), firstRender);
                firstRender = false;

                KeyEvent event = TUIFormHelper.readKey(reader);
                if (event.action() == KeyAction.ESCAPE) {
                    return false;
                } else if (event.action() == KeyAction.TAB || event.action() == KeyAction.DOWN) {
                    focusedField = (focusedField + 1) % 3;
                } else if (event.action() == KeyAction.SHIFT_TAB || event.action() == KeyAction.UP) {
                    focusedField = (focusedField - 1 + 3) % 3;
                } else if (focusedField == 2 && (event.action() == KeyAction.LEFT || event.action() == KeyAction.RIGHT)) {
                    actionIdx = (actionIdx == 0) ? 1 : 0;
                } else if (focusedField == 1 && event.action() == KeyAction.BACKSPACE) {
                    if (amountBuf.length() > 0) amountBuf.deleteCharAt(amountBuf.length() - 1);
                } else if (event.action() == KeyAction.ENTER) {
                    if (focusedField == 0) {
                        AccountDTO chosen = AccountSelectorModal.selectSenderAccount(
                                terminal, origAttr, reader, accounts, sourceAccount, width);
                        if (chosen != null) sourceAccount = chosen;
                        focusedField = 1;
                        firstRender = true;
                    } else if (focusedField == 2) {
                        if (actionIdx == 0) {
                            boolean ok = executeDeposit(user, goal, sourceAccount, depAmt, savingGoalController);
                            if (ok) return true;
                            statusMessage = "Transfer failed: Insufficient funds or invalid amount.";
                            isErrorStatus = true;
                        } else {
                            return false;
                        }
                    } else {
                        focusedField = (focusedField + 1) % 3;
                    }
                } else if (event.action() == KeyAction.DIGIT || event.action() == KeyAction.CHAR) {
                    char c = event.ch();
                    if (focusedField == 1) {
                        if ((c >= '0' && c <= '9') || (c == '.' && !amountBuf.toString().contains("."))) {
                            if (amountBuf.length() < 10) amountBuf.append(c);
                        }
                    } else if (focusedField == 2) {
                        if (c == '1') {
                            boolean ok = executeDeposit(user, goal, sourceAccount, depAmt, savingGoalController);
                            if (ok) return true;
                            statusMessage = "Transfer failed: Insufficient funds or invalid amount.";
                            isErrorStatus = true;
                        } else if (c == '2') {
                            return false;
                        }
                    }
                }
            }
        } catch (Exception e) {
            logger.error("Error in DepositGoalModal", e);
            return false;
        }
    }

    public static boolean depositToGoal(Terminal terminal, Attributes origAttr, NonBlockingReader reader,
                                        SavingGoal goal, List<AccountDTO> accounts,
                                        SavingGoalController savingGoalController,
                                        AccountController accountController,
                                        User user) {
        return depositToGoal(terminal, origAttr, reader, goal, accounts, savingGoalController, accountController, user, TUILayout.APP_WIDTH);
    }

    private static boolean executeDeposit(User user, SavingGoal goal, AccountDTO account, BigDecimal amount,
                                          SavingGoalController savingGoalController) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) return false;
        Connection conn = null;
        try {
            conn = DatabaseConnection.getConnection();
            conn.setAutoCommit(false);

            Account acc = ControllerFactory.getAccountRepository().findByIdForUpdate(conn, account.getAccountId())
                    .orElse(null);
            if (acc == null || acc.getBalance().compareTo(amount) < 0) {
                return false;
            }

            BigDecimal newBal = acc.getBalance().subtract(amount);
            acc.setBalance(newBal);
            ControllerFactory.getAccountRepository().updateWithConnection(conn, acc);

            Transaction tx = Transaction.builder()
                    .accountId(acc.getAccountId())
                    .transactionType(TransactionType.PAYMENT)
                    .amount(amount)
                    .currency(acc.getCurrency())
                    .description("Deposit to Savings Goal: " + goal.getName())
                    .status(TransactionStatus.COMPLETED)
                    .createdAt(LocalDateTime.now())
                    .build();
            ControllerFactory.getTransactionRepository().saveWithConnection(conn, tx);

            conn.commit();

            Long goalId = goal.getGoalId();
            if (goalId == null) {
                SavingGoal created = savingGoalController.createGoal(user, goal.getName(), goal.getTargetAmount(), goal.getDeadline());
                goalId = created.getGoalId();
                goal.setGoalId(goalId);
            }
            savingGoalController.contribute(goalId, amount, user);
            return true;
        } catch (Exception e) {
            logger.error("Deposit to goal transaction failed", e);
            if (conn != null) {
                try { conn.rollback(); } catch (Exception ignored) {}
            }
            return false;
        } finally {
            if (conn != null) {
                try { conn.setAutoCommit(true); conn.close(); } catch (Exception ignored) {}
            }
        }
    }

    private static BigDecimal parseDecimal(String s, BigDecimal def) {
        try {
            return new BigDecimal(s.replace(",", "").replace("$", "").trim());
        } catch (Exception e) {
            return def;
        }
    }
}
