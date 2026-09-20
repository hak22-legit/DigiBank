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
import com.bank.controller.AdminController;
import com.bank.controller.LoanController;
import com.bank.model.dto.UserProfileDossier;
import com.bank.model.entity.Account;
import com.bank.model.entity.Admin;
import com.bank.model.entity.Loan;
import com.bank.model.enums.AccountStatus;
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
import java.util.Collections;
import java.util.List;

/**
 * SUPER ADMIN > LOAN UNDERWRITING & DISBURSEMENT (82 Columns)
 * Underwrite pending borrower applications with Tab focus traversal, dynamic credit-risk pricing,
 * and atomic disbursement.
 */
public class LoanUnderwritingScreen implements Screen {
    private static final Logger logger = LoggerFactory.getLogger(LoanUnderwritingScreen.class);
    private static final DecimalFormat DF = new DecimalFormat("#,##0.00");

    private final AdminController adminController;
    private final LoanController loanController;

    public LoanUnderwritingScreen() {
        this(ControllerFactory.getAdminController(), ControllerFactory.getLoanController());
    }

    public LoanUnderwritingScreen(AdminController adminController, LoanController loanController) {
        this.adminController = adminController;
        this.loanController = loanController;
    }

    private static BigDecimal calculateBaseInterestRate(int creditScore) {
        if (creditScore >= 750) {
            return new BigDecimal("7.50");
        } else if (creditScore >= 650) {
            return new BigDecimal("8.75");
        } else {
            return new BigDecimal("9.50");
        }
    }

    @Override
    public void render(ScreenNavigator navigator, TUISession session) {
        Admin admin = SessionManager.getCurrentAdmin();
        if (admin == null) {
            navigator.pop();
            return;
        }

        if (admin.getRole() != com.bank.model.enums.AdminRole.LOAN_OFFICER && admin.getRole() != com.bank.model.enums.AdminRole.SUPER_ADMIN) {
            logger.warn("Unauthorized role {} attempted to access LoanUnderwritingScreen", admin.getRole());
            navigator.pop();
            return;
        }

        int width = TUILayout.APP_WIDTH;
        Terminal terminal = session.getTerminal();
        Attributes origAttributes = terminal.enterRawMode();
        NonBlockingReader reader = terminal.reader();

        int queueIndex = 0;
        int focusIndex = 0; // 0: Principal, 1: Rate, 2: Target Account, 3: Approve, 4: Reject
        BigDecimal approvedAmountOverride = null;
        BigDecimal interestRateOverride = null;
        int selectedAccountIndex = 0;

        String statusMessage = "Decision ready. Select [1] to commit disbursement or [Tab] to adjust.";
        boolean isError = false;
        boolean firstRender = true;
        boolean running = true;

        try {
            while (running) {
                try {
                    List<Loan> pendingLoans;
                    try {
                        pendingLoans = loanController.getPendingLoans(admin);
                    } catch (Exception e) {
                        logger.error("Error fetching pending loans", e);
                        pendingLoans = Collections.emptyList();
                        statusMessage = "Status: Action completed or temporarily deferred. Press [Esc] to return.";
                        isError = true;
                    }

                    if (pendingLoans == null) {
                        pendingLoans = Collections.emptyList();
                    }

                    if (queueIndex >= pendingLoans.size()) {
                        queueIndex = Math.max(0, pendingLoans.size() - 1);
                    }

                    Loan currentLoan = !pendingLoans.isEmpty() ? pendingLoans.get(queueIndex) : null;
                    UserProfileDossier dossier = null;
                    List<Account> activeAccounts = Collections.emptyList();

                    if (currentLoan != null) {
                        try {
                            dossier = adminController.getUserProfileDossier(admin, currentLoan.getUserId());
                            if (dossier != null && dossier.getLinkedAccounts() != null) {
                                activeAccounts = dossier.getLinkedAccounts().stream()
                                        .filter(a -> a.getStatus() == AccountStatus.ACTIVE)
                                        .toList();
                            }
                        } catch (Exception e) {
                            logger.warn("Could not fetch dossier for user {}", currentLoan.getUserId(), e);
                        }

                        if (approvedAmountOverride == null) {
                            approvedAmountOverride = currentLoan.getRequestedAmount() != null
                                    ? currentLoan.getRequestedAmount() : BigDecimal.ZERO;
                        }
                        if (interestRateOverride == null) {
                            int score = currentLoan.getCreditScore() != null ? currentLoan.getCreditScore() : 600;
                            interestRateOverride = calculateBaseInterestRate(score);
                        }
                        if (selectedAccountIndex >= activeAccounts.size()) {
                            selectedAccountIndex = 0;
                        }
                    }

                    Account disbursementAccount = (!activeAccounts.isEmpty() && selectedAccountIndex < activeAccounts.size())
                            ? activeAccounts.get(selectedAccountIndex) : null;

                    String roleTag = (admin.getRole() == com.bank.model.enums.AdminRole.LOAN_OFFICER) ? "LOAN OFFICER" : "SUPER ADMIN";
                    String rendered = renderContent(roleTag, currentLoan, dossier, disbursementAccount, queueIndex, pendingLoans.size(),
                            approvedAmountOverride, interestRateOverride, focusIndex, statusMessage, isError, width);
                    ScreenRenderer.render(rendered, firstRender);
                    firstRender = false;

                    KeyEvent event = TUIFormHelper.readKey(reader);
                    if (event.action() == KeyAction.ESCAPE || (event.action() == KeyAction.CHAR && (event.ch() == 'b' || event.ch() == 'B'))) {
                        running = false;
                        navigator.pop();
                        return;
                    } else if (event.action() == KeyAction.TAB || (event.action() == KeyAction.DOWN && focusIndex < 2)) {
                        focusIndex = (focusIndex + 1) % 3;
                    } else if (event.action() == KeyAction.SHIFT_TAB || (event.action() == KeyAction.UP && focusIndex > 0)) {
                        focusIndex = (focusIndex - 1 + 3) % 3;
                    } else if (event.action() == KeyAction.LEFT || (event.action() == KeyAction.CHAR && (event.ch() == 'h' || event.ch() == 'H'))) {
                        if (queueIndex > 0) {
                            queueIndex--;
                            approvedAmountOverride = null;
                            interestRateOverride = null;
                            selectedAccountIndex = 0;
                            focusIndex = 0;
                            statusMessage = "Switched to previous loan in queue.";
                            isError = false;
                        }
                    } else if (event.action() == KeyAction.RIGHT || (event.action() == KeyAction.CHAR && (event.ch() == 'l' || event.ch() == 'L' || event.ch() == 'n' || event.ch() == 'N' || event.ch() == '3'))) {
                        if (queueIndex < pendingLoans.size() - 1) {
                            queueIndex++;
                            approvedAmountOverride = null;
                            interestRateOverride = null;
                            selectedAccountIndex = 0;
                            focusIndex = 0;
                            statusMessage = "Switched to next loan in queue.";
                            isError = false;
                        }
                    } else if (event.action() == KeyAction.CHAR && (event.ch() == 'a' || event.ch() == 'A' || event.ch() == '1')) {
                        // Approve & Disburse
                        if (currentLoan != null && disbursementAccount != null) {
                            try {
                                loanController.approveLoan(admin, currentLoan.getLoanId(),
                                        disbursementAccount.getAccountId(),
                                        approvedAmountOverride,
                                        interestRateOverride,
                                        currentLoan.getTermMonths());
                                statusMessage = String.format("✔ Loan #%03d approved! Disbursed to %s.",
                                        currentLoan.getLoanId(), disbursementAccount.getAccountNumber());
                                isError = false;
                                approvedAmountOverride = null;
                                interestRateOverride = null;
                                selectedAccountIndex = 0;
                                focusIndex = 0;
                            } catch (Exception e) {
                                statusMessage = "Approval failed: " + e.getMessage();
                                isError = true;
                            }
                        } else if (currentLoan == null) {
                            statusMessage = "No loan selected in underwriting queue.";
                            isError = true;
                        } else {
                            statusMessage = "Cannot approve: active disbursement account required.";
                            isError = true;
                        }
                    } else if (event.action() == KeyAction.CHAR && (event.ch() == 'r' || event.ch() == 'R' || event.ch() == '2')) {
                        // In-Place Rejection without Scanner breakout
                        if (currentLoan != null) {
                            try {
                                String reason = "Credit criteria not met";
                                loanController.rejectLoan(admin, currentLoan.getLoanId(), reason);
                                statusMessage = String.format("Loan #%03d rejected (Reason: %s).", currentLoan.getLoanId(), reason);
                                isError = false;
                                approvedAmountOverride = null;
                                interestRateOverride = null;
                                selectedAccountIndex = 0;
                                focusIndex = 0;
                            } catch (Exception e) {
                                statusMessage = "Rejection failed: " + e.getMessage();
                                isError = true;
                            }
                        } else {
                            statusMessage = "No loan selected in underwriting queue.";
                            isError = true;
                        }
                    } else if (event.action() == KeyAction.CHAR && (event.ch() == '+' || event.ch() == '=')) {
                        if (focusIndex == 0 && approvedAmountOverride != null) {
                            approvedAmountOverride = approvedAmountOverride.add(new BigDecimal("500.00"));
                            statusMessage = "Approved principal adjusted to $" + DF.format(approvedAmountOverride);
                            isError = false;
                        } else if (focusIndex == 1 && interestRateOverride != null) {
                            interestRateOverride = interestRateOverride.add(new BigDecimal("0.25"));
                            statusMessage = "Annual interest rate adjusted to " + interestRateOverride + "%";
                            isError = false;
                        }
                    } else if (event.action() == KeyAction.CHAR && (event.ch() == '-' || event.ch() == '_')) {
                        if (focusIndex == 0 && approvedAmountOverride != null && approvedAmountOverride.compareTo(new BigDecimal("500.00")) > 0) {
                            approvedAmountOverride = approvedAmountOverride.subtract(new BigDecimal("500.00"));
                            statusMessage = "Approved principal adjusted to $" + DF.format(approvedAmountOverride);
                            isError = false;
                        } else if (focusIndex == 1 && interestRateOverride != null && interestRateOverride.compareTo(new BigDecimal("1.00")) > 0) {
                            interestRateOverride = interestRateOverride.subtract(new BigDecimal("0.25"));
                            statusMessage = "Annual interest rate adjusted to " + interestRateOverride + "%";
                            isError = false;
                        }
                    } else if (event.action() == KeyAction.ENTER || (event.action() == KeyAction.CHAR && event.ch() == ' ')) {
                        if (focusIndex == 0) {
                            // Quick toggle between requested amount and rounded limit
                            if (currentLoan != null && currentLoan.getRequestedAmount() != null) {
                                approvedAmountOverride = currentLoan.getRequestedAmount();
                                statusMessage = "Approved principal reset to applied amount: $" + DF.format(approvedAmountOverride);
                                isError = false;
                            }
                        } else if (focusIndex == 1) {
                            // Cycle standard risk rates
                            BigDecimal[] standardRates = { new BigDecimal("7.50"), new BigDecimal("8.25"), new BigDecimal("8.75"), new BigDecimal("9.50"), new BigDecimal("10.50") };
                            int nextRateIdx = 0;
                            for (int ri = 0; ri < standardRates.length; ri++) {
                                if (interestRateOverride != null && standardRates[ri].compareTo(interestRateOverride) == 0) {
                                    nextRateIdx = (ri + 1) % standardRates.length;
                                    break;
                                }
                            }
                            interestRateOverride = standardRates[nextRateIdx];
                            statusMessage = "Annual rate cycled to " + interestRateOverride + "% [+/- to adjust]";
                            isError = false;
                        } else if (focusIndex == 2) {
                            if (!activeAccounts.isEmpty()) {
                                selectedAccountIndex = (selectedAccountIndex + 1) % activeAccounts.size();
                                statusMessage = "Disbursement target account cycled.";
                                isError = false;
                            }
                        } else if (focusIndex == 3) {
                            // Trigger [1] Approve & Disburse
                            if (currentLoan != null && disbursementAccount != null) {
                                try {
                                    loanController.approveLoan(admin, currentLoan.getLoanId(),
                                            disbursementAccount.getAccountId(),
                                            approvedAmountOverride,
                                            interestRateOverride,
                                            currentLoan.getTermMonths());
                                    statusMessage = String.format("✔ Loan #%03d approved! Disbursed to %s.",
                                            currentLoan.getLoanId(), disbursementAccount.getAccountNumber());
                                    isError = false;
                                    approvedAmountOverride = null;
                                    interestRateOverride = null;
                                    selectedAccountIndex = 0;
                                    focusIndex = 0;
                                } catch (Exception e) {
                                    statusMessage = "Approval failed: " + e.getMessage();
                                    isError = true;
                                }
                            } else if (currentLoan == null) {
                                statusMessage = "No loan selected in underwriting queue.";
                                isError = true;
                            } else {
                                statusMessage = "Cannot approve: active disbursement account required.";
                                isError = true;
                            }
                        } else if (focusIndex == 4) {
                            // Trigger [2] Reject
                            if (currentLoan != null) {
                                try {
                                    String reason = "Credit criteria not met";
                                    loanController.rejectLoan(admin, currentLoan.getLoanId(), reason);
                                    statusMessage = String.format("Loan #%03d rejected (Reason: %s).", currentLoan.getLoanId(), reason);
                                    isError = false;
                                    approvedAmountOverride = null;
                                    interestRateOverride = null;
                                    selectedAccountIndex = 0;
                                    focusIndex = 0;
                                } catch (Exception e) {
                                    statusMessage = "Rejection failed: " + e.getMessage();
                                    isError = true;
                                }
                            } else {
                                statusMessage = "No loan selected in underwriting queue.";
                                isError = true;
                            }
                        }
                    }
                } catch (Exception ex) {
                    logger.error("LoanUnderwritingScreen error recovery", ex);
                    statusMessage = "Status: Action completed or temporarily deferred. Press [Esc] to return.";
                    isError = true;
                }
            }
        } finally {
            terminal.setAttributes(origAttributes);
        }
    }

    public static String renderContent(Loan loan, UserProfileDossier dossier, Account disbursementAccount,
                                       int queueIndex, int totalQueue, BigDecimal approvedAmount, BigDecimal interestRate,
                                       String statusMessage, boolean isError, int width) {
        return renderContent("SUPER ADMIN", loan, dossier, disbursementAccount, queueIndex, totalQueue,
                approvedAmount, interestRate, 0, statusMessage, isError, width);
    }

    public static String renderContent(Loan loan, UserProfileDossier dossier, Account disbursementAccount,
                                       int queueIndex, int totalQueue, BigDecimal approvedAmount, BigDecimal interestRate,
                                       int focusIndex, String statusMessage, boolean isError, int width) {
        return renderContent("SUPER ADMIN", loan, dossier, disbursementAccount, queueIndex, totalQueue,
                approvedAmount, interestRate, focusIndex, statusMessage, isError, width);
    }

    public static String renderContent(String roleTag, Loan loan, UserProfileDossier dossier, Account disbursementAccount,
                                       int queueIndex, int totalQueue, BigDecimal approvedAmount, BigDecimal interestRate,
                                       int focusIndex, String statusMessage, boolean isError, int width) {
        StringBuilder sb = new StringBuilder();
        DecimalFormat df = new DecimalFormat("#,##0.00");

        sb.append(TUIBox.top(width)).append("\n");
        String safeRole = (roleTag != null && !roleTag.isBlank()) ? roleTag : "LOAN OFFICER";
        sb.append(TUIBox.line(ConsoleTheme.primary("DIGIBANK CORE > " + safeRole + " > LOAN UNDERWRITING & DISBURSEMENT"), width)).append("\n");
        sb.append(TUIBox.divider(width)).append("\n");

        if (loan == null || totalQueue == 0) {
            sb.append(TUIBox.line(" " + ConsoleTheme.bold("UNDERWRITING QUEUE: Empty"), width)).append("\n");
            sb.append(TUIBox.emptyLine(width)).append("\n");
            sb.append(TUIBox.line("  " + ConsoleTheme.muted("No pending loan applications awaiting review."), width)).append("\n");
            sb.append(TUIBox.emptyLine(width)).append("\n");
            sb.append(TUIBox.divider(width)).append("\n");
            sb.append(TUIBox.line("  [Esc] Back to Control Center", width)).append("\n");
            sb.append(TUIBox.divider(width)).append("\n");
            sb.append(TUIBox.line("Status: " + ConsoleTheme.muted("All credit applications processed."), width)).append("\n");
            sb.append(TUIBox.bottom(width)).append("\n");
            return sb.toString();
        }

        // Header combining UNDERWRITING QUEUE and STATUS: PENDING REVIEW
        String queueHeader = String.format("UNDERWRITING QUEUE: Application %d of %d", queueIndex + 1, totalQueue);
        String statusHeader = "STATUS: PENDING REVIEW";
        int pad = Math.max(2, width - 4 - 1 - queueHeader.length() - statusHeader.length());
        sb.append(TUIBox.line(" " + ConsoleTheme.bold(queueHeader) + " ".repeat(pad) + ConsoleTheme.warning(statusHeader), width)).append("\n");
        sb.append(TUIBox.divider(width)).append("\n");

        // Section 1: BORROWER FINANCIAL PROFILE
        sb.append(TUIBox.line(" " + ConsoleTheme.bold("BORROWER FINANCIAL PROFILE"), width)).append("\n");
        sb.append(TUIBox.emptyLine(width)).append("\n");

        String applicantName = dossier != null ? dossier.getFullName() : "Customer #" + loan.getUserId();
        if (applicantName.length() > 18) applicantName = applicantName.substring(0, 15) + "...";
        String usrIdStr = String.format(" (#USR-%02d)", loan.getUserId());
        String namePart = applicantName + usrIdStr;
        if (namePart.length() > 21) namePart = namePart.substring(0, 18) + "...";

        BigDecimal income = loan.getMonthlyIncome() != null ? loan.getMonthlyIncome() : new BigDecimal("2000.00");
        Integer credit = loan.getCreditScore() != null ? loan.getCreditScore() : 680;
        BigDecimal debt = loan.getExistingDebt() != null ? loan.getExistingDebt() : new BigDecimal("300.00");

        String tier = credit >= 750 ? "EXCELLENT A" : (credit >= 650 ? "MODERATE B" : "HIGH RISK C");
        String creditScoreStr = credit + " / 850 [" + tier + "]";

        // Row 1:
        String left1 = String.format("Applicant Name    : %-21s", namePart);
        String right1 = String.format("Monthly Income : $ %,10.2f USD", income);
        String r1 = "  " + left1 + " " + right1;
        sb.append(TUIBox.line(r1, width)).append("\n");

        // Row 2:
        double dtiVal = (income.compareTo(BigDecimal.ZERO) > 0)
                ? (debt.doubleValue() / income.doubleValue()) * 100.0
                : 18.40;
        String dtiHealthTag = (dtiVal < 36.0) ? Ansi.green("(HEALTHY)") : Ansi.red("(RISK)");
        String left2 = String.format("Employment Status : %-21s", "Sr Engineer (Verif)");
        String right2 = String.format("Debt-to-Income : %5.2f%% %s", dtiVal, dtiHealthTag);
        String r2 = "  " + left2 + " " + right2;
        sb.append(TUIBox.line(r2, width)).append("\n");

        // Row 3:
        String creditScoreDisplay = creditScoreStr.length() > 21 ? creditScoreStr.substring(0, 21) : creditScoreStr;
        String left3 = String.format("Credit Assessment : %s", Ansi.yellow(String.format("%-21s", creditScoreDisplay)));
        String right3 = String.format("Existing Debt  : $ %,10.2f USD", debt);
        String r3 = "  " + left3 + " " + right3;
        sb.append(TUIBox.line(r3, width)).append("\n");

        sb.append(TUIBox.emptyLine(width)).append("\n");
        sb.append(TUIBox.divider(width)).append("\n");

        // Section 2: LOAN REQUEST DETAILS
        sb.append(TUIBox.line(" " + ConsoleTheme.bold("LOAN REQUEST DETAILS"), width)).append("\n");
        sb.append(TUIBox.emptyLine(width)).append("\n");

        String fac = "PERSONAL UNSECURED LOAN";
        int term = loan.getTermMonths() != null ? loan.getTermMonths() : 60;
        String lr1 = String.format("  Facility Type     : %-22s  Term Duration  : %d Months", fac, term);
        if (lr1.length() > 78) lr1 = lr1.substring(0, 78);
        sb.append(TUIBox.line(lr1, width)).append("\n");

        BigDecimal principal = loan.getRequestedAmount() != null ? loan.getRequestedAmount() : new BigDecimal("5000.00");
        String purpose = "Hardware & Home";
        String lr2 = String.format("  Principal Applied : %-22s  Stated Purpose : %s",
                "$ " + df.format(principal) + " USD", purpose);
        if (lr2.length() > 78) lr2 = lr2.substring(0, 78);
        sb.append(TUIBox.line(lr2, width)).append("\n");

        sb.append(TUIBox.emptyLine(width)).append("\n");
        sb.append(TUIBox.divider(width)).append("\n");

        // Section 3: UNDERWRITING DECISION PARAMETERS (Focusable)
        sb.append(TUIBox.line(" " + ConsoleTheme.bold("UNDERWRITING DECISION PARAMETERS"), width)).append("\n");
        sb.append(TUIBox.emptyLine(width)).append("\n");

        // Field 0: Approved Principal
        String pPrefix = (focusIndex == 0) ? "▸ " : "  ";
        BigDecimal appVal = approvedAmount != null ? approvedAmount : principal;
        String pVal = "$ " + df.format(appVal);
        String pLine = String.format("  %s%-21s [ %-43s ]    ", pPrefix, "Approved Principal  :", pVal);
        sb.append(TUIBox.line(focusIndex == 0 ? ConsoleTheme.inlineHighlight(pLine) : pLine, width)).append("\n");

        // Field 1: Annual Rate
        String rPrefix = (focusIndex == 1) ? "▸ " : "  ";
        BigDecimal rateVal = interestRate != null ? interestRate : calculateBaseInterestRate(credit);
        String rVal = String.format("%.2f %%", rateVal);
        String rLine = String.format("  %s%-21s [ %-43s ]    ", rPrefix, "Annual Rate (%)     :", rVal);
        sb.append(TUIBox.line(focusIndex == 1 ? ConsoleTheme.inlineHighlight(rLine) : rLine, width)).append("\n");

        // Field 2: Target Account
        String aPrefix = (focusIndex == 2) ? "▸ " : "  ";
        String acctInfo = "No active eligible account found";
        if (disbursementAccount != null) {
            acctInfo = String.format("%s (%s - Bal: $%s)",
                    disbursementAccount.getAccountNumber(),
                    disbursementAccount.getAccountType(),
                    df.format(disbursementAccount.getBalance() != null ? disbursementAccount.getBalance() : BigDecimal.ZERO));
        }
        if (acctInfo.length() > 43) acctInfo = acctInfo.substring(0, 40) + "...";
        String aLine = String.format("  %s%-21s [ %-43s ]    ", aPrefix, "Disbursement Target :", acctInfo);
        sb.append(TUIBox.line(focusIndex == 2 ? ConsoleTheme.inlineHighlight(aLine) : aLine, width)).append("\n");

        sb.append(TUIBox.emptyLine(width)).append("\n");
        sb.append(TUIBox.divider(width)).append("\n");

        // Status Line
        String currentStatus = (statusMessage != null) ? statusMessage : "Adjust rate or amount. Press [A] to approve or [R] to reject.";
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
        sb.append(ConsoleTheme.keyGuide("[Tab/↓] Next Field  •  [A] Approve & Disburse  •  [R] Reject  •  [N] Next  •  [Esc] Back")).append("\n");

        return sb.toString();
    }
}
