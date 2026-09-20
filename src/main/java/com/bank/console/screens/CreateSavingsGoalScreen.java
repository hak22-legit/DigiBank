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
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Modernized Create Savings Goal Screen with dynamic financial projections and AccountSelectorModal.
 */
public class CreateSavingsGoalScreen implements Screen {
    private static final Logger logger = LoggerFactory.getLogger(CreateSavingsGoalScreen.class);

    private final SavingGoalController savingGoalController;
    private final AccountController accountController;

    public CreateSavingsGoalScreen() {
        this(ControllerFactory.getSavingGoalController(), ControllerFactory.getAccountController());
    }

    public CreateSavingsGoalScreen(SavingGoalController savingGoalController, AccountController accountController) {
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

        List<AccountDTO> accounts = accountController.getAccountsForUser(userEntity);
        if (accounts == null || accounts.isEmpty()) {
            TUILayout.printAlert("No funding accounts available to initialize a goal.", true);
            navigator.pop();
            return;
        }

        AccountDTO fundingAccount = accounts.get(0);

        // Fields:
        // 0: Goal Title
        // 1: Target Amount
        // 2: Target Target Date
        // 3: Funding Account (modal)
        // 4: Initial Seed Deposit
        // 5: Action Bar ([1] Save & Initialize Goal, [2] Cancel & Return)
        int focusedField = 0;
        int actionIdx = 0; // 0: Save, 1: Cancel

        StringBuilder titleBuf = new StringBuilder("Emergency Fund");
        StringBuilder targetBuf = new StringBuilder("3000.00");
        StringBuilder dateBuf = new StringBuilder("2026-12-31");
        StringBuilder seedBuf = new StringBuilder("100.00");

        String statusMessage = "Ready";
        boolean isErrorStatus = false;

        Terminal terminal = session.getTerminal();
        Attributes origAttributes = terminal.enterRawMode();
        NonBlockingReader reader = terminal.reader();
        boolean firstRender = true;

        try {
            while (true) {
                BigDecimal targetAmt = parseDecimal(targetBuf.toString(), new BigDecimal("3000.00"));
                BigDecimal seedAmt = parseDecimal(seedBuf.toString(), new BigDecimal("100.00"));
                LocalDate deadline = parseDate(dateBuf.toString(), LocalDate.now().plusMonths(4));

                // Projection calculation
                long days = Math.max(1, ChronoUnit.DAYS.between(LocalDate.now(), deadline));
                double months = Math.max(0.5, days / 30.0);
                BigDecimal remainingToSave = targetAmt.subtract(seedAmt).max(BigDecimal.ZERO);
                BigDecimal reqMonthly = remainingToSave.divide(BigDecimal.valueOf(months), 2, RoundingMode.HALF_UP);
                String feasibility = reqMonthly.compareTo(new BigDecimal("1200.00")) <= 0 ? "OK" : "STRETCH";

                StringBuilder sb = new StringBuilder();
                sb.append(TUIBox.top(width)).append("\n");
                sb.append(TUIBox.line(ConsoleTheme.primary("DIGIBANK CORE > BUDGETS & SAVINGS > CREATE SAVINGS GOAL"), width)).append("\n");
                sb.append(TUIBox.divider(width)).append("\n");
                sb.append(TUIBox.line("GOAL PARAMETERS", width)).append("\n");
                sb.append(TUIBox.emptyLine(width)).append("\n");

                sb.append(TUIFormHelper.formatFieldRow("Goal Title", titleBuf.toString(), focusedField == 0, 24, 46)).append("\n");
                sb.append(TUIFormHelper.formatFieldRow("Target Amount", "$ " + targetBuf.toString(), focusedField == 1, 24, 46)).append("\n");
                sb.append(TUIFormHelper.formatFieldRow("Target Target Date", dateBuf.toString(), focusedField == 2, 24, 46)).append("\n");

                String accDisplay = String.format("%s (%s - Bal: $%s %s)",
                        fundingAccount.getAccountNumber(),
                        fundingAccount.getAccountType(),
                        df.format(fundingAccount.getBalance()),
                        fundingAccount.getCurrency());
                sb.append(TUIFormHelper.formatFieldRow("Funding Account", accDisplay, focusedField == 3, 24, 46)).append("\n");
                sb.append(TUIFormHelper.formatFieldRow("Initial Seed Deposit", "$ " + seedBuf.toString(), focusedField == 4, 24, 46)).append("\n");
                sb.append(TUIBox.emptyLine(width)).append("\n");

                sb.append(TUIBox.divider(width)).append("\n");
                sb.append(TUIBox.line("FINANCIAL PROJECTION", width)).append("\n");
                sb.append(TUIBox.emptyLine(width)).append("\n");

                String projRow = String.format("  Duration: ~%.1f Months     Req. Savings: $ %s / Month     Feasibility: %s",
                        months, df.format(reqMonthly), feasibility);
                sb.append(TUIBox.line(projRow, width)).append("\n");

                sb.append(TUIBox.divider(width)).append("\n");
                sb.append(TUIBox.line("ACTION", width)).append("\n");
                sb.append(TUIBox.emptyLine(width)).append("\n");

                String a1 = "[1] Save & Initialize Goal";
                String a2 = "[2] Cancel & Return";
                String act1 = (focusedField == 5 && actionIdx == 0) ? "▸ " + ConsoleTheme.highlight(a1) : "  " + a1;
                String act2 = (focusedField == 5 && actionIdx == 1) ? "▸ " + ConsoleTheme.highlight(a2) : "  " + a2;
                sb.append(TUIBox.line("  " + act1 + "                    " + act2, width)).append("\n");

                sb.append(TUIBox.divider(width)).append("\n");
                String statusLine = isErrorStatus ? ConsoleTheme.error(statusMessage) : statusMessage;
                sb.append(TUIBox.line("Status: " + statusLine, width)).append("\n");
                sb.append(TUIBox.bottom(width)).append("\n");
                sb.append(ConsoleTheme.keyGuide("[Tab/↓] Next Field  •  [Enter] Confirm/Select  •  [1/2] Action  •  [Esc] Cancel")).append("\n");

                ScreenRenderer.render(sb.toString(), firstRender);
                firstRender = false;

                KeyEvent event = TUIFormHelper.readKey(reader);
                if (event.action() == KeyAction.ESCAPE) {
                    terminal.setAttributes(origAttributes);
                    navigator.pop();
                    return;
                } else if (event.action() == KeyAction.TAB || event.action() == KeyAction.DOWN) {
                    focusedField = (focusedField + 1) % 6;
                } else if (event.action() == KeyAction.SHIFT_TAB || event.action() == KeyAction.UP) {
                    focusedField = (focusedField - 1 + 6) % 6;
                } else if (focusedField == 5 && (event.action() == KeyAction.LEFT || event.action() == KeyAction.RIGHT)) {
                    actionIdx = (actionIdx == 0) ? 1 : 0;
                } else if (event.action() == KeyAction.BACKSPACE) {
                    if (focusedField == 0 && titleBuf.length() > 0) titleBuf.deleteCharAt(titleBuf.length() - 1);
                    else if (focusedField == 1 && targetBuf.length() > 0) targetBuf.deleteCharAt(targetBuf.length() - 1);
                    else if (focusedField == 2 && dateBuf.length() > 0) dateBuf.deleteCharAt(dateBuf.length() - 1);
                    else if (focusedField == 4 && seedBuf.length() > 0) seedBuf.deleteCharAt(seedBuf.length() - 1);
                } else if (event.action() == KeyAction.ENTER) {
                    if (focusedField == 3) {
                        AccountDTO chosen = AccountSelectorModal.selectSenderAccount(
                                terminal, origAttributes, reader, accounts, fundingAccount, width);
                        if (chosen != null) {
                            fundingAccount = chosen;
                            focusedField = 4;
                        }
                        firstRender = true;
                    } else if (focusedField == 5) {
                        if (actionIdx == 0) {
                            saveGoal(userEntity, titleBuf.toString(), targetAmt, deadline, fundingAccount, seedAmt);
                            terminal.setAttributes(origAttributes);
                            navigator.pop();
                            return;
                        } else {
                            terminal.setAttributes(origAttributes);
                            navigator.pop();
                            return;
                        }
                    } else {
                        focusedField = (focusedField + 1) % 6;
                    }
                } else if (event.action() == KeyAction.DIGIT || event.action() == KeyAction.CHAR) {
                    char c = event.ch();
                    if (focusedField == 0) {
                        if (titleBuf.length() < 30) titleBuf.append(c);
                    } else if (focusedField == 1) {
                        if ((c >= '0' && c <= '9') || (c == '.' && !targetBuf.toString().contains("."))) {
                            if (targetBuf.length() < 10) targetBuf.append(c);
                        }
                    } else if (focusedField == 2) {
                        if ((c >= '0' && c <= '9') || c == '-') {
                            if (dateBuf.length() < 10) dateBuf.append(c);
                        }
                    } else if (focusedField == 4) {
                        if ((c >= '0' && c <= '9') || (c == '.' && !seedBuf.toString().contains("."))) {
                            if (seedBuf.length() < 10) seedBuf.append(c);
                        }
                    } else if (focusedField == 5) {
                        if (c == '1') {
                            saveGoal(userEntity, titleBuf.toString(), targetAmt, deadline, fundingAccount, seedAmt);
                            terminal.setAttributes(origAttributes);
                            navigator.pop();
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
            logger.error("Error in CreateSavingsGoalScreen", e);
        } finally {
            terminal.setAttributes(origAttributes);
        }
    }

    private void saveGoal(User user, String title, BigDecimal target, LocalDate deadline,
                           AccountDTO fundingAccount, BigDecimal seed) {
        try {
            SavingGoal g = savingGoalController.createGoal(user, title, target, deadline);
            if (seed != null && seed.compareTo(BigDecimal.ZERO) > 0 && fundingAccount != null) {
                // Atomic debit from account
                Connection conn = null;
                try {
                    conn = DatabaseConnection.getConnection();
                    conn.setAutoCommit(false);
                    Account acc = ControllerFactory.getAccountRepository().findByIdForUpdate(conn, fundingAccount.getAccountId())
                            .orElse(null);
                    if (acc != null && acc.getBalance().compareTo(seed) >= 0) {
                        BigDecimal newBal = acc.getBalance().subtract(seed);
                        acc.setBalance(newBal);
                        ControllerFactory.getAccountRepository().updateWithConnection(conn, acc);

                        Transaction tx = Transaction.builder()
                                .accountId(acc.getAccountId())
                                .transactionType(TransactionType.PAYMENT)
                                .amount(seed)
                                .currency(acc.getCurrency())
                                .description("Initial seed deposit for goal: " + title)
                                .status(TransactionStatus.COMPLETED)
                                .createdAt(LocalDateTime.now())
                                .build();
                        ControllerFactory.getTransactionRepository().saveWithConnection(conn, tx);
                        conn.commit();

                        savingGoalController.contribute(g.getGoalId(), seed, user);
                    }
                } catch (Exception e) {
                    if (conn != null) conn.rollback();
                } finally {
                    if (conn != null) {
                        conn.setAutoCommit(true);
                        conn.close();
                    }
                }
            }
        } catch (Exception e) {
            logger.error("Failed to save savings goal", e);
        }
    }

    private static BigDecimal parseDecimal(String s, BigDecimal def) {
        try {
            return new BigDecimal(s.replace(",", "").replace("$", "").trim());
        } catch (Exception e) {
            return def;
        }
    }

    private static LocalDate parseDate(String s, LocalDate def) {
        try {
            return LocalDate.parse(s.trim());
        } catch (Exception e) {
            return def;
        }
    }
}
