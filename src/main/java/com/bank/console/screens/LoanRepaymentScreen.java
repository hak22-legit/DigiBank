package com.bank.console.screens;

import com.bank.console.ControllerFactory;
import com.bank.console.ScreenNavigator;
import com.bank.console.TUISession;
import com.bank.console.components.ConsolePrompt;
import com.bank.console.components.TUIBox;
import com.bank.console.components.TUILayout;
import com.bank.console.theme.ConsoleTheme;
import com.bank.controller.AccountController;
import com.bank.controller.LoanController;
import com.bank.model.dto.AccountDTO;
import com.bank.model.dto.LoanDTO;
import com.bank.model.dto.UserDTO;
import com.bank.model.entity.LoanPayment;
import com.bank.model.entity.User;
import com.bank.model.enums.LoanPaymentStatus;
import com.bank.model.enums.LoanStatus;
import com.bank.security.SessionManager;
import org.jline.terminal.Attributes;
import org.jline.terminal.Terminal;
import org.jline.utils.NonBlockingReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * DEDICATED SCREEN: LOAN REPAYMENT PORTAL (82 Columns)
 * Pure keyboard navigation with zero trailing prompts.
 */
public class LoanRepaymentScreen implements Screen {
    private static final Logger logger = LoggerFactory.getLogger(LoanRepaymentScreen.class);
    private static final DecimalFormat CURRENCY = new DecimalFormat("$#,##0.00");
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final LoanController loanController;
    private final AccountController accountController;
    private String statusMessage;
    private boolean isErrorStatus;

    public LoanRepaymentScreen() {
        this(ControllerFactory.getLoanController(), ControllerFactory.getAccountController());
    }

    public LoanRepaymentScreen(LoanController loanController, AccountController accountController) {
        this.loanController = loanController;
        this.accountController = accountController;
        this.statusMessage = null;
        this.isErrorStatus = false;
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
        Terminal terminal = session.getTerminal();
        Attributes origAttributes = terminal.enterRawMode();
        NonBlockingReader reader = terminal.reader();

        int selectedIndex = 0;
        boolean firstRender = true;

        try {
            while (true) {
                List<LoanDTO> loans = loanController.getUserLoans(userEntity);
                LoanDTO activeLoan = loans != null ? loans.stream()
                        .filter(l -> l.getStatus() == LoanStatus.APPROVED || l.getStatus() == LoanStatus.ACTIVE)
                        .findFirst().orElse(null) : null;

                StringBuilder sb = new StringBuilder();
                if (firstRender) {
                    sb.append(ConsoleTheme.CLEAR_SCREEN);
                } else {
                    sb.append("\u001B[H");
                }

                sb.append(TUIBox.top(width)).append("\n");
                sb.append(TUIBox.line(ConsoleTheme.primary("DIGIBANK CORE > LOAN REPAYMENT & INSTALLMENT SCHEDULE"), width)).append("\n");
                sb.append(TUIBox.divider(width)).append("\n");
                sb.append(TUIBox.emptyLine(width)).append("\n");

                if (activeLoan == null) {
                    sb.append(TUIBox.center(ConsoleTheme.muted("No active loans found requiring installment repayment."), width)).append("\n");
                    sb.append(TUIBox.emptyLine(width)).append("\n");
                    sb.append(TUIBox.divider(width)).append("\n");

                    String b0 = selectedIndex == 0 ? "  ► " + ConsoleTheme.highlight("[1] Go to Loans Overview") : "    " + "[1] Go to Loans Overview";
                    String b1 = selectedIndex == 1 ? "► " + ConsoleTheme.highlight("[0] Return to Main Menu") : "  " + ConsoleTheme.muted("[0] Return to Main Menu");
                    sb.append(TUIBox.line(b0 + "        " + b1, width)).append("\n");
                    sb.append(TUIBox.bottom(width)).append("\n");

                    if (statusMessage != null) {
                        String statusDisplay = isErrorStatus ? ConsoleTheme.error(statusMessage) : ConsoleTheme.success(statusMessage);
                        sb.append(" Status: ").append(statusDisplay).append("\n");
                    }
                    sb.append(ConsoleTheme.muted("  [↑/↓] Navigate  •  [Enter] Select  •  [1/0] Quick Select  •  [Esc] Back")).append("\n");

                    System.out.print(sb.toString());
                    System.out.flush();
                    firstRender = false;

                    int ch = reader.read();
                    if (ch == 27) { // ESC or arrow sequence
                        int next = reader.read(60);
                        if (next == -2 || next == -1) {
                            navigator.pop();
                            return;
                        }
                        if (next == '[' || next == 'O') {
                            int code = reader.read();
                            if (code == 'A' || code == 'D') { // Up / Left
                                selectedIndex = (selectedIndex - 1 + 2) % 2;
                            } else if (code == 'B' || code == 'C') { // Down / Right
                                selectedIndex = (selectedIndex + 1) % 2;
                            }
                        }
                    } else if (ch == '\t') {
                        selectedIndex = (selectedIndex + 1) % 2;
                    } else if (ch == '\r' || ch == '\n') {
                        if (selectedIndex == 0) {
                            terminal.setAttributes(origAttributes);
                            navigator.push(new LoanScreen());
                            return;
                        } else {
                            navigator.pop();
                            return;
                        }
                    } else if (ch == '1') {
                        terminal.setAttributes(origAttributes);
                        navigator.push(new LoanScreen());
                        return;
                    } else if (ch == '0' || ch == 'b' || ch == 'B') {
                        navigator.pop();
                        return;
                    } else if (ch == 3) { // Ctrl+C
                        session.clearScreen();
                        System.exit(0);
                    }
                    continue;
                }

                // Active Loan Summary
                String loanIdStr = String.format("#LN-%04d", activeLoan.getLoanId());
                String balanceStr = CURRENCY.format(activeLoan.getOutstandingBalance() != null ? activeLoan.getOutstandingBalance() : BigDecimal.ZERO);

                BigDecimal monthlyDue = BigDecimal.ZERO;
                if (activeLoan.getApprovedAmount() != null && activeLoan.getTermMonths() != null && activeLoan.getTermMonths() > 0) {
                    monthlyDue = activeLoan.getApprovedAmount().divide(BigDecimal.valueOf(activeLoan.getTermMonths()), 2, RoundingMode.HALF_UP);
                }
                String monthlyStr = CURRENCY.format(monthlyDue);
                String rateStr = (activeLoan.getInterestRate() != null ? activeLoan.getInterestRate().stripTrailingZeros().toPlainString() : "0.0") + "%/yr";

                sb.append(TUIBox.line(String.format("  Active Loan : %-15s   Status      : %s",
                        ConsoleTheme.bold(loanIdStr), ConsoleTheme.success("ACTIVE")), width)).append("\n");
                sb.append(TUIBox.line(String.format("  Outstanding : %-15s   Monthly Due : %s",
                        ConsoleTheme.error(balanceStr), ConsoleTheme.bold(monthlyStr)), width)).append("\n");
                sb.append(TUIBox.line(String.format("  Rate / Term : %-15s   Risk Level  : %s",
                        rateStr, activeLoan.getRiskLevel() != null ? activeLoan.getRiskLevel().name() : "STANDARD"), width)).append("\n");
                sb.append(TUIBox.emptyLine(width)).append("\n");
                sb.append(TUIBox.divider(width)).append("\n");
                sb.append(TUIBox.emptyLine(width)).append("\n");

                // Payment History Schedule
                List<LoanPayment> schedule = loanController.getPaymentHistory(activeLoan.getLoanId(), userEntity);
                sb.append(TUIBox.line(ConsoleTheme.bold("  REPAYMENT SCHEDULE (loan_payments)"), width)).append("\n");
                String th = String.format("  %-6s  %-12s  %-14s  %-12s  %-12s  %-10s",
                        "INST#", "DUE DATE", "PRINCIPAL", "INTEREST", "TOTAL DUE", "STATUS");
                sb.append(TUIBox.line(th, width)).append("\n");
                sb.append(TUIBox.line("  " + "─".repeat(76), width)).append("\n");

                if (schedule == null || schedule.isEmpty()) {
                    sb.append(TUIBox.line(ConsoleTheme.muted("  No payment installments generated yet."), width)).append("\n");
                } else {
                    int displayed = 0;
                    for (LoanPayment p : schedule) {
                        if (displayed++ >= 6) break;
                        String due = p.getDueDate() != null ? p.getDueDate().format(DATE_FMT) : "N/A";
                        String princ = CURRENCY.format(p.getPrincipalAmount() != null ? p.getPrincipalAmount() : BigDecimal.ZERO);
                        String intr = CURRENCY.format(p.getInterestAmount() != null ? p.getInterestAmount() : BigDecimal.ZERO);
                        String tot = CURRENCY.format(p.getAmount() != null ? p.getAmount() : BigDecimal.ZERO);
                        String statBadge = p.getStatus() == LoanPaymentStatus.COMPLETED ? ConsoleTheme.success("PAID") :
                                p.getStatus() == LoanPaymentStatus.SCHEDULED ? ConsoleTheme.warning("PENDING") :
                                        ConsoleTheme.error(String.valueOf(p.getStatus()));

                        String row = String.format("  #%-5d  %-12s  %-14s  %-12s  %-12s  %s",
                                p.getPaymentId() != null ? p.getPaymentId() : displayed,
                                due, princ, intr, tot, statBadge);
                        sb.append(TUIBox.line(row, width)).append("\n");
                    }
                }

                sb.append(TUIBox.emptyLine(width)).append("\n");
                sb.append(TUIBox.divider(width)).append("\n");

                String b0 = selectedIndex == 0 ? "  ► " + ConsoleTheme.highlight("[1] Pay Next Installment") : "    " + "[1] Pay Next Installment";
                String b1 = selectedIndex == 1 ? "► " + ConsoleTheme.highlight("[0] Return to Main Menu") : "  " + ConsoleTheme.muted("[0] Return to Main Menu");
                sb.append(TUIBox.line(b0 + "       " + b1, width)).append("\n");
                sb.append(TUIBox.bottom(width)).append("\n");

                if (statusMessage != null) {
                    String statusDisplay = isErrorStatus ? ConsoleTheme.error(statusMessage) : ConsoleTheme.success(statusMessage);
                    sb.append(" Status: ").append(statusDisplay).append("\n");
                }
                sb.append(ConsoleTheme.muted("  [↑/↓] Navigate  •  [Enter] Select  •  [1/0] Quick Select  •  [Esc] Back")).append("\n");

                System.out.print(sb.toString());
                System.out.flush();
                firstRender = false;

                int ch = reader.read();

                if (ch == 27) { // ESC or Escape sequence
                    int next = reader.read(60);
                    if (next == -2 || next == -1) {
                        navigator.pop();
                        return;
                    }
                    if (next == '[' || next == 'O') {
                        int code = reader.read();
                        if (code == 'A' || code == 'D') { // Up / Left
                            selectedIndex = (selectedIndex - 1 + 2) % 2;
                        } else if (code == 'B' || code == 'C') { // Down / Right
                            selectedIndex = (selectedIndex + 1) % 2;
                        }
                    }
                } else if (ch == '\t') {
                    selectedIndex = (selectedIndex + 1) % 2;
                } else if (ch == '\r' || ch == '\n') {
                    if (selectedIndex == 0) {
                        terminal.setAttributes(origAttributes);
                        handleMakePayment(userEntity, activeLoan, monthlyDue);
                        origAttributes = terminal.enterRawMode();
                        firstRender = true;
                    } else {
                        navigator.pop();
                        return;
                    }
                } else if (ch == '1') {
                    terminal.setAttributes(origAttributes);
                    handleMakePayment(userEntity, activeLoan, monthlyDue);
                    origAttributes = terminal.enterRawMode();
                    firstRender = true;
                } else if (ch == '0' || ch == 'b' || ch == 'B') {
                    navigator.pop();
                    return;
                } else if (ch == 3) { // Ctrl+C
                    session.clearScreen();
                    System.exit(0);
                }
            }
        } catch (IOException e) {
            logger.error("Error in loan repayment loop", e);
        } finally {
            terminal.setAttributes(origAttributes);
        }
    }

    private void handleMakePayment(User userEntity, LoanDTO activeLoan, BigDecimal defaultAmount) {
        List<AccountDTO> accounts = accountController.getAccountsForUser(userEntity);
        if (accounts == null || accounts.isEmpty()) {
            this.statusMessage = "No funding accounts available to make loan payment.";
            this.isErrorStatus = true;
            return;
        }

        System.out.println();
        System.out.println("  Available Payment Accounts:");
        for (int i = 0; i < accounts.size(); i++) {
            AccountDTO acc = accounts.get(i);
            System.out.println(String.format("  [%d] %s (%s) - Balance: %s",
                    i + 1, acc.getAccountNumber(), acc.getAccountType(), CURRENCY.format(acc.getBalance())));
        }

        String accInput = ConsolePrompt.promptLine("Select source account number (1-" + accounts.size() + ")");
        int accIdx = 0;
        try {
            accIdx = Integer.parseInt(accInput.trim()) - 1;
            if (accIdx < 0 || accIdx >= accounts.size()) {
                this.statusMessage = "Invalid account choice.";
                this.isErrorStatus = true;
                return;
            }
        } catch (Exception e) {
            this.statusMessage = "Invalid input for account selection.";
            this.isErrorStatus = true;
            return;
        }

        AccountDTO source = accounts.get(accIdx);

        String promptMsg = "Payment Amount (default: " + CURRENCY.format(defaultAmount) + ")";
        String amountInput = ConsolePrompt.promptLine(promptMsg);
        BigDecimal amount = amountInput.isEmpty() ? defaultAmount : new BigDecimal(amountInput);

        try {
            LoanDTO updated = loanController.repayLoan(userEntity, activeLoan.getLoanId(), source.getAccountId(), amount);
            this.statusMessage = "Payment successful! Remaining balance: " + CURRENCY.format(updated.getOutstandingBalance());
            this.isErrorStatus = false;
        } catch (Exception e) {
            logger.error("Loan payment execution failed", e);
            this.statusMessage = "Payment error: " + e.getMessage();
            this.isErrorStatus = true;
        }
    }
}
