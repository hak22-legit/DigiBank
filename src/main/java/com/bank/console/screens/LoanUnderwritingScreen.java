package com.bank.console.screens;

import com.bank.console.ControllerFactory;
import com.bank.console.ScreenNavigator;
import com.bank.console.TUISession;
import com.bank.console.TerminalInputHandler;
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
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import com.bank.model.enums.LoanStatus;

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

        List<Loan> loanQueue = new ArrayList<>();
        try {
            loanQueue = new ArrayList<>(loanController.getPendingLoans(admin));
        } catch (Exception e) {
            logger.error("Error fetching pending loans", e);
        }

        try {
            TerminalInputHandler.drainBuffer(reader);
            while (running) {
                try {
                    if (loanQueue == null) {
                        loanQueue = new ArrayList<>();
                    }

                    if (queueIndex >= loanQueue.size()) {
                        queueIndex = Math.max(0, loanQueue.size() - 1);
                    }

                    Loan currentLoan = !loanQueue.isEmpty() ? loanQueue.get(queueIndex) : null;
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

                    boolean isActioned = (currentLoan != null && (currentLoan.getStatus() == LoanStatus.REJECTED
                            || currentLoan.getStatus() == LoanStatus.APPROVED || currentLoan.getStatus() == LoanStatus.ACTIVE));

                    Account disbursementAccount = (!activeAccounts.isEmpty() && selectedAccountIndex < activeAccounts.size())
                            ? activeAccounts.get(selectedAccountIndex) : null;

                    String roleTag = (admin.getRole() == com.bank.model.enums.AdminRole.LOAN_OFFICER) ? "LOAN OFFICER" : "SUPER ADMIN";
                    String rendered = renderContent(roleTag, currentLoan, dossier, disbursementAccount, queueIndex, loanQueue.size(),
                            approvedAmountOverride, interestRateOverride, focusIndex, statusMessage, isError, width);
                    ScreenRenderer.render(rendered, firstRender);
                    firstRender = false;

                    TerminalInputHandler.KeyCode event = TerminalInputHandler.readNavigationKey(reader);
                    if (TerminalInputHandler.isEscapeOrBack(event) || event.is('0')) {
                        running = false;
                        navigator.pop();
                        return;
                    } else if (event.isTab() || ((event.isDown() || event.is('j') || event.is('J')) && focusIndex < 2)) {
                        if (!isActioned) {
                            focusIndex = (focusIndex + 1) % 3;
                        }
                    } else if (event.isShiftTab() || ((event.isUp() || event.is('k') || event.is('K')) && focusIndex > 0)) {
                        if (!isActioned) {
                            focusIndex = (focusIndex - 1 + 3) % 3;
                        }
                    } else if (event.isLeft() || event.is('P') || event.is('p') || event.is('h') || event.is('H')) {
                        if (queueIndex > 0) {
                            queueIndex--;
                            approvedAmountOverride = null;
                            interestRateOverride = null;
                            selectedAccountIndex = 0;
                            focusIndex = 0;
                            statusMessage = "Switched to previous loan in queue.";
                            isError = false;
                        }
                    } else if (event.isRight() || event.is('N') || event.is('n') || event.is('l') || event.is('L') || event.is('3')) {
                        if (queueIndex < loanQueue.size() - 1) {
                            queueIndex++;
                            approvedAmountOverride = null;
                            interestRateOverride = null;
                            selectedAccountIndex = 0;
                            focusIndex = 0;
                            statusMessage = "Switched to next loan in queue.";
                            isError = false;
                        }
                    } else if (event.is('A') || event.is('a') || event.is('1')) {
                        // Approve & Disburse (only if PENDING)
                        if (currentLoan == null) {
                            statusMessage = "No loan selected in underwriting queue.";
                            isError = true;
                        } else if (currentLoan.getStatus() != LoanStatus.PENDING) {
                            statusMessage = "Cannot approve: application has already been actioned (" + currentLoan.getStatus() + ").";
                            isError = true;
                        } else if (disbursementAccount == null) {
                            statusMessage = "Cannot approve: active disbursement account required.";
                            isError = true;
                        } else {
                            try {
                                Loan updated = loanController.approveLoan(admin, currentLoan.getLoanId(),
                                        disbursementAccount.getAccountId(),
                                        approvedAmountOverride,
                                        interestRateOverride,
                                        currentLoan.getTermMonths());
                                Long approvedId = currentLoan.getLoanId();
                                String accNum = disbursementAccount.getAccountNumber();
                                loanQueue.remove(queueIndex);
                                if (queueIndex >= loanQueue.size() && !loanQueue.isEmpty()) {
                                    queueIndex = loanQueue.size() - 1;
                                }
                                statusMessage = String.format("✔ Loan #%03d approved! Disbursed to %s.", approvedId, accNum);
                                isError = false;
                                approvedAmountOverride = null;
                                interestRateOverride = null;
                                selectedAccountIndex = 0;
                                focusIndex = 0;
                            } catch (Exception e) {
                                statusMessage = "Approval failed: " + e.getMessage();
                                isError = true;
                            }
                        }
                    } else if (event.is('R') || event.is('r') || event.is('2')) {
                        // In-Place Rejection (only if PENDING)
                        if (currentLoan == null) {
                            statusMessage = "No loan selected in underwriting queue.";
                            isError = true;
                        } else if (currentLoan.getStatus() != LoanStatus.PENDING) {
                            statusMessage = "Cannot reject: application has already been actioned (" + currentLoan.getStatus() + ").";
                            isError = true;
                        } else {
                            try {
                                String reason = "Credit criteria not met (Score < 600, DTI > 40%)";
                                Loan updated = loanController.rejectLoan(admin, currentLoan.getLoanId(), reason);
                                Long rejectedId = currentLoan.getLoanId();
                                loanQueue.remove(queueIndex);
                                if (queueIndex >= loanQueue.size() && !loanQueue.isEmpty()) {
                                    queueIndex = loanQueue.size() - 1;
                                }
                                statusMessage = String.format("Application #%03d rejected. Record archived to underwriting history.", rejectedId);
                                isError = false;
                                approvedAmountOverride = null;
                                interestRateOverride = null;
                                selectedAccountIndex = 0;
                                focusIndex = 0;
                            } catch (Exception e) {
                                statusMessage = "Rejection failed: " + e.getMessage();
                                isError = true;
                            }
                        }
                    } else if (!isActioned && (event.is('+') || event.is('='))) {
                        if (focusIndex == 0 && approvedAmountOverride != null) {
                            approvedAmountOverride = approvedAmountOverride.add(new BigDecimal("500.00"));
                            statusMessage = "Approved principal adjusted to $" + DF.format(approvedAmountOverride);
                            isError = false;
                        } else if (focusIndex == 1 && interestRateOverride != null) {
                            interestRateOverride = interestRateOverride.add(new BigDecimal("0.25"));
                            statusMessage = "Annual interest rate adjusted to " + interestRateOverride + "%";
                            isError = false;
                        }
                    } else if (!isActioned && (event.is('-') || event.is('_'))) {
                        if (focusIndex == 0 && approvedAmountOverride != null && approvedAmountOverride.compareTo(new BigDecimal("500.00")) > 0) {
                            approvedAmountOverride = approvedAmountOverride.subtract(new BigDecimal("500.00"));
                            statusMessage = "Approved principal adjusted to $" + DF.format(approvedAmountOverride);
                            isError = false;
                        } else if (focusIndex == 1 && interestRateOverride != null && interestRateOverride.compareTo(new BigDecimal("1.00")) > 0) {
                            interestRateOverride = interestRateOverride.subtract(new BigDecimal("0.25"));
                            statusMessage = "Annual interest rate adjusted to " + interestRateOverride + "%";
                            isError = false;
                        }
                    } else if (!isActioned && (event.isEnter() || event.is(' '))) {
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

        // Header combining UNDERWRITING QUEUE and STATUS:
        String queueHeader = String.format("UNDERWRITING QUEUE: Application %d of %d", queueIndex + 1, totalQueue);
        boolean isRejected = (loan.getStatus() == LoanStatus.REJECTED);
        boolean isApproved = (loan.getStatus() == LoanStatus.APPROVED || loan.getStatus() == LoanStatus.ACTIVE);
        String statusHeader;
        String coloredStatusHeader;
        if (isRejected) {
            statusHeader = "STATUS: REJECTED";
            coloredStatusHeader = "\033[31m" + statusHeader + "\033[0m";
        } else if (isApproved) {
            statusHeader = "STATUS: APPROVED";
            coloredStatusHeader = "\033[32m" + statusHeader + "\033[0m";
        } else {
            statusHeader = "STATUS: PENDING REVIEW";
            coloredStatusHeader = ConsoleTheme.warning(statusHeader);
        }
        int pad = Math.max(2, width - 4 - 1 - queueHeader.length() - statusHeader.length());
        sb.append(TUIBox.line(" " + ConsoleTheme.bold(queueHeader) + " ".repeat(pad) + coloredStatusHeader, width)).append("\n");
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

        if (isRejected) {
            // Section 3: FINAL UNDERWRITING DECISION
            sb.append(TUIBox.line(" " + ConsoleTheme.bold("FINAL UNDERWRITING DECISION"), width)).append("\n");
            sb.append(TUIBox.emptyLine(width)).append("\n");

            String dispVal = Ansi.red("REJECTED (ADVERSE ACTION)");
            String dispLine = String.format("  %-17s: %s", "Disposition", dispVal);
            sb.append(TUIBox.line(dispLine, width)).append("\n");

            String reason = (loan.getRejectionReason() != null && !loan.getRejectionReason().isBlank())
                    ? loan.getRejectionReason()
                    : "Credit criteria not met (Score < 600, DTI > 40%)";
            String reasonLine = String.format("  %-17s: %s", "Primary Reason", reason);
            sb.append(TUIBox.line(reasonLine, width)).append("\n");

            String officerId = (loan.getApprovedBy() != null) ? String.format("#ADM-%02d", loan.getApprovedBy()) : "#ADM-01";
            String roleStr = safeRole.replace(" ", "_");
            String timeStr = (loan.getApprovedAt() != null)
                    ? loan.getApprovedAt().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
                    : LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            String actionedByVal = String.format("%s (%s) at %s", officerId, roleStr, timeStr);
            String actionedLine = String.format("  %-17s: %s", "Actioned By", actionedByVal);
            sb.append(TUIBox.line(actionedLine, width)).append("\n");

            sb.append(TUIBox.emptyLine(width)).append("\n");
            sb.append(TUIBox.divider(width)).append("\n");
        } else {
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
        }

        // Status Line
        String defaultStatus = isRejected
                ? String.format("Application #%03d rejected. Record archived to underwriting history.", loan.getLoanId())
                : "Adjust rate or amount. Press [A] to approve or [R] to reject.";
        String currentStatus = (statusMessage != null) ? statusMessage : defaultStatus;
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
        if (isRejected || isApproved) {
            sb.append(ConsoleTheme.keyGuide("[N] Next Application  •  [P] Previous  •  [Esc] Back to Queue")).append("\n");
        } else {
            sb.append(ConsoleTheme.keyGuide("[Tab/↓] Next Field  •  [A] Approve & Disburse  •  [R] Reject  •  [N] Next  •  [Esc] Back")).append("\n");
        }

        return sb.toString();
    }
}
