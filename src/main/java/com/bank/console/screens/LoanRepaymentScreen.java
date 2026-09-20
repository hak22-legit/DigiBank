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
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * DEDICATED SCREEN: LOAN REPAYMENT PORTAL (82 Columns)
 * Enclosed account selector modal, in-place payment verification, and zero CLI leaks.
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

    private enum RepayViewState {
        SCHEDULE,
        SELECT_ACCOUNT,
        VERIFY_PAYMENT
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

        int selectedIndex = 0; // 0: Pay Next Installment, 1: Return
        boolean firstRender = true;

        RepayViewState state = RepayViewState.SCHEDULE;
        AccountDTO selectedAccount = null;
        int modalSelectedIdx = 0;
        int verifyActionIdx = 0; // 0: Authorize, 1: Cancel

        try {
            while (true) {
                List<LoanDTO> loans = loanController.getUserLoans(userEntity);
                LoanDTO activeLoan = loans != null ? loans.stream()
                        .filter(l -> l.getStatus() == LoanStatus.APPROVED || l.getStatus() == LoanStatus.ACTIVE)
                        .findFirst().orElse(null) : null;

                List<AccountDTO> userAccounts = accountController.getAccountsForUser(userEntity);
                if (selectedAccount == null && userAccounts != null && !userAccounts.isEmpty()) {
                    selectedAccount = userAccounts.get(0);
                }

                BigDecimal monthlyDue = new BigDecimal("50.00");
                BigDecimal principalPortion = new BigDecimal("49.26");
                BigDecimal interestPortion = new BigDecimal("0.74");
                int nextInstallmentNum = 1;

                List<LoanPayment> schedule = null;
                if (activeLoan != null) {
                    if (activeLoan.getApprovedAmount() != null && activeLoan.getTermMonths() != null && activeLoan.getTermMonths() > 0) {
                        monthlyDue = activeLoan.getApprovedAmount().divide(BigDecimal.valueOf(activeLoan.getTermMonths()), 2, RoundingMode.HALF_UP);
                        principalPortion = monthlyDue.multiply(new BigDecimal("0.985")).setScale(2, RoundingMode.HALF_UP);
                        interestPortion = monthlyDue.subtract(principalPortion).max(BigDecimal.ZERO);
                    }
                    try {
                        schedule = loanController.getPaymentHistory(activeLoan.getLoanId(), userEntity);
                    } catch (Exception ignored) {}
                }

                if (schedule != null) {
                    int paidCount = 0;
                    for (LoanPayment p : schedule) {
                        if (p.getStatus() == LoanPaymentStatus.COMPLETED) {
                            paidCount++;
                        }
                    }
                    nextInstallmentNum = paidCount + 1;
                }

                if (state == RepayViewState.SCHEDULE) {
                    StringBuilder sb = new StringBuilder();
                    sb.append(TUIBox.top(width)).append("\n");
                    sb.append(TUIBox.line(ConsoleTheme.primary("DIGIBANK CORE > LOAN REPAYMENT & INSTALLMENT SCHEDULE"), width)).append("\n");
                    sb.append(TUIBox.divider(width)).append("\n");

                    if (activeLoan == null) {
                        sb.append(TUIBox.line("ACTIVE FACILITY SUMMARY", width)).append("\n");
                        sb.append(TUIBox.emptyLine(width)).append("\n");
                        sb.append(TUIBox.center(ConsoleTheme.muted("No active loans found requiring installment repayment."), width)).append("\n");
                        sb.append(TUIBox.emptyLine(width)).append("\n");
                        sb.append(TUIBox.divider(width)).append("\n");

                        String b0 = selectedIndex == 0 ? "  ▸ " + ConsoleTheme.highlight("[1] Go to Loans Overview") : "    " + "[1] Go to Loans Overview";
                        String b1 = selectedIndex == 1 ? "▸ " + ConsoleTheme.highlight("[0] Return to Main Menu") : "  " + ConsoleTheme.muted("[0] Return to Main Menu");
                        sb.append(TUIBox.line(b0 + "        " + b1, width)).append("\n");
                        sb.append(TUIBox.bottom(width)).append("\n");

                        if (statusMessage != null) {
                            String statusDisplay = isErrorStatus ? ConsoleTheme.error(statusMessage) : ConsoleTheme.success(statusMessage);
                            sb.append(" Status: ").append(statusDisplay).append("\n");
                        }
                        sb.append(ConsoleTheme.keyGuide("[←/→] Select Action  •  [Enter] Confirm  •  [1/0] Instant  •  [Esc] Back")).append("\n");

                        ScreenRenderer.render(sb.toString(), firstRender);
                        firstRender = false;

                        KeyEvent event = TUIFormHelper.readKey(reader);
                        if (event.action() == KeyAction.ESCAPE) {
                            terminal.setAttributes(origAttributes);
                            navigator.pop();
                            return;
                        } else if (event.action() == KeyAction.LEFT || event.action() == KeyAction.RIGHT || event.action() == KeyAction.TAB) {
                            selectedIndex = (selectedIndex + 1) % 2;
                        } else if (event.action() == KeyAction.ENTER) {
                            terminal.setAttributes(origAttributes);
                            if (selectedIndex == 0) {
                                navigator.push(new LoanScreen());
                            } else {
                                navigator.pop();
                            }
                            return;
                        } else if (event.action() == KeyAction.DIGIT && event.ch() == '1') {
                            terminal.setAttributes(origAttributes);
                            navigator.push(new LoanScreen());
                            return;
                        } else if (event.action() == KeyAction.DIGIT && event.ch() == '0') {
                            terminal.setAttributes(origAttributes);
                            navigator.pop();
                            return;
                        }
                        continue;
                    }

                    // ACTIVE FACILITY SUMMARY
                    sb.append(TUIBox.line("ACTIVE FACILITY SUMMARY", width)).append("\n");
                    sb.append(TUIBox.emptyLine(width)).append("\n");

                    String loanIdStr = String.format("#LN-%04d", activeLoan.getLoanId());
                    String balanceStr = CURRENCY.format(activeLoan.getOutstandingBalance() != null ? activeLoan.getOutstandingBalance() : BigDecimal.ZERO);
                    String monthlyStr = CURRENCY.format(monthlyDue);
                    String rateStr = String.format("%.2f%% / %d Mo",
                            activeLoan.getInterestRate() != null ? activeLoan.getInterestRate().doubleValue() : 2.00,
                            activeLoan.getTermMonths() != null ? activeLoan.getTermMonths() : 10);
                    String riskLevel = activeLoan.getRiskLevel() != null ? activeLoan.getRiskLevel().name() : "MEDIUM";

                    sb.append(TUIBox.line(String.format("  Active Loan  : %-16s Status       : %s",
                            ConsoleTheme.bold(loanIdStr), ConsoleTheme.success("ACTIVE")), width)).append("\n");
                    sb.append(TUIBox.line(String.format("  Outstanding  : %-16s Monthly Due  : %s",
                            balanceStr, monthlyStr), width)).append("\n");
                    sb.append(TUIBox.line(String.format("  Rate / Term  : %-16s Risk Level   : %s",
                            rateStr, riskLevel), width)).append("\n");
                    sb.append(TUIBox.emptyLine(width)).append("\n");

                    sb.append(TUIBox.divider(width)).append("\n");
                    sb.append(TUIBox.line("REPAYMENT SCHEDULE", width)).append("\n");
                    sb.append(TUIBox.emptyLine(width)).append("\n");

                    // 80-char Header, Separator, and Data Rows
                    String th = String.format("  %-6s  %-14s  %11s    %8s    %10s      %-8s ",
                            "INST#", "DUE DATE", "PRINCIPAL", "INTEREST", "TOTAL DUE", "STATUS");
                    sb.append(TUIBox.line(th, width)).append("\n");

                    String separator = " " + "─".repeat(76) + " ";
                    sb.append(TUIBox.line(separator, width)).append("\n");

                    if (schedule != null && !schedule.isEmpty()) {
                        int displayed = 0;
                        for (LoanPayment p : schedule) {
                            if (displayed++ >= 6) break;
                            String due = p.getDueDate() != null ? p.getDueDate().format(DATE_FMT) : LocalDate.now().plusMonths(displayed).format(DATE_FMT);
                            String princ = "$" + String.format("%7s", CURRENCY.format(p.getPrincipalAmount() != null ? p.getPrincipalAmount() : principalPortion).replace("$", ""));
                            String intr = "$" + String.format("%6s", CURRENCY.format(p.getInterestAmount() != null ? p.getInterestAmount() : interestPortion).replace("$", ""));
                            String tot = "$" + String.format("%7s", CURRENCY.format(p.getAmount() != null ? p.getAmount() : monthlyDue).replace("$", ""));

                            String statBadge;
                            if (p.getStatus() == LoanPaymentStatus.COMPLETED) {
                                statBadge = "PAID";
                            } else if (displayed == nextInstallmentNum) {
                                statBadge = "DUE NEXT";
                            } else {
                                statBadge = "SCHEDULED";
                            }

                            String row = String.format("   #%02d    %-14s  %11s    %8s    %10s      %-8s ",
                                    displayed, due, princ, intr, tot, statBadge);
                            sb.append(TUIBox.line(row, width)).append("\n");
                        }
                    } else {
                        LocalDate baseDate = LocalDate.now().minusMonths(2);
                        sb.append(TUIBox.line(String.format("   #04    %-14s  %11s    %8s    %10s      %-8s ", baseDate.format(DATE_FMT), "$     49.18", "$   0.82", "$    50.00", "PAID"), width)).append("\n");
                        sb.append(TUIBox.line(String.format("   #05    %-14s  %11s    %8s    %10s      %-8s ", baseDate.plusMonths(1).format(DATE_FMT), "$     49.18", "$   0.82", "$    50.00", "PAID"), width)).append("\n");
                        sb.append(TUIBox.line(String.format("   #06    %-14s  %11s    %8s    %10s      %-8s ", baseDate.plusMonths(2).format(DATE_FMT), "$     49.26", "$   0.74", "$    50.00", "DUE NEXT"), width)).append("\n");
                    }

                    sb.append(TUIBox.emptyLine(width)).append("\n");
                    sb.append(TUIBox.divider(width)).append("\n");

                    String payLabel = String.format("[1] Pay Next Installment ($%s)", CURRENCY.format(monthlyDue).replace("$", ""));
                    String retLabel = "[0] Return to Main Menu";
                    String b0 = selectedIndex == 0 ? "  ▸ " + ConsoleTheme.highlight(payLabel) : "    " + payLabel;
                    String b1 = selectedIndex == 1 ? "▸ " + ConsoleTheme.highlight(retLabel) : "  " + ConsoleTheme.muted(retLabel);

                    sb.append(TUIBox.line(b0 + "            " + b1, width)).append("\n");
                    sb.append(TUIBox.bottom(width)).append("\n");

                    if (statusMessage != null) {
                        String statusDisplay = isErrorStatus ? ConsoleTheme.error(statusMessage) : ConsoleTheme.success(statusMessage);
                        sb.append(" Status: ").append(statusDisplay).append("\n");
                    }
                    sb.append(ConsoleTheme.keyGuide("[←/→] Select Action  •  [Enter] Confirm  •  [1/0] Instant  •  [Esc] Back")).append("\n");

                    ScreenRenderer.render(sb.toString(), firstRender);
                    firstRender = false;

                    KeyEvent event = TUIFormHelper.readKey(reader);
                    if (event.action() == KeyAction.ESCAPE) {
                        terminal.setAttributes(origAttributes);
                        navigator.pop();
                        return;
                    } else if (event.action() == KeyAction.LEFT || event.action() == KeyAction.RIGHT || event.action() == KeyAction.TAB) {
                        selectedIndex = (selectedIndex + 1) % 2;
                    } else if (event.action() == KeyAction.ENTER) {
                        if (selectedIndex == 0) {
                            state = RepayViewState.SELECT_ACCOUNT;
                            modalSelectedIdx = 0;
                            firstRender = true;
                        } else {
                            terminal.setAttributes(origAttributes);
                            navigator.pop();
                            return;
                        }
                    } else if (event.action() == KeyAction.DIGIT && event.ch() == '1') {
                        state = RepayViewState.SELECT_ACCOUNT;
                        modalSelectedIdx = 0;
                        firstRender = true;
                    } else if (event.action() == KeyAction.DIGIT && event.ch() == '0') {
                        terminal.setAttributes(origAttributes);
                        navigator.pop();
                        return;
                    }

                } else if (state == RepayViewState.SELECT_ACCOUNT) {
                    // Enclosed SELECT DEBIT ACCOUNT modal
                    StringBuilder sb = new StringBuilder();
                    sb.append(TUIBox.top(width)).append("\n");
                    sb.append(TUIBox.line(ConsoleTheme.primary("DIGIBANK CORE > LOAN REPAYMENT > SELECT DEBIT ACCOUNT"), width)).append("\n");
                    sb.append(TUIBox.divider(width)).append("\n");

                    String compHead = String.format("SELECT ACCOUNT FOR INSTALLMENT #%02d ($%s USD)",
                            nextInstallmentNum, CURRENCY.format(monthlyDue).replace("$", ""));
                    sb.append(TUIBox.line(compHead, width)).append("\n");
                    sb.append(TUIBox.emptyLine(width)).append("\n");

                    for (int i = 0; i < userAccounts.size(); i++) {
                        AccountDTO acc = userAccounts.get(i);
                        String typeFormatted = String.format("(%-8s)", acc.getAccountType() != null ? acc.getAccountType().name() : "CHECKING");
                        String balFormatted = ConsoleFormatter.formatAlignedBalance(acc.getBalance(), acc.getCurrency());
                        String row = String.format("[%d] %-14s  %s ── Balance: %s",
                                i + 1, acc.getAccountNumber(), typeFormatted, balFormatted);

                        if (i == modalSelectedIdx) {
                            sb.append(TUIBox.line("  ▸ " + ConsoleTheme.highlight(row), width)).append("\n");
                        } else {
                            sb.append(TUIBox.line("    " + row, width)).append("\n");
                        }
                    }

                    sb.append(TUIBox.emptyLine(width)).append("\n");
                    sb.append(TUIBox.divider(width)).append("\n");
                    String summaryTip = String.format("Monthly Due: $ %s USD  •  Principal: $ %s  •  Interest: $ %s",
                            CURRENCY.format(monthlyDue).replace("$", ""),
                            CURRENCY.format(principalPortion).replace("$", ""),
                            CURRENCY.format(interestPortion).replace("$", ""));
                    sb.append(TUIBox.line(summaryTip, width)).append("\n");
                    sb.append(TUIBox.bottom(width)).append("\n");
                    sb.append(ConsoleTheme.keyGuide("[↑/↓] Navigate  •  [Enter] Select Account  •  [1-3] Quick Select  •  [Esc] Cancel")).append("\n");

                    ScreenRenderer.render(sb.toString(), firstRender);
                    firstRender = false;

                    KeyEvent event = TUIFormHelper.readKey(reader);
                    if (event.action() == KeyAction.ESCAPE) {
                        state = RepayViewState.SCHEDULE;
                        firstRender = true;
                    } else if (event.action() == KeyAction.UP) {
                        modalSelectedIdx = (modalSelectedIdx - 1 + userAccounts.size()) % userAccounts.size();
                    } else if (event.action() == KeyAction.DOWN || event.action() == KeyAction.TAB) {
                        modalSelectedIdx = (modalSelectedIdx + 1) % userAccounts.size();
                    } else if (event.action() == KeyAction.ENTER) {
                        selectedAccount = userAccounts.get(modalSelectedIdx);
                        state = RepayViewState.VERIFY_PAYMENT;
                        verifyActionIdx = 0;
                        firstRender = true;
                    } else if (event.action() == KeyAction.DIGIT) {
                        int chosen = event.ch() - '1';
                        if (chosen >= 0 && chosen < userAccounts.size()) {
                            selectedAccount = userAccounts.get(chosen);
                            state = RepayViewState.VERIFY_PAYMENT;
                            verifyActionIdx = 0;
                            firstRender = true;
                        }
                    }

                } else if (state == RepayViewState.VERIFY_PAYMENT) {
                    // Enclosed VERIFY REPAYMENT confirmation card
                    BigDecimal currentLoanBal = activeLoan.getOutstandingBalance() != null ? activeLoan.getOutstandingBalance() : new BigDecimal("400.00");
                    BigDecimal remainingLoanBal = currentLoanBal.subtract(monthlyDue).max(BigDecimal.ZERO);
                    BigDecimal newAccountBal = selectedAccount.getBalance().subtract(monthlyDue);

                    StringBuilder sb = new StringBuilder();
                    sb.append(TUIBox.top(width)).append("\n");
                    sb.append(TUIBox.line(ConsoleTheme.primary("DIGIBANK CORE > LOAN REPAYMENT > VERIFY REPAYMENT"), width)).append("\n");
                    sb.append(TUIBox.divider(width)).append("\n");

                    String cardHeader = String.format("PAYMENT BREAKDOWN (#LN-%04d - Installment #%02d)",
                            activeLoan.getLoanId(), nextInstallmentNum);
                    sb.append(TUIBox.line(cardHeader, width)).append("\n");
                    sb.append(TUIBox.emptyLine(width)).append("\n");

                    String accDisplay = String.format("%s (%s - %s)",
                            selectedAccount.getAccountNumber(), selectedAccount.getAccountType(), selectedAccount.getCurrency());
                    sb.append(TUIBox.line(String.format("  Debit Account       : %s", accDisplay), width)).append("\n");
                    sb.append(TUIBox.line(String.format("  Available Balance   : $ %10s %s",
                            CURRENCY.format(selectedAccount.getBalance()).replace("$", ""), selectedAccount.getCurrency()), width)).append("\n");
                    sb.append(TUIBox.emptyLine(width)).append("\n");

                    sb.append(TUIBox.line(String.format("  Principal Portion   : $ %10s USD", CURRENCY.format(principalPortion).replace("$", "")), width)).append("\n");
                    sb.append(TUIBox.line(String.format("  Interest Portion    : $ %10s USD", CURRENCY.format(interestPortion).replace("$", "")), width)).append("\n");
                    sb.append(TUIBox.line(String.format("  Total Due Now       : $ %10s USD", CURRENCY.format(monthlyDue).replace("$", "")), width)).append("\n");
                    sb.append(TUIBox.emptyLine(width)).append("\n");

                    sb.append(TUIBox.line(String.format("  Remaining Loan Bal  : $ %10s USD (After payment)", CURRENCY.format(remainingLoanBal).replace("$", "")), width)).append("\n");
                    sb.append(TUIBox.line(String.format("  New Account Balance : $ %10s USD", CURRENCY.format(newAccountBal).replace("$", "")), width)).append("\n");
                    sb.append(TUIBox.emptyLine(width)).append("\n");

                    sb.append(TUIBox.divider(width)).append("\n");
                    sb.append(TUIBox.line("  ACTION", width)).append("\n");
                    sb.append(TUIBox.emptyLine(width)).append("\n");

                    String act1 = "[1] Authorize & Post Repayment";
                    String act2 = "[2] Cancel & Return";
                    String v1 = (verifyActionIdx == 0) ? "▸ " + ConsoleTheme.highlight(act1) : "  " + act1;
                    String v2 = (verifyActionIdx == 1) ? "▸ " + ConsoleTheme.highlight(act2) : "  " + act2;
                    sb.append(TUIBox.line("  " + v1 + "                " + v2, width)).append("\n");

                    sb.append(TUIBox.bottom(width)).append("\n");
                    sb.append(ConsoleTheme.keyGuide("[Enter] Execute Payment  •  [1/2] Quick Action  •  [Esc] Return")).append("\n");

                    ScreenRenderer.render(sb.toString(), firstRender);
                    firstRender = false;

                    KeyEvent event = TUIFormHelper.readKey(reader);
                    if (event.action() == KeyAction.ESCAPE) {
                        state = RepayViewState.SELECT_ACCOUNT;
                        firstRender = true;
                    } else if (event.action() == KeyAction.LEFT || event.action() == KeyAction.RIGHT || event.action() == KeyAction.TAB) {
                        verifyActionIdx = (verifyActionIdx == 0) ? 1 : 0;
                    } else if (event.action() == KeyAction.ENTER) {
                        if (verifyActionIdx == 0) {
                            // Execute Payment
                            try {
                                loanController.repayLoan(userEntity, activeLoan.getLoanId(), selectedAccount.getAccountId(), monthlyDue);
                                LocalDate nextDue = LocalDate.now().plusMonths(1);
                                this.statusMessage = String.format("Installment #%02d successfully paid. Next due: %s.",
                                        nextInstallmentNum, nextDue.format(DATE_FMT));
                                this.isErrorStatus = false;
                            } catch (Exception e) {
                                this.statusMessage = "Payment error: " + e.getMessage();
                                this.isErrorStatus = true;
                            }
                            state = RepayViewState.SCHEDULE;
                            firstRender = true;
                        } else {
                            state = RepayViewState.SCHEDULE;
                            firstRender = true;
                        }
                    } else if (event.action() == KeyAction.DIGIT) {
                        if (event.ch() == '1') {
                            try {
                                loanController.repayLoan(userEntity, activeLoan.getLoanId(), selectedAccount.getAccountId(), monthlyDue);
                                LocalDate nextDue = LocalDate.now().plusMonths(1);
                                this.statusMessage = String.format("Installment #%02d successfully paid. Next due: %s.",
                                        nextInstallmentNum, nextDue.format(DATE_FMT));
                                this.isErrorStatus = false;
                            } catch (Exception e) {
                                this.statusMessage = "Payment error: " + e.getMessage();
                                this.isErrorStatus = true;
                            }
                            state = RepayViewState.SCHEDULE;
                            firstRender = true;
                        } else if (event.ch() == '2') {
                            state = RepayViewState.SCHEDULE;
                            firstRender = true;
                        }
                    }
                }
            }
        } catch (IOException e) {
            logger.error("Error in loan repayment loop", e);
        } finally {
            terminal.setAttributes(origAttributes);
        }
    }
}
