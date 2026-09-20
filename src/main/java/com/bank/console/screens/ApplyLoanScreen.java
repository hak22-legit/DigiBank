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
import com.bank.controller.LoanController;
import com.bank.database.DatabaseConnection;
import com.bank.model.dto.AccountDTO;
import com.bank.model.dto.UserDTO;
import com.bank.model.entity.Account;
import com.bank.model.entity.Loan;
import com.bank.model.entity.LoanPayment;
import com.bank.model.entity.Transaction;
import com.bank.model.entity.User;
import com.bank.model.enums.*;
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
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Interactive 82-column Apply for Loan & Real-Time Underwriting Workflow.
 */
public class ApplyLoanScreen implements Screen {
    private static final Logger logger = LoggerFactory.getLogger(ApplyLoanScreen.class);
    private static final int[] AVAILABLE_TERMS = {6, 12, 24, 36};

    private final LoanController loanController;
    private final AccountController accountController;

    public ApplyLoanScreen() {
        this(ControllerFactory.getLoanController(), ControllerFactory.getAccountController());
    }

    public ApplyLoanScreen(LoanController loanController, AccountController accountController) {
        this.loanController = loanController;
        this.accountController = accountController;
    }

    private enum ViewState {
        FORM,
        CONTRACT_CONFIRMATION
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

        List<AccountDTO> accounts = accountController.getAccountsForUser(userEntity);
        if (accounts == null || accounts.isEmpty()) {
            TUILayout.printAlert("No active accounts available for disbursement.", true);
            navigator.pop();
            return;
        }

        AccountDTO disbursementAccount = accounts.get(0);

        // Form Fields:
        // 0: Requested Loan ($)
        // 1: Repayment Term (Months)
        // 2: Monthly Income
        // 3: Monthly Living Exp
        // 4: Existing Debt Service
        // 5: Credit Score
        // 6: Disbursement Account (modal)
        // 7: Action Bar ([1] Review & Confirm Application, [2] Cancel & Return)
        int focusedField = 0;
        int actionIndex = 0; // 0: Review & Confirm, 1: Cancel

        StringBuilder loanAmtBuf = new StringBuilder("1000.00");
        int termIdx = 1; // 12 Months
        StringBuilder incomeBuf = new StringBuilder("2000.00");
        StringBuilder expenseBuf = new StringBuilder("500.00");
        StringBuilder debtBuf = new StringBuilder("300.00");
        StringBuilder scoreBuf = new StringBuilder("600");

        ViewState state = ViewState.FORM;
        int confirmActionIdx = 0; // 0: Accept & Disburse, 1: Back to Edit

        String statusMessage = "Ready for underwriting assessment.";
        boolean isErrorStatus = false;

        Terminal terminal = session.getTerminal();
        Attributes origAttributes = terminal.enterRawMode();
        NonBlockingReader reader = terminal.reader();
        boolean firstRender = true;

        try {
            while (true) {
                // Parse values safely for real-time preview
                BigDecimal requestedAmount = parseDecimal(loanAmtBuf.toString(), new BigDecimal("1000.00"));
                int termMonths = AVAILABLE_TERMS[termIdx];
                BigDecimal monthlyIncome = parseDecimal(incomeBuf.toString(), new BigDecimal("2000.00"));
                BigDecimal livingExp = parseDecimal(expenseBuf.toString(), new BigDecimal("500.00"));
                BigDecimal existingDebt = parseDecimal(debtBuf.toString(), new BigDecimal("300.00"));
                int creditScore = parseInteger(scoreBuf.toString(), 600);

                // Real-time underwriting calculations
                BigDecimal aprRate = calculateAPR(creditScore);
                String aprTierLabel = getAPRTierLabel(creditScore, aprRate);
                BigDecimal monthlyPayment = calculateMonthlyPayment(requestedAmount, aprRate, termMonths);
                BigDecimal dti = calculateDTI(monthlyIncome, existingDebt, monthlyPayment);
                String riskRating = calculateRiskRating(creditScore, dti);

                if (state == ViewState.FORM) {
                    StringBuilder sb = new StringBuilder();
                    sb.append(TUIBox.top(width)).append("\n");
                    sb.append(TUIBox.line(ConsoleTheme.primary("DIGIBANK CORE > LOAN MANAGEMENT > APPLY FOR NEW FACILITY"), width)).append("\n");
                    sb.append(TUIBox.divider(width)).append("\n");
                    sb.append(TUIBox.line("APPLICANT FINANCIAL PROFILE", width)).append("\n");
                    sb.append(TUIBox.emptyLine(width)).append("\n");

                    sb.append(TUIFormHelper.formatFieldRow("Requested Loan ($)", loanAmtBuf.toString(), focusedField == 0, 24, 46)).append("\n");
                    sb.append(TUIFormHelper.formatFieldRow("Repayment Term", termMonths + " Months", focusedField == 1, 24, 46)).append("\n");
                    sb.append(TUIFormHelper.formatFieldRow("Monthly Income", incomeBuf.toString(), focusedField == 2, 24, 46)).append("\n");
                    sb.append(TUIFormHelper.formatFieldRow("Monthly Living Exp", expenseBuf.toString(), focusedField == 3, 24, 46)).append("\n");
                    sb.append(TUIFormHelper.formatFieldRow("Existing Debt Service", debtBuf.toString(), focusedField == 4, 24, 46)).append("\n");
                    sb.append(TUIFormHelper.formatFieldRow("Credit Score (300-850)", scoreBuf.toString(), focusedField == 5, 24, 46)).append("\n");

                    String accDisplay = String.format("%s (%s - %s)",
                            disbursementAccount.getAccountNumber(),
                            disbursementAccount.getAccountType(),
                            disbursementAccount.getCurrency());
                    sb.append(TUIFormHelper.formatFieldRow("Disbursement Account", accDisplay, focusedField == 6, 24, 46)).append("\n");
                    sb.append(TUIBox.emptyLine(width)).append("\n");

                    sb.append(TUIBox.divider(width)).append("\n");
                    sb.append(TUIBox.line("UNDERWRITING ASSESSMENT PREVIEW", width)).append("\n");
                    sb.append(TUIBox.emptyLine(width)).append("\n");

                    String aprRow = String.format("  Estimated APR Tier   : %s", aprTierLabel);
                    String dtiStatus = dti.compareTo(new BigDecimal("50.00")) <= 0 ? "(ACCEPTABLE - Max 50.00%)" : "(HIGH RISK - Exceeds 50.00%)";
                    String dtiRow = String.format("  Debt-to-Income (DTI) : %.2f%% %s", dti.doubleValue(), dtiStatus);
                    String monRow = String.format("  Est. Monthly Payment : $ %s USD / Month", df.format(monthlyPayment));
                    String riskRow = String.format("  Credit Risk Rating   : %s", riskRating);

                    sb.append(TUIBox.line(aprRow, width)).append("\n");
                    sb.append(TUIBox.line(dtiRow, width)).append("\n");
                    sb.append(TUIBox.line(monRow, width)).append("\n");
                    sb.append(TUIBox.line(riskRow, width)).append("\n");

                    sb.append(TUIBox.divider(width)).append("\n");
                    sb.append(TUIBox.line("ACTION", width)).append("\n");
                    sb.append(TUIBox.emptyLine(width)).append("\n");

                    String a1 = "[1] Review & Confirm Application";
                    String a2 = "[2] Cancel & Return";
                    String act1 = (focusedField == 7 && actionIndex == 0) ? "▸ " + ConsoleTheme.highlight(a1) : "  " + a1;
                    String act2 = (focusedField == 7 && actionIndex == 1) ? "▸ " + ConsoleTheme.highlight(a2) : "  " + a2;
                    sb.append(TUIBox.line("  " + act1 + "              " + act2, width)).append("\n");

                    sb.append(TUIBox.divider(width)).append("\n");
                    String statusLine = isErrorStatus ? ConsoleTheme.error(statusMessage) : statusMessage;
                    sb.append(TUIBox.line("Status: " + statusLine, width)).append("\n");
                    sb.append(TUIBox.bottom(width)).append("\n");
                    sb.append(ConsoleTheme.keyGuide("[Tab/↓] Next Field  •  [Enter] Action/Select  •  [1/2] Quick Action  •  [Esc] Back")).append("\n");

                    ScreenRenderer.render(sb.toString(), firstRender);
                    firstRender = false;

                    KeyEvent event = TUIFormHelper.readKey(reader);
                    if (event.action() == KeyAction.ESCAPE) {
                        terminal.setAttributes(origAttributes);
                        navigator.pop();
                        return;
                    } else if (event.action() == KeyAction.TAB || event.action() == KeyAction.DOWN) {
                        focusedField = (focusedField + 1) % 8;
                    } else if (event.action() == KeyAction.SHIFT_TAB || event.action() == KeyAction.UP) {
                        focusedField = (focusedField - 1 + 8) % 8;
                    } else if (focusedField == 1 && (event.action() == KeyAction.LEFT || event.action() == KeyAction.RIGHT || (event.action() == KeyAction.CHAR && event.ch() == ' '))) {
                        termIdx = (termIdx + 1) % AVAILABLE_TERMS.length;
                    } else if (focusedField == 7 && (event.action() == KeyAction.LEFT || event.action() == KeyAction.RIGHT)) {
                        actionIndex = (actionIndex == 0) ? 1 : 0;
                    } else if (event.action() == KeyAction.BACKSPACE) {
                        deleteLastChar(focusedField, loanAmtBuf, incomeBuf, expenseBuf, debtBuf, scoreBuf);
                    } else if (event.action() == KeyAction.ENTER) {
                        if (focusedField == 6) {
                            AccountDTO chosen = AccountSelectorModal.showModal(
                                    terminal, origAttributes, reader, accounts, disbursementAccount, width,
                                    "DIGIBANK CORE > LOAN MANAGEMENT > SELECT DISBURSEMENT ACCOUNT",
                                    "AVAILABLE DESTINATION ACCOUNTS",
                                    "Bal: ",
                                    "Tip: Approved loan principal will credit directly into this account.",
                                    " [↑/↓] Navigate  •  [Enter] Confirm  •  [%s] Hotkey  •  [Esc] Back"
                            );
                            if (chosen != null) {
                                disbursementAccount = chosen;
                                focusedField = 7;
                            }
                            firstRender = true;
                        } else if (focusedField == 7) {
                            if (actionIndex == 0) {
                                state = ViewState.CONTRACT_CONFIRMATION;
                                confirmActionIdx = 0;
                                firstRender = true;
                            } else {
                                terminal.setAttributes(origAttributes);
                                navigator.pop();
                                return;
                            }
                        } else {
                            focusedField = (focusedField + 1) % 8;
                        }
                    } else if (event.action() == KeyAction.DIGIT || event.action() == KeyAction.CHAR) {
                        char c = event.ch();
                        if (focusedField == 7) {
                            if (c == '1') {
                                state = ViewState.CONTRACT_CONFIRMATION;
                                confirmActionIdx = 0;
                                firstRender = true;
                            } else if (c == '2') {
                                terminal.setAttributes(origAttributes);
                                navigator.pop();
                                return;
                            }
                        } else {
                            appendChar(focusedField, c, loanAmtBuf, incomeBuf, expenseBuf, debtBuf, scoreBuf);
                        }
                    }

                } else if (state == ViewState.CONTRACT_CONFIRMATION) {
                    BigDecimal totalRepayable = monthlyPayment.multiply(BigDecimal.valueOf(termMonths)).setScale(2, RoundingMode.HALF_UP);
                    BigDecimal totalInterest = totalRepayable.subtract(requestedAmount).max(BigDecimal.ZERO);
                    LocalDate firstDueDate = LocalDate.now().plusMonths(1);

                    StringBuilder sb = new StringBuilder();
                    sb.append(TUIBox.top(width)).append("\n");
                    sb.append(TUIBox.line(ConsoleTheme.primary("DIGIBANK CORE > LOAN MANAGEMENT > CONTRACT CONFIRMATION"), width)).append("\n");
                    sb.append(TUIBox.divider(width)).append("\n");
                    sb.append(TUIBox.line("FACILITY OFFER & DISBURSEMENT SUMMARY", width)).append("\n");
                    sb.append(TUIBox.emptyLine(width)).append("\n");

                    sb.append(TUIBox.line(String.format("  Facility Type        : Personal Credit Facility (#LN-NEW)"), width)).append("\n");
                    sb.append(TUIBox.line(String.format("  Principal Disbursed  : $ %10s USD", df.format(requestedAmount)), width)).append("\n");
                    sb.append(TUIBox.line(String.format("  Interest Rate (APR)  : %.2f%% Fixed Annual Rate", aprRate.doubleValue()), width)).append("\n");
                    sb.append(TUIBox.line(String.format("  Tenor & Frequency    : %d Months (Monthly Amortization)", termMonths), width)).append("\n");
                    sb.append(TUIBox.emptyLine(width)).append("\n");

                    sb.append(TUIBox.line(String.format("  Total Interest Due   : $ %10s USD", df.format(totalInterest)), width)).append("\n");
                    sb.append(TUIBox.line(String.format("  Total Repayable      : $ %10s USD", df.format(totalRepayable)), width)).append("\n");
                    sb.append(TUIBox.line(String.format("  Fixed Monthly Due    : $ %10s USD / Month", df.format(monthlyPayment)), width)).append("\n");
                    sb.append(TUIBox.emptyLine(width)).append("\n");

                    String destDisplay = String.format("%s (%s - %s)",
                            disbursementAccount.getAccountNumber(),
                            disbursementAccount.getAccountType(),
                            disbursementAccount.getCurrency());
                    sb.append(TUIBox.line(String.format("  Credit Destination   : %s", destDisplay), width)).append("\n");
                    sb.append(TUIBox.line(String.format("  First Due Date       : %s", firstDueDate.format(dfDate)), width)).append("\n");
                    sb.append(TUIBox.emptyLine(width)).append("\n");

                    sb.append(TUIBox.divider(width)).append("\n");
                    sb.append(TUIBox.line("CONFIRMATION", width)).append("\n");
                    sb.append(TUIBox.emptyLine(width)).append("\n");

                    String c1 = "[1] Accept Terms & Disburse Funds";
                    String c2 = "[2] Back to Edit Form";
                    String conf1 = (confirmActionIdx == 0) ? "▸ " + ConsoleTheme.highlight(c1) : "  " + c1;
                    String conf2 = (confirmActionIdx == 1) ? "▸ " + ConsoleTheme.highlight(c2) : "  " + c2;
                    sb.append(TUIBox.line("  " + conf1 + "             " + conf2, width)).append("\n");

                    sb.append(TUIBox.divider(width)).append("\n");
                    sb.append(TUIBox.line("Status: Review schedule terms. Funds credit instantly upon authorization.", width)).append("\n");
                    sb.append(TUIBox.bottom(width)).append("\n");
                    sb.append(ConsoleTheme.keyGuide("[Enter] Execute Agreement  •  [1/2] Quick Action  •  [Esc] Cancel & Return")).append("\n");

                    ScreenRenderer.render(sb.toString(), firstRender);
                    firstRender = false;

                    KeyEvent event = TUIFormHelper.readKey(reader);
                    if (event.action() == KeyAction.ESCAPE) {
                        state = ViewState.FORM;
                        firstRender = true;
                    } else if (event.action() == KeyAction.LEFT || event.action() == KeyAction.RIGHT || event.action() == KeyAction.TAB) {
                        confirmActionIdx = (confirmActionIdx == 0) ? 1 : 0;
                    } else if (event.action() == KeyAction.ENTER) {
                        if (confirmActionIdx == 0) {
                            // Execute disbursement transaction
                            boolean success = executeDisbursement(
                                    userEntity, requestedAmount, termMonths, aprRate,
                                    totalRepayable, monthlyPayment, disbursementAccount, firstDueDate);
                            terminal.setAttributes(origAttributes);
                            if (success) {
                                navigator.pop();
                            }
                            return;
                        } else {
                            state = ViewState.FORM;
                            firstRender = true;
                        }
                    } else if (event.action() == KeyAction.DIGIT) {
                        if (event.ch() == '1') {
                            boolean success = executeDisbursement(
                                    userEntity, requestedAmount, termMonths, aprRate,
                                    totalRepayable, monthlyPayment, disbursementAccount, firstDueDate);
                            terminal.setAttributes(origAttributes);
                            if (success) {
                                navigator.pop();
                            }
                            return;
                        } else if (event.ch() == '2') {
                            state = ViewState.FORM;
                            firstRender = true;
                        }
                    }
                }
            }
        } catch (Exception e) {
            logger.error("Error in ApplyLoanScreen", e);
        } finally {
            terminal.setAttributes(origAttributes);
        }
    }

    private boolean executeDisbursement(User user, BigDecimal principal, int termMonths,
                                        BigDecimal apr, BigDecimal totalRepayable,
                                        BigDecimal monthlyPayment, AccountDTO destAccount,
                                        LocalDate firstDueDate) {
        Connection conn = null;
        try {
            conn = DatabaseConnection.getConnection();
            conn.setAutoCommit(false);

            // 1. Lock Account and credit funds
            Account acc = ControllerFactory.getAccountRepository().findByIdForUpdate(conn, destAccount.getAccountId())
                    .orElseThrow(() -> new IllegalStateException("Disbursement account not found"));
            BigDecimal newBalance = acc.getBalance().add(principal);
            acc.setBalance(newBalance);
            ControllerFactory.getAccountRepository().updateWithConnection(conn, acc);

            // 2. Insert Loan
            Loan loan = Loan.builder()
                    .userId(user.getUserId())
                    .accountId(acc.getAccountId())
                    .requestedAmount(principal)
                    .approvedAmount(principal)
                    .interestRate(apr)
                    .termMonths(termMonths)
                    .monthlyIncome(new BigDecimal("2000.00"))
                    .monthlyExpense(new BigDecimal("500.00"))
                    .existingDebt(new BigDecimal("300.00"))
                    .creditScore(600)
                    .riskScore(new BigDecimal("25.00"))
                    .riskLevel(RiskLevel.LOW)
                    .status(LoanStatus.ACTIVE)
                    .outstandingBalance(totalRepayable)
                    .build();
            Loan savedLoan = ControllerFactory.getLoanRepository().saveWithConnection(conn, loan);

            // 3. Insert Transaction
            Transaction tx = Transaction.builder()
                    .accountId(acc.getAccountId())
                    .transactionType(TransactionType.LOAN_DISBURSEMENT)
                    .amount(principal)
                    .currency(acc.getCurrency())
                    .description(String.format("Loan Disbursement (#LN-%d)", savedLoan.getLoanId()))
                    .status(TransactionStatus.COMPLETED)
                    .createdAt(LocalDateTime.now())
                    .build();
            ControllerFactory.getTransactionRepository().saveWithConnection(conn, tx);

            // 4. Generate amortized loan payments
            BigDecimal principalPerMonth = principal.divide(BigDecimal.valueOf(termMonths), 2, RoundingMode.HALF_UP);
            BigDecimal interestPerMonth = monthlyPayment.subtract(principalPerMonth).max(BigDecimal.ZERO);

            for (int i = 0; i < termMonths; i++) {
                LoanPayment lp = LoanPayment.builder()
                        .loanId(savedLoan.getLoanId())
                        .accountId(acc.getAccountId())
                        .amount(monthlyPayment)
                        .principalAmount(principalPerMonth)
                        .interestAmount(interestPerMonth)
                        .dueDate(firstDueDate.plusMonths(i))
                        .status(LoanPaymentStatus.SCHEDULED)
                        .paymentMethod("AUTO_DEBIT")
                        .createdAt(LocalDateTime.now())
                        .build();
                ControllerFactory.getLoanPaymentRepository().saveWithConnection(conn, lp);
            }

            conn.commit();
            return true;
        } catch (Exception e) {
            logger.error("Failed to disburse loan", e);
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

    public static BigDecimal calculateAPR(int creditScore) {
        if (creditScore >= 720) return new BigDecimal("6.50");
        if (creditScore >= 600) return new BigDecimal("8.50");
        return new BigDecimal("12.00");
    }

    public static String getAPRTierLabel(int creditScore, BigDecimal apr) {
        if (creditScore >= 720) return String.format("TIER 1 (PRIME) ── %.2f%% p.a.", apr.doubleValue());
        if (creditScore >= 600) return String.format("TIER 2 (STANDARD) ── %.2f%% p.a.", apr.doubleValue());
        return String.format("TIER 3 (SUBPRIME) ── %.2f%% p.a.", apr.doubleValue());
    }

    public static BigDecimal calculateMonthlyPayment(BigDecimal principal, BigDecimal apr, int termMonths) {
        if (principal == null || principal.compareTo(BigDecimal.ZERO) <= 0 || termMonths <= 0) {
            return BigDecimal.ZERO;
        }
        double p = principal.doubleValue();
        double r = (apr.doubleValue() / 100.0) / 12.0;
        if (r == 0) {
            return principal.divide(BigDecimal.valueOf(termMonths), 2, RoundingMode.HALF_UP);
        }
        double m = p * (r * Math.pow(1.0 + r, termMonths)) / (Math.pow(1.0 + r, termMonths) - 1.0);
        return BigDecimal.valueOf(m).setScale(2, RoundingMode.HALF_UP);
    }

    public static BigDecimal calculateDTI(BigDecimal monthlyIncome, BigDecimal existingDebt, BigDecimal monthlyPayment) {
        if (monthlyIncome == null || monthlyIncome.compareTo(BigDecimal.ZERO) <= 0) {
            return new BigDecimal("100.00");
        }
        BigDecimal totalDebtService = (existingDebt != null ? existingDebt : BigDecimal.ZERO).add(monthlyPayment);
        return totalDebtService.divide(monthlyIncome, 4, RoundingMode.HALF_UP)
                .multiply(new BigDecimal("100"))
                .setScale(2, RoundingMode.HALF_UP);
    }

    public static String calculateRiskRating(int creditScore, BigDecimal dti) {
        if (dti.compareTo(new BigDecimal("40.00")) <= 0 && creditScore >= 700) {
            return "LOW RISK";
        }
        if (dti.compareTo(new BigDecimal("50.00")) <= 0 && creditScore >= 600) {
            return "MODERATE RISK";
        }
        return "HIGH RISK";
    }

    private static BigDecimal parseDecimal(String s, BigDecimal def) {
        try {
            return new BigDecimal(s.replace(",", "").replace("$", "").trim());
        } catch (Exception e) {
            return def;
        }
    }

    private static int parseInteger(String s, int def) {
        try {
            return Integer.parseInt(s.trim());
        } catch (Exception e) {
            return def;
        }
    }

    private static void deleteLastChar(int field, StringBuilder loan, StringBuilder inc, StringBuilder exp, StringBuilder debt, StringBuilder score) {
        StringBuilder b = switch (field) {
            case 0 -> loan;
            case 2 -> inc;
            case 3 -> exp;
            case 4 -> debt;
            case 5 -> score;
            default -> null;
        };
        if (b != null && b.length() > 0) {
            b.deleteCharAt(b.length() - 1);
        }
    }

    private static void appendChar(int field, char c, StringBuilder loan, StringBuilder inc, StringBuilder exp, StringBuilder debt, StringBuilder score) {
        StringBuilder b = switch (field) {
            case 0 -> loan;
            case 2 -> inc;
            case 3 -> exp;
            case 4 -> debt;
            case 5 -> score;
            default -> null;
        };
        if (b != null) {
            if ((c >= '0' && c <= '9') || (c == '.' && !b.toString().contains("."))) {
                if (b.length() < 12) b.append(c);
            }
        }
    }
}
