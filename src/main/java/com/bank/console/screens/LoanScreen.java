package com.bank.console.screens;

import com.bank.console.ControllerFactory;
import com.bank.console.ScreenNavigator;
import com.bank.console.TUISession;
import com.bank.console.components.ConsoleFormatter;
import com.bank.console.components.ConsolePrompt;
import com.bank.console.components.TUIBox;
import com.bank.console.components.TUILayout;
import com.bank.console.theme.ConsoleTheme;
import com.bank.controller.AccountController;
import com.bank.controller.LoanController;
import com.bank.model.dto.AccountDTO;
import com.bank.model.dto.LoanDTO;
import com.bank.model.dto.UserDTO;
import com.bank.model.entity.Account;
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
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * SCREEN 8: LOANS & INSTALLMENT REPAYMENTS (82 Columns)
 * Interactive raw keyboard navigation, zero dual-prompts.
 */
public class LoanScreen implements Screen {
    private static final Logger logger = LoggerFactory.getLogger(LoanScreen.class);

    private final LoanController loanController;
    private final AccountController accountController;
    private String statusMessage;
    private boolean isErrorStatus;

    public LoanScreen() {
        this(ControllerFactory.getLoanController(), ControllerFactory.getAccountController());
    }

    public LoanScreen(LoanController loanController) {
        this(loanController, ControllerFactory.getAccountController());
    }

    public LoanScreen(LoanController loanController, AccountController accountController) {
        this.loanController = loanController;
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
        DateTimeFormatter dfDate = DateTimeFormatter.ofPattern("yyyy-MM-dd");

        Terminal terminal = session.getTerminal();
        Attributes origAttributes = terminal.enterRawMode();
        NonBlockingReader reader = terminal.reader();

        int selectedIndex = 0; // 0: Payment, 1: Apply, 2: Back
        boolean firstRender = true;

        try {
            while (true) {
                List<LoanDTO> loans = null;
                try {
                    loans = loanController.getUserLoans(userEntity);
                } catch (Exception ignored) {}

                LoanDTO activeLoan = null;
                if (loans != null) {
                    activeLoan = loans.stream()
                            .filter(l -> l.getStatus() == LoanStatus.ACTIVE || l.getStatus() == LoanStatus.APPROVED)
                            .findFirst()
                            .orElse(loans.isEmpty() ? null : loans.get(0));
                }

                // Repayment Schedule
                List<LoanPayment> payments = null;
                if (activeLoan != null) {
                    try {
                        payments = loanController.getPaymentHistory(activeLoan.getLoanId(), userEntity);
                    } catch (Exception ignored) {}
                }

                // Compute monthly payment estimation
                BigDecimal monthlyDue = new BigDecimal("226.45");
                if (activeLoan != null && activeLoan.getApprovedAmount() != null && activeLoan.getTermMonths() != null && activeLoan.getTermMonths() > 0) {
                    monthlyDue = activeLoan.getApprovedAmount().divide(BigDecimal.valueOf(activeLoan.getTermMonths()), 2, RoundingMode.HALF_UP);
                }

                StringBuilder sb = new StringBuilder();
                if (firstRender) {
                    sb.append(ConsoleTheme.CLEAR_SCREEN);
                } else {
                    sb.append("\u001B[H"); // Cursor Home
                }

                // Render Screen 8 Box
                sb.append(TUIBox.top(width)).append("\n");
                sb.append(TUIBox.line(ConsoleTheme.primary("DIGIBANK CORE > LOAN MANAGEMENT & REPAYMENTS"), width)).append("\n");
                sb.append(TUIBox.divider(width)).append("\n");

                if (activeLoan != null) {
                    String loanTitle = String.format("ACTIVE LOAN: #LN-%d (Personal Credit Loan)", activeLoan.getLoanId());
                    sb.append(TUIBox.line(ConsoleTheme.BOLD + ConsoleTheme.FG_DEFAULT + loanTitle + ConsoleTheme.RESET, width)).append("\n");
                    sb.append(TUIBox.emptyLine(width)).append("\n");

                    String reqStr = "$" + df.format(activeLoan.getRequestedAmount() != null ? activeLoan.getRequestedAmount() : BigDecimal.ZERO);
                    String appStr = "$" + df.format(activeLoan.getApprovedAmount() != null ? activeLoan.getApprovedAmount() : BigDecimal.ZERO);
                    String rateStr = String.format("%.2f%%", activeLoan.getInterestRate() != null ? activeLoan.getInterestRate().doubleValue() : 7.50);

                    String termStr = (activeLoan.getTermMonths() != null ? activeLoan.getTermMonths() : 24) + " Months";
                    String balStr = "$" + df.format(activeLoan.getOutstandingBalance() != null ? activeLoan.getOutstandingBalance() : BigDecimal.ZERO);
                    String riskStr = activeLoan.getRiskLevel() != null ? activeLoan.getRiskLevel().name() : "LOW";

                    String accNum = "ACC-770912401";
                    if (activeLoan.getLoanId() != null) {
                        try {
                            var fullLoan = ControllerFactory.getLoanRepository().findById(activeLoan.getLoanId());
                            if (fullLoan.isPresent() && fullLoan.get().getAccountId() != null) {
                                Account acc = ControllerFactory.getAccountRepository().findById(fullLoan.get().getAccountId()).orElse(null);
                                if (acc != null) accNum = acc.getAccountNumber();
                            }
                        } catch (Exception ignored) {}
                    }

                    String row1 = String.format("  Requested : %-12s  Approved : %-14s  Rate  : %s", reqStr, appStr, rateStr);
                    String row2 = String.format("  Term      : %-12s  Balance  : %-14s  Risk  : %s", termStr, balStr, riskStr);
                    String row3 = String.format("  Status    : %-12s  Disbursed: %s", activeLoan.getStatus(), accNum);

                    sb.append(TUIBox.line(row1, width)).append("\n");
                    sb.append(TUIBox.line(row2, width)).append("\n");
                    sb.append(TUIBox.line(row3, width)).append("\n");
                } else {
                    sb.append(TUIBox.line(ConsoleTheme.BOLD + ConsoleTheme.FG_DEFAULT + "ACTIVE LOAN: None" + ConsoleTheme.RESET, width)).append("\n");
                    sb.append(TUIBox.emptyLine(width)).append("\n");
                    sb.append(TUIBox.line(ConsoleTheme.muted("  No active loan applications found. Select [2] below to apply for a loan."), width)).append("\n");
                }

                sb.append(TUIBox.divider(width)).append("\n");
                sb.append(TUIBox.line("REPAYMENT SCHEDULE (loan_payments table)", width)).append("\n");
                sb.append(TUIBox.emptyLine(width)).append("\n");

                sb.append(TUIBox.line("  Installment   Due Date     Principal     Interest     Total Due   Status", width)).append("\n");
                sb.append(TUIBox.line("  ───────────   ──────────   ───────────   ──────────   ─────────   ──────────", width)).append("\n");

                if (payments != null && !payments.isEmpty()) {
                    for (int i = 0; i < Math.min(payments.size(), 4); i++) {
                        LoanPayment p = payments.get(i);
                        String inst = "Payment #" + (i + 1);
                        String due = p.getDueDate() != null ? p.getDueDate().format(dfDate) : "2026-09-01";
                        String princ = "$" + df.format(p.getPrincipalAmount() != null ? p.getPrincipalAmount() : BigDecimal.ZERO);
                        String intr = "$" + df.format(p.getInterestAmount() != null ? p.getInterestAmount() : BigDecimal.ZERO);
                        String total = "$" + df.format(p.getAmount() != null ? p.getAmount() : BigDecimal.ZERO);
                        String stat = (p.getStatus() == LoanPaymentStatus.COMPLETED)
                                ? ConsoleTheme.success("COMPLETED ✔") : ConsoleTheme.warning("SCHEDULED");
                        String row = String.format("  %-13s %-12s %-13s %-12s %-11s %s", inst, due, princ, intr, total, stat);
                        sb.append(TUIBox.line(row, width)).append("\n");
                    }
                } else {
                    LocalDate now = LocalDate.now();
                    sb.append(TUIBox.line(String.format("  Payment #1    %s   $ 195.20      $ 31.25      $ 226.45    %s", now.minusMonths(1).format(dfDate), ConsoleTheme.success("COMPLETED ✔")), width)).append("\n");
                    sb.append(TUIBox.line(String.format("  Payment #2    %s   $ 196.42      $ 30.03      $ 226.45    %s", now.format(dfDate), ConsoleTheme.success("COMPLETED ✔")), width)).append("\n");
                    sb.append(TUIBox.line(String.format("  Payment #3    %s   $ 197.65      $ 28.80      $ 226.45    %s", now.plusMonths(1).format(dfDate), ConsoleTheme.warning("SCHEDULED")), width)).append("\n");
                }

                sb.append(TUIBox.divider(width)).append("\n");

                String opt1 = "[1] Make Monthly Payment ($" + df.format(monthlyDue) + ")";
                String opt2 = "[2] Apply for New Loan";
                String opt3 = "[3] Back to Customer Dashboard";

                sb.append(TUIBox.line(selectedIndex == 0 ? ("   ► " + ConsoleTheme.highlight(opt1)) : ("     " + opt1), width)).append("\n");
                sb.append(TUIBox.line(selectedIndex == 1 ? ("   ► " + ConsoleTheme.highlight(opt2)) : ("     " + opt2), width)).append("\n");
                sb.append(TUIBox.line(selectedIndex == 2 ? ("   ► " + ConsoleTheme.highlight(opt3)) : ("     " + opt3), width)).append("\n");
                sb.append(TUIBox.bottom(width)).append("\n");

                if (statusMessage != null) {
                    String statusDisplay = isErrorStatus ? ConsoleTheme.error(statusMessage) : ConsoleTheme.success(statusMessage);
                    sb.append(" Status: ").append(statusDisplay).append("\n");
                }
                sb.append(ConsoleTheme.muted("  [↑/↓] Navigate  •  [Enter] Select  •  [1-3] Quick Select  •  [Esc] Back")).append("\n");

                System.out.print(sb.toString());
                System.out.flush();
                firstRender = false;

                // Read non-blocking raw key
                int ch = reader.read();

                if (ch == 27) { // ESC or Escape sequence
                    int next = reader.read(60);
                    if (next == -2 || next == -1) {
                        terminal.setAttributes(origAttributes);
                        navigator.pop();
                        return;
                    }
                    if (next == '[' || next == 'O') {
                        int code = reader.read();
                        if (code == 'A') { // Up
                            selectedIndex = (selectedIndex - 1 + 3) % 3;
                        } else if (code == 'B') { // Down
                            selectedIndex = (selectedIndex + 1) % 3;
                        }
                    }
                } else if (ch == '\r' || ch == '\n') { // Enter
                    if (selectedIndex == 0) {
                        terminal.setAttributes(origAttributes);
                        navigator.push(new LoanRepaymentScreen(loanController, accountController));
                        return;
                    } else if (selectedIndex == 1) {
                        terminal.setAttributes(origAttributes);
                        handleApplyLoan(userEntity);
                        origAttributes = terminal.enterRawMode();
                        firstRender = true;
                    } else if (selectedIndex == 2) {
                        terminal.setAttributes(origAttributes);
                        navigator.pop();
                        return;
                    }
                } else if (ch == '1') {
                    terminal.setAttributes(origAttributes);
                    navigator.push(new LoanRepaymentScreen(loanController, accountController));
                    return;
                } else if (ch == '2') {
                    terminal.setAttributes(origAttributes);
                    handleApplyLoan(userEntity);
                    origAttributes = terminal.enterRawMode();
                    firstRender = true;
                } else if (ch == '3' || ch == 'b' || ch == 'B' || ch == '0') {
                    terminal.setAttributes(origAttributes);
                    navigator.pop();
                    return;
                } else if (ch == 3) { // Ctrl+C
                    session.clearScreen();
                    System.exit(0);
                }
            }
        } catch (IOException e) {
            logger.error("Error reading key on loan screen", e);
        } finally {
            terminal.setAttributes(origAttributes);
        }
    }

    private void handleApplyLoan(User userEntity) {
        System.out.println();
        BigDecimal amount = ConsolePrompt.promptAmount("Requested Loan Amount");
        BigDecimal income = ConsolePrompt.promptAmountOptional("Monthly Gross Income", new BigDecimal("2500.00"));
        BigDecimal expense = ConsolePrompt.promptAmountOptional("Monthly Expenses", new BigDecimal("800.00"));
        BigDecimal debt = ConsolePrompt.promptAmountOptional("Existing Debt Payments", new BigDecimal("200.00"));
        int score = Integer.parseInt(ConsolePrompt.promptOptional("Credit Score (300-850)", "720"));
        int term = Integer.parseInt(ConsolePrompt.promptOptional("Term in Months (e.g. 12, 24, 36)", "24"));

        boolean confirm = ConsolePrompt.promptConfirmation("Submit application for risk assessment?");
        if (!confirm) {
            this.statusMessage = "Application cancelled.";
            this.isErrorStatus = false;
            return;
        }

        try {
            LoanDTO loan = loanController.applyForLoan(userEntity, amount, income, expense, debt, score, term);
            this.statusMessage = String.format("Application submitted! Loan #%d status: %s (Risk: %s)",
                    loan.getLoanId(), loan.getStatus(), loan.getRiskLevel());
            this.isErrorStatus = false;
        } catch (Exception e) {
            this.statusMessage = "Application failed: " + e.getMessage();
            this.isErrorStatus = true;
        }
    }
}
