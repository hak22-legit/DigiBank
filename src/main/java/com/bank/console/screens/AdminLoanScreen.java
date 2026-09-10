package com.bank.console.screens;

import com.bank.console.ControllerFactory;
import com.bank.console.ScreenNavigator;
import com.bank.console.TUISession;
import com.bank.console.components.ConsoleFormatter;
import com.bank.console.components.ConsolePrompt;
import com.bank.console.components.TUIBox;
import com.bank.console.components.TUILayout;
import com.bank.console.theme.ConsoleTheme;
import com.bank.controller.LoanController;
import com.bank.model.dto.AdminDTO;
import com.bank.model.entity.Admin;
import com.bank.model.entity.Loan;
import com.bank.model.entity.User;
import com.bank.security.SessionManager;
import org.jline.terminal.Attributes;
import org.jline.terminal.Terminal;
import org.jline.utils.NonBlockingReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

/**
 * SCREEN 11: LOAN UNDERWRITING (ADMIN ACTION) (82 Columns)
 * Non-blocking keyboard navigation with zero trailing prompts.
 */
public class AdminLoanScreen implements Screen {
    private static final Logger logger = LoggerFactory.getLogger(AdminLoanScreen.class);

    private final LoanController loanController;
    private int currentIndex = 0;
    private String statusMessage;
    private boolean isErrorStatus;

    public AdminLoanScreen() {
        this(ControllerFactory.getLoanController());
    }

    public AdminLoanScreen(LoanController loanController) {
        this.loanController = loanController;
        this.statusMessage = null;
        this.isErrorStatus = false;
    }

    @Override
    public void render(ScreenNavigator navigator, TUISession session) {
        AdminDTO adminDto = session.getCurrentAdmin();
        Admin adminEntity = SessionManager.getCurrentAdmin();
        if (adminDto == null || adminEntity == null) {
            navigator.clearAndPush(new WelcomeScreen());
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
                List<Loan> pendingLoans = null;
                try {
                    pendingLoans = loanController.getPendingLoans(adminEntity);
                } catch (Exception e) {
                    this.statusMessage = "Failed to load pending loans: " + e.getMessage();
                    this.isErrorStatus = true;
                }

                StringBuilder sb = new StringBuilder();
                if (firstRender) {
                    sb.append(ConsoleTheme.CLEAR_SCREEN);
                } else {
                    sb.append("\u001B[H");
                }

                if (pendingLoans == null || pendingLoans.isEmpty()) {
                    sb.append(TUIBox.top(width)).append("\n");
                    sb.append(TUIBox.line(" " + ConsoleTheme.bold("DIGIBANK CORE > ADMIN > LOAN UNDERWRITING REVIEW"), width)).append("\n");
                    sb.append(TUIBox.divider(width)).append("\n");
                    sb.append(TUIBox.emptyLine(width)).append("\n");
                    sb.append(TUIBox.center(ConsoleTheme.muted("No pending loan applications awaiting underwriting review."), width)).append("\n");
                    sb.append(TUIBox.emptyLine(width)).append("\n");
                    sb.append(TUIBox.line("   ► " + ConsoleTheme.highlight("[B] BACK TO ADMIN DASHBOARD"), width)).append("\n");
                    sb.append(TUIBox.bottom(width)).append("\n");

                    if (statusMessage != null) {
                        String statusDisplay = isErrorStatus ? ConsoleTheme.error(statusMessage) : ConsoleTheme.success(statusMessage);
                        sb.append(" Status: ").append(statusDisplay).append("\n");
                    }
                    sb.append(ConsoleTheme.muted("  [Enter/Esc] Return to Admin Dashboard")).append("\n");

                    System.out.print(sb.toString());
                    System.out.flush();
                    firstRender = false;

                    int ch = reader.read();
                    if (ch == 27 || ch == '\r' || ch == '\n' || ch == 'b' || ch == 'B' || ch == '0') {
                        navigator.pop();
                        return;
                    } else if (ch == 3) {
                        session.clearScreen();
                        System.exit(0);
                    }
                    continue;
                }

                if (currentIndex < 0 || currentIndex >= pendingLoans.size()) {
                    currentIndex = 0;
                }
                Loan targetLoan = pendingLoans.get(currentIndex);

                // Header
                sb.append(TUIBox.top(width)).append("\n");
                sb.append(TUIBox.line(" " + ConsoleTheme.bold("DIGIBANK CORE > ADMIN > LOAN UNDERWRITING REVIEW"), width)).append("\n");

                // Section 1: APPLICATION: #LN-xxxx
                sb.append(TUIBox.divider(width)).append("\n");
                String appTitle = "APPLICATION: #LN-" + targetLoan.getLoanId();
                if (pendingLoans.size() > 1) {
                    appTitle += " (" + (currentIndex + 1) + " of " + pendingLoans.size() + ")";
                }
                sb.append(TUIBox.line(" " + ConsoleTheme.bold(appTitle), width)).append("\n");
                sb.append(TUIBox.emptyLine(width)).append("\n");

                String applicantName = "USER #" + targetLoan.getUserId();
                try {
                    Optional<User> userOpt = ControllerFactory.getUserRepository().findById(targetLoan.getUserId());
                    if (userOpt.isPresent()) {
                        applicantName = userOpt.get().getFullName().toUpperCase();
                    }
                } catch (Exception ignored) {}

                String applicantStr = applicantName + " (#USR-" + targetLoan.getUserId() + ")";
                BigDecimal reqAmount = targetLoan.getRequestedAmount() != null ? targetLoan.getRequestedAmount() : BigDecimal.ZERO;
                String reqStr = ConsoleFormatter.formatCurrency(reqAmount) + " USD";
                int termMonths = targetLoan.getTermMonths() != null ? targetLoan.getTermMonths() : 12;
                String termStr = termMonths + " Months";

                BigDecimal mIncome = targetLoan.getMonthlyIncome() != null ? targetLoan.getMonthlyIncome() : BigDecimal.ZERO;
                String incomeStr = ConsoleFormatter.formatCurrency(mIncome) + " USD";

                BigDecimal mExp = targetLoan.getMonthlyExpense() != null ? targetLoan.getMonthlyExpense() : BigDecimal.ZERO;
                String expStr = ConsoleFormatter.formatCurrency(mExp) + " USD";

                BigDecimal debt = targetLoan.getExistingDebt() != null ? targetLoan.getExistingDebt() : BigDecimal.ZERO;
                String debtStr = ConsoleFormatter.formatCurrency(debt) + " USD";

                int cs = targetLoan.getCreditScore() != null ? targetLoan.getCreditScore() : 650;
                String csRating = cs >= 750 ? "Excellent" : cs >= 700 ? "Good" : cs >= 650 ? "Fair" : "Poor";
                String csStr = cs + " (" + csRating + ")";

                String riskScoreStr = targetLoan.getRiskScore() != null ? String.format("%.2f", targetLoan.getRiskScore().doubleValue()) : "28.40";
                String riskLevelStr = targetLoan.getRiskLevel() != null ? targetLoan.getRiskLevel().name() : "LOW";

                sb.append(TUIBox.line("  Applicant        : " + applicantStr, width)).append("\n");
                sb.append(TUIBox.line(String.format("  %-18s: %-22s %-14s: %s", "Requested Amount", reqStr, "Term Length", termStr), width)).append("\n");
                sb.append(TUIBox.line(String.format("  %-18s: %-22s %-14s: %s", "Monthly Income", incomeStr, "Monthly Exp.", expStr), width)).append("\n");
                sb.append(TUIBox.line(String.format("  %-18s: %-22s %-14s: %s", "Existing Debt", debtStr, "Credit Score", csStr), width)).append("\n");
                sb.append(TUIBox.line("  System Assessment: Risk Score " + riskScoreStr + " | Risk Level: " + riskLevelStr, width)).append("\n");

                // Section 2: UNDERWRITING DECISION
                sb.append(TUIBox.divider(width)).append("\n");
                sb.append(TUIBox.line(" " + ConsoleTheme.bold("UNDERWRITING DECISION"), width)).append("\n");
                sb.append(TUIBox.emptyLine(width)).append("\n");

                BigDecimal defaultApproved = targetLoan.getApprovedAmount() != null ? targetLoan.getApprovedAmount() : reqAmount;
                BigDecimal defaultRate = targetLoan.getInterestRate() != null ? targetLoan.getInterestRate() : new BigDecimal("6.75");
                String defaultReason = targetLoan.getRejectionReason() != null ? targetLoan.getRejectionReason() : "N/A";

                String appAmtField = String.format("   Approved Amount     : [ %-43s ]", ConsoleFormatter.formatCurrency(defaultApproved));
                String rateField   = String.format("   Interest Rate (%%/yr): [ %-43s ]", String.format("%.2f", defaultRate.doubleValue()));
                String reasonField = String.format("   Rejection Reason    : [ %-43s ]", defaultReason);

                sb.append(TUIBox.line(appAmtField, width)).append("\n");
                sb.append(TUIBox.line(rateField, width)).append("\n");
                sb.append(TUIBox.line(reasonField, width)).append("\n");
                sb.append(TUIBox.emptyLine(width)).append("\n");

                int totalOptions = (pendingLoans.size() > 1) ? 5 : 3;
                if (selectedIndex >= totalOptions) selectedIndex = 0;

                String bApprove = (selectedIndex == 0) ? ("► " + ConsoleTheme.highlight("[A] APPROVE APPLICATION")) : "  [A] APPROVE APPLICATION";
                String bReject  = (selectedIndex == 1) ? ("► " + ConsoleTheme.highlight("[R] REJECT APPLICATION"))  : "  [R] REJECT APPLICATION";
                String bBack    = (selectedIndex == 2) ? ("► " + ConsoleTheme.highlight("[B] BACK"))                : "  " + ConsoleTheme.muted("[B] BACK");

                if (pendingLoans.size() > 1) {
                    String bNext = (selectedIndex == 3) ? ("► " + ConsoleTheme.highlight("[N] NEXT APPLICATION"))      : "  [N] NEXT APPLICATION";
                    String bPrev = (selectedIndex == 4) ? ("► " + ConsoleTheme.highlight("[P] PREVIOUS APPLICATION"))  : "  [P] PREVIOUS APPLICATION";
                    sb.append(TUIBox.line("   " + bApprove + "       " + bReject + "       " + bBack, width)).append("\n");
                    sb.append(TUIBox.line("     " + bNext + "          " + bPrev, width)).append("\n");
                } else {
                    sb.append(TUIBox.line("   " + bApprove + "       " + bReject + "       " + bBack, width)).append("\n");
                }

                sb.append(TUIBox.bottom(width)).append("\n");

                if (statusMessage != null) {
                    String statusDisplay = isErrorStatus ? ConsoleTheme.error(statusMessage) : ConsoleTheme.success(statusMessage);
                    sb.append(" Status: ").append(statusDisplay).append("\n");
                }
                sb.append(ConsoleTheme.muted("  [↑/↓] Navigate  •  [Enter] Select  •  [A/R/N/P] Quick Action  •  [Esc] Back")).append("\n");

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
                            selectedIndex = (selectedIndex - 1 + totalOptions) % totalOptions;
                        } else if (code == 'B' || code == 'C') { // Down / Right
                            selectedIndex = (selectedIndex + 1) % totalOptions;
                        }
                    }
                } else if (ch == '\t') {
                    selectedIndex = (selectedIndex + 1) % totalOptions;
                } else if (ch == '\r' || ch == '\n') {
                    if (selectedIndex == 0) {
                        terminal.setAttributes(origAttributes);
                        handleApprove(session, targetLoan, adminEntity, defaultApproved, defaultRate);
                        origAttributes = terminal.enterRawMode();
                        firstRender = true;
                    } else if (selectedIndex == 1) {
                        terminal.setAttributes(origAttributes);
                        handleReject(session, targetLoan, adminEntity);
                        origAttributes = terminal.enterRawMode();
                        firstRender = true;
                    } else if (selectedIndex == 2) {
                        navigator.pop();
                        return;
                    } else if (selectedIndex == 3 && pendingLoans.size() > 1) {
                        currentIndex = (currentIndex + 1) % pendingLoans.size();
                    } else if (selectedIndex == 4 && pendingLoans.size() > 1) {
                        currentIndex = (currentIndex - 1 + pendingLoans.size()) % pendingLoans.size();
                    }
                } else if (ch == 'a' || ch == 'A' || ch == '1') {
                    terminal.setAttributes(origAttributes);
                    handleApprove(session, targetLoan, adminEntity, defaultApproved, defaultRate);
                    origAttributes = terminal.enterRawMode();
                    firstRender = true;
                } else if (ch == 'r' || ch == 'R' || ch == '2') {
                    terminal.setAttributes(origAttributes);
                    handleReject(session, targetLoan, adminEntity);
                    origAttributes = terminal.enterRawMode();
                    firstRender = true;
                } else if (ch == 'n' || ch == 'N') {
                    if (pendingLoans.size() > 1) {
                        currentIndex = (currentIndex + 1) % pendingLoans.size();
                    }
                } else if (ch == 'p' || ch == 'P') {
                    if (pendingLoans.size() > 1) {
                        currentIndex = (currentIndex - 1 + pendingLoans.size()) % pendingLoans.size();
                    }
                } else if (ch == 'b' || ch == 'B' || ch == '0') {
                    navigator.pop();
                    return;
                } else if (ch == 3) { // Ctrl+C
                    session.clearScreen();
                    System.exit(0);
                }
            }
        } catch (IOException e) {
            logger.error("Error in admin loan loop", e);
        } finally {
            terminal.setAttributes(origAttributes);
        }
    }

    private void handleApprove(TUISession session, Loan targetLoan, Admin adminEntity, BigDecimal defaultAmount, BigDecimal defaultRate) {
        session.clearScreen();
        int width = TUILayout.APP_WIDTH;
        System.out.println(TUIBox.top(width));
        System.out.println(TUIBox.line(" " + ConsoleTheme.bold("DIGIBANK CORE > ADMIN > UNDERWRITE LOAN #" + targetLoan.getLoanId()), width));
        System.out.println(TUIBox.divider(width));
        System.out.println(TUIBox.emptyLine(width));
        System.out.println(TUIBox.line("  Applicant: #USR-" + targetLoan.getUserId() + " | Requested: " + ConsoleFormatter.formatCurrency(targetLoan.getRequestedAmount()), width));
        System.out.println(TUIBox.emptyLine(width));
        System.out.println(TUIBox.bottom(width));
        System.out.println(TUIBox.rule(width));

        String amtStr = ConsolePrompt.promptOptional("Approved Amount ($)", defaultAmount.toPlainString());
        BigDecimal approvedAmt;
        try {
            approvedAmt = new BigDecimal(amtStr.trim());
        } catch (Exception e) {
            this.statusMessage = "Invalid approved amount format.";
            this.isErrorStatus = true;
            return;
        }

        String rateStr = ConsolePrompt.promptOptional("Interest Rate (%/year)", defaultRate.toPlainString());
        BigDecimal approvedRate;
        try {
            approvedRate = new BigDecimal(rateStr.trim());
        } catch (Exception e) {
            this.statusMessage = "Invalid interest rate format.";
            this.isErrorStatus = true;
            return;
        }

        boolean confirm = ConsolePrompt.promptConfirmation("Confirm approval of loan #" + targetLoan.getLoanId() + " for " + ConsoleFormatter.formatCurrency(approvedAmt) + "?");
        if (!confirm) {
            this.statusMessage = "Underwriting action cancelled.";
            this.isErrorStatus = false;
            return;
        }

        try {
            loanController.approveLoan(adminEntity, targetLoan.getLoanId(), targetLoan.getAccountId(), approvedAmt, approvedRate, targetLoan.getTermMonths());
            this.statusMessage = "Loan #" + targetLoan.getLoanId() + " approved successfully!";
            this.isErrorStatus = false;
        } catch (Exception e) {
            logger.error("Failed to approve loan", e);
            this.statusMessage = "Approval failed: " + e.getMessage();
            this.isErrorStatus = true;
        }
    }

    private void handleReject(TUISession session, Loan targetLoan, Admin adminEntity) {
        session.clearScreen();
        int width = TUILayout.APP_WIDTH;
        System.out.println(TUIBox.top(width));
        System.out.println(TUIBox.line(" " + ConsoleTheme.bold("DIGIBANK CORE > ADMIN > REJECT LOAN #" + targetLoan.getLoanId()), width));
        System.out.println(TUIBox.divider(width));
        System.out.println(TUIBox.emptyLine(width));
        System.out.println(TUIBox.line("  Applicant: #USR-" + targetLoan.getUserId() + " | Requested: " + ConsoleFormatter.formatCurrency(targetLoan.getRequestedAmount()), width));
        System.out.println(TUIBox.emptyLine(width));
        System.out.println(TUIBox.bottom(width));
        System.out.println(TUIBox.rule(width));

        String reason = ConsolePrompt.promptText("Rejection Reason (Debt-to-Income / Credit Score / Incomplete)");
        if (reason == null || reason.trim().isEmpty()) {
            this.statusMessage = "Rejection requires a valid reason.";
            this.isErrorStatus = true;
            return;
        }

        boolean confirm = ConsolePrompt.promptConfirmation("Confirm REJECTION of loan #" + targetLoan.getLoanId() + "?");
        if (!confirm) {
            this.statusMessage = "Underwriting action cancelled.";
            this.isErrorStatus = false;
            return;
        }

        try {
            loanController.rejectLoan(adminEntity, targetLoan.getLoanId(), reason.trim());
            this.statusMessage = "Loan #" + targetLoan.getLoanId() + " rejected.";
            this.isErrorStatus = false;
        } catch (Exception e) {
            logger.error("Failed to reject loan", e);
            this.statusMessage = "Rejection failed: " + e.getMessage();
            this.isErrorStatus = true;
        }
    }
}
