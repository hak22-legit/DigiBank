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
import com.bank.controller.AccountController;
import com.bank.controller.SavingGoalController;
import com.bank.database.DatabaseConnection;
import com.bank.model.dto.AccountDTO;
import com.bank.model.dto.UserDTO;
import com.bank.model.entity.Account;
import com.bank.model.entity.SavingGoal;
import com.bank.model.entity.Transaction;
import com.bank.model.entity.User;
import com.bank.model.enums.GoalStatus;
import com.bank.model.enums.TransactionStatus;
import com.bank.model.enums.TransactionType;
import com.bank.security.SessionManager;
import org.jline.terminal.Attributes;
import org.jline.terminal.Terminal;
import org.jline.utils.NonBlockingReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Connection;
import java.text.DecimalFormat;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Modernized Deposit to Goal Screen with dynamic impact preview and modal selectors.
 */
public class DepositToGoalScreen implements Screen {
    private static final Logger logger = LoggerFactory.getLogger(DepositToGoalScreen.class);

    private final SavingGoalController savingGoalController;
    private final AccountController accountController;

    public DepositToGoalScreen() {
        this(ControllerFactory.getSavingGoalController(), ControllerFactory.getAccountController());
    }

    public DepositToGoalScreen(SavingGoalController savingGoalController, AccountController accountController) {
        this.savingGoalController = savingGoalController;
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
        DecimalFormat df = new DecimalFormat("#,##0.00");

        List<SavingGoal> allGoals = savingGoalController.getGoalsForUser(userEntity);
        List<SavingGoal> activeGoals = allGoals != null
                ? allGoals.stream().filter(g -> g.getStatus() == GoalStatus.ACTIVE).toList()
                : List.of();

        if (activeGoals.isEmpty()) {
            TUILayout.printAlert("No active savings goals found to deposit into.", true);
            navigator.pop();
            return;
        }

        List<AccountDTO> accounts = accountController.getAccountsForUser(userEntity);
        if (accounts == null || accounts.isEmpty()) {
            TUILayout.printAlert("No debit source accounts available for deposit.", true);
            navigator.pop();
            return;
        }

        SavingGoal selectedGoal = activeGoals.get(0);
        AccountDTO sourceAccount = accounts.get(0);

        // Fields:
        // 0: Destination Goal
        // 1: Debit Source Account
        // 2: Deposit Amount
        // 3: Action Bar ([1] Authorize & Transfer Funds, [2] Cancel & Return)
        int focusedField = 0;
        int actionIdx = 0; // 0: Authorize, 1: Cancel

        StringBuilder amountBuf = new StringBuilder("1000.00");
        String statusMessage = "Ready";
        boolean isErrorStatus = false;

        Terminal terminal = session.getTerminal();
        Attributes origAttributes = terminal.enterRawMode();
        NonBlockingReader reader = terminal.reader();
        boolean firstRender = true;

        try {
            while (true) {
                BigDecimal depAmt = parseDecimal(amountBuf.toString(), new BigDecimal("1000.00"));
                BigDecimal goalTarget = selectedGoal.getTargetAmount() != null ? selectedGoal.getTargetAmount() : new BigDecimal("2500.00");
                BigDecimal currentSaved = selectedGoal.getCurrentAmount() != null ? selectedGoal.getCurrentAmount() : BigDecimal.ZERO;

                BigDecimal newSaved = currentSaved.add(depAmt);
                double pct = goalTarget.compareTo(BigDecimal.ZERO) > 0
                        ? (newSaved.doubleValue() / goalTarget.doubleValue()) * 100.0
                        : 0.0;
                int filledBars = Math.min(16, Math.max(0, (int) Math.round((pct / 100.0) * 16)));
                String bar = "█".repeat(filledBars) + "░".repeat(16 - filledBars);

                BigDecimal updatedBal = sourceAccount.getBalance().subtract(depAmt);

                StringBuilder sb = new StringBuilder();
                sb.append(TUIBox.top(width)).append("\n");
                sb.append(TUIBox.line(ConsoleTheme.primary("DIGIBANK CORE > BUDGETS & SAVINGS > DEPOSIT TO GOAL"), width)).append("\n");
                sb.append(TUIBox.divider(width)).append("\n");
                sb.append(TUIBox.line("TARGET & FUNDING DETAILS", width)).append("\n");
                sb.append(TUIBox.emptyLine(width)).append("\n");

                int goalIdx = activeGoals.indexOf(selectedGoal) + 1;
                int currentGoalPct = (goalTarget.compareTo(BigDecimal.ZERO) > 0)
                        ? (int) Math.round(currentSaved.doubleValue() / goalTarget.doubleValue() * 100)
                        : 0;
                String goalDisplay = String.format("[%d] %s (Target: $%s | %d%%)",
                        goalIdx, selectedGoal.getName(), df.format(goalTarget), currentGoalPct);
                sb.append(TUIFormHelper.formatFieldRow("Destination Goal", goalDisplay, focusedField == 0, 24, 46)).append("\n");

                String accDisplay = String.format("%s (%s - Bal: $%s %s)",
                        sourceAccount.getAccountNumber(),
                        sourceAccount.getAccountType(),
                        df.format(sourceAccount.getBalance()),
                        sourceAccount.getCurrency());
                sb.append(TUIFormHelper.formatFieldRow("Debit Source Account", accDisplay, focusedField == 1, 24, 46)).append("\n");
                sb.append(TUIFormHelper.formatFieldRow("Deposit Amount", "$ " + amountBuf.toString(), focusedField == 2, 24, 46)).append("\n");
                sb.append(TUIBox.emptyLine(width)).append("\n");

                sb.append(TUIBox.divider(width)).append("\n");
                sb.append(TUIBox.line("POST-DEPOSIT IMPACT PREVIEW", width)).append("\n");
                sb.append(TUIBox.emptyLine(width)).append("\n");

                String progRow = String.format("  New Goal Progress    : $ %s / $ %s   [%s]  %5.1f%%",
                        df.format(newSaved), df.format(goalTarget), bar, pct);
                String balRow = String.format("  Updated Account Bal  : $ %s %s (%s)",
                        df.format(updatedBal), sourceAccount.getCurrency(), sourceAccount.getAccountType());
                sb.append(TUIBox.line(progRow, width)).append("\n");
                sb.append(TUIBox.line(balRow, width)).append("\n");

                sb.append(TUIBox.divider(width)).append("\n");
                sb.append(TUIBox.line("ACTION", width)).append("\n");
                sb.append(TUIBox.emptyLine(width)).append("\n");

                String a1 = "[1] Authorize & Transfer Funds";
                String a2 = "[2] Cancel & Return";
                String act1 = (focusedField == 3 && actionIdx == 0) ? "▸ " + ConsoleTheme.highlight(a1) : "  " + a1;
                String act2 = (focusedField == 3 && actionIdx == 1) ? "▸ " + ConsoleTheme.highlight(a2) : "  " + a2;
                sb.append(TUIBox.line("  " + act1 + "                " + act2, width)).append("\n");

                sb.append(TUIBox.divider(width)).append("\n");
                String statusLine = isErrorStatus ? ConsoleTheme.error(statusMessage) : statusMessage;
                sb.append(TUIBox.line("Status: " + statusLine, width)).append("\n");
                sb.append(TUIBox.bottom(width)).append("\n");
                sb.append(ConsoleTheme.keyGuide("[Tab/↓] Next Field  •  [Enter] Action/Select  •  [1/2] Action  •  [Esc] Cancel")).append("\n");

                ScreenRenderer.render(sb.toString(), firstRender);
                firstRender = false;

                KeyEvent event = TUIFormHelper.readKey(reader);
                if (event.action() == KeyAction.ESCAPE) {
                    terminal.setAttributes(origAttributes);
                    navigator.pop();
                    return;
                } else if (event.action() == KeyAction.TAB || event.action() == KeyAction.DOWN) {
                    focusedField = (focusedField + 1) % 4;
                } else if (event.action() == KeyAction.SHIFT_TAB || event.action() == KeyAction.UP) {
                    focusedField = (focusedField - 1 + 4) % 4;
                } else if (focusedField == 3 && (event.action() == KeyAction.LEFT || event.action() == KeyAction.RIGHT)) {
                    actionIdx = (actionIdx == 0) ? 1 : 0;
                } else if (focusedField == 2 && event.action() == KeyAction.BACKSPACE) {
                    if (amountBuf.length() > 0) amountBuf.deleteCharAt(amountBuf.length() - 1);
                } else if (event.action() == KeyAction.ENTER) {
                    if (focusedField == 0) {
                        SavingGoal chosen = GoalSelectorModal.selectGoal(
                                terminal, origAttributes, reader, activeGoals, selectedGoal, width);
                        if (chosen != null) {
                            selectedGoal = chosen;
                            focusedField = 1;
                        }
                        firstRender = true;
                    } else if (focusedField == 1) {
                        AccountDTO chosen = AccountSelectorModal.selectSenderAccount(
                                terminal, origAttributes, reader, accounts, sourceAccount, width);
                        if (chosen != null) {
                            sourceAccount = chosen;
                            focusedField = 2;
                        }
                        firstRender = true;
                    } else if (focusedField == 3) {
                        if (actionIdx == 0) {
                            boolean ok = executeDeposit(userEntity, selectedGoal, sourceAccount, depAmt);
                            terminal.setAttributes(origAttributes);
                            if (ok) {
                                navigator.pop();
                            }
                            return;
                        } else {
                            terminal.setAttributes(origAttributes);
                            navigator.pop();
                            return;
                        }
                    } else {
                        focusedField = (focusedField + 1) % 4;
                    }
                } else if (event.action() == KeyAction.DIGIT || event.action() == KeyAction.CHAR) {
                    char c = event.ch();
                    if (focusedField == 2) {
                        if ((c >= '0' && c <= '9') || (c == '.' && !amountBuf.toString().contains("."))) {
                            if (amountBuf.length() < 10) amountBuf.append(c);
                        }
                    } else if (focusedField == 3) {
                        if (c == '1') {
                            boolean ok = executeDeposit(userEntity, selectedGoal, sourceAccount, depAmt);
                            terminal.setAttributes(origAttributes);
                            if (ok) {
                                navigator.pop();
                            }
                            return;
                        } else if (c == '2') {
                            terminal.setAttributes(origAttributes);
                            navigator.pop();
                            return;
                        }
                    }
                }
            }
        } catch (Exception e) {
            logger.error("Error in DepositToGoalScreen", e);
        } finally {
            terminal.setAttributes(origAttributes);
        }
    }

    private boolean executeDeposit(User user, SavingGoal goal, AccountDTO account, BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) return false;
        Connection conn = null;
        try {
            conn = DatabaseConnection.getConnection();
            conn.setAutoCommit(false);

            Account acc = ControllerFactory.getAccountRepository().findByIdForUpdate(conn, account.getAccountId())
                    .orElseThrow(() -> new IllegalStateException("Account not found"));

            if (acc.getBalance().compareTo(amount) < 0) {
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

            savingGoalController.contribute(goal.getGoalId(), amount, user);
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
