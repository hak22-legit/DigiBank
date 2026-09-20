package com.bank.console.screens;

import com.bank.console.ControllerFactory;
import com.bank.console.ScreenNavigator;
import com.bank.console.TUISession;
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
import com.bank.model.entity.Admin;
import com.bank.model.entity.Loan;
import com.bank.security.SessionManager;
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
 * LOAN OFFICER > CUSTOMER BORROWING HISTORY & PROFILES (82 Columns)
 * Search and review customer loan history, repayment performance, and credit metrics.
 */
public class BorrowingHistoryScreen implements Screen {
    private static final Logger logger = LoggerFactory.getLogger(BorrowingHistoryScreen.class);

    private final AdminController adminController;
    private final LoanController loanController;

    public BorrowingHistoryScreen() {
        this(ControllerFactory.getAdminController(), ControllerFactory.getLoanController());
    }

    public BorrowingHistoryScreen(AdminController adminController, LoanController loanController) {
        this.adminController = adminController;
        this.loanController = loanController;
    }

    @Override
    public void render(ScreenNavigator navigator, TUISession session) {
        Admin admin = SessionManager.getCurrentAdmin();
        if (admin == null) {
            navigator.pop();
            return;
        }

        int width = TUILayout.APP_WIDTH;
        Terminal terminal = session.getTerminal();
        Attributes origAttributes = terminal.enterRawMode();
        NonBlockingReader reader = terminal.reader();

        StringBuilder idBuffer = new StringBuilder();
        Long searchUserId = null;
        List<Loan> userLoans = Collections.emptyList();
        UserProfileDossier dossier = null;

        String statusMessage = "Type Customer User ID and press [Enter] to inspect credit profile.";
        boolean isError = false;
        boolean firstRender = true;

        try {
            while (true) {
                String rendered = renderContent(idBuffer.toString(), searchUserId, dossier, userLoans, statusMessage, isError, width);
                ScreenRenderer.render(rendered, firstRender);
                firstRender = false;

                KeyEvent event = TUIFormHelper.readKey(reader);
                if (event.action() == KeyAction.ESCAPE) {
                    navigator.pop();
                    return;
                } else if (event.action() == KeyAction.BACKSPACE) {
                    if (!idBuffer.isEmpty()) {
                        idBuffer.deleteCharAt(idBuffer.length() - 1);
                    }
                } else if (event.action() == KeyAction.DIGIT) {
                    if (idBuffer.length() < 10) {
                        idBuffer.append(event.ch());
                    }
                } else if (event.action() == KeyAction.ENTER) {
                    if (!idBuffer.isEmpty()) {
                        try {
                            searchUserId = Long.parseLong(idBuffer.toString().trim());
                            userLoans = loanController.getCustomerBorrowingHistory(admin, searchUserId);
                            try {
                                dossier = adminController.getUserProfileDossier(admin, searchUserId);
                            } catch (Exception e) {
                                dossier = null;
                            }
                            statusMessage = String.format("Found %d loan records for Customer #USR-%02d.", userLoans.size(), searchUserId);
                            isError = false;
                        } catch (Exception e) {
                            statusMessage = "Search error: " + e.getMessage();
                            isError = true;
                            userLoans = Collections.emptyList();
                            dossier = null;
                        }
                    } else {
                        statusMessage = "Please enter a valid numeric Customer ID.";
                        isError = true;
                    }
                }
            }
        } catch (IOException e) {
            logger.error("Error in BorrowingHistoryScreen loop", e);
        } finally {
            terminal.setAttributes(origAttributes);
        }
    }

    public static String renderContent(String searchInput, Long searchUserId, UserProfileDossier dossier,
                                       List<Loan> loans, String statusMessage, boolean isError, int width) {
        StringBuilder sb = new StringBuilder();
        DecimalFormat df = new DecimalFormat("#,##0.00");

        sb.append(TUIBox.top(width)).append("\n");
        sb.append(TUIBox.line(ConsoleTheme.primary("DIGIBANK CORE > LOAN OFFICER > BORROWING HISTORY & PROFILES"), width)).append("\n");
        sb.append(TUIBox.divider(width)).append("\n");

        sb.append(TUIBox.line(" " + ConsoleTheme.bold("CUSTOMER LOOKUP"), width)).append("\n");
        sb.append(TUIBox.emptyLine(width)).append("\n");
        String searchField = String.format("  Search Customer User ID: [ %-46s ]", searchInput + "_");
        sb.append(TUIBox.line(searchField, width)).append("\n");
        sb.append(TUIBox.emptyLine(width)).append("\n");
        sb.append(TUIBox.divider(width)).append("\n");

        if (searchUserId != null && dossier != null) {
            sb.append(TUIBox.line(" " + ConsoleTheme.bold("BORROWER DOSSIER & CREDIT METRICS"), width)).append("\n");
            sb.append(TUIBox.emptyLine(width)).append("\n");
            String r1 = String.format("  Customer Name: %-25s  Status       : %s", dossier.getFullName(), dossier.getStatus());
            String r2 = String.format("  Email Address: %-25s  Phone Number : %s", dossier.getEmail(), dossier.getPhone());
            sb.append(TUIBox.line(r1, width)).append("\n");
            sb.append(TUIBox.line(r2, width)).append("\n");
            sb.append(TUIBox.emptyLine(width)).append("\n");
            sb.append(TUIBox.divider(width)).append("\n");
        }

        sb.append(TUIBox.line(" " + ConsoleTheme.bold("HISTORICAL LOAN FACILITIES & REPAYMENT RECORD"), width)).append("\n");
        sb.append(TUIBox.emptyLine(width)).append("\n");

        String th = String.format("  %-9s %-14s %-10s %-12s %-12s %-11s",
                "LOAN ID", "AMOUNT", "TERM", "RATE (%)", "BALANCE", "STATUS");
        sb.append(TUIBox.line(th, width)).append("\n");
        sb.append(TUIBox.line("  " + "─".repeat(74), width)).append("\n");

        if (loans != null && !loans.isEmpty()) {
            for (Loan l : loans) {
                String lid = String.format("#LN-%04d", l.getLoanId());
                String amt = "$ " + df.format(l.getRequestedAmount() != null ? l.getRequestedAmount() : BigDecimal.ZERO);
                String term = (l.getTermMonths() != null ? l.getTermMonths() : 0) + " Mo";
                String rate = (l.getInterestRate() != null ? df.format(l.getInterestRate()) : "0.00") + "%";
                String bal = "$ " + df.format(l.getOutstandingBalance() != null ? l.getOutstandingBalance() : BigDecimal.ZERO);
                String st = l.getStatus() != null ? l.getStatus().name() : "-";

                String row = String.format("  %-9s %-14s %-10s %-12s %-12s %-11s", lid, amt, term, rate, bal, st);
                if (row.length() > 74) row = row.substring(0, 74);
                sb.append(TUIBox.line(row, width)).append("\n");
            }
        } else {
            String msg = searchUserId == null ? "Enter a customer ID above to view loan portfolio records."
                    : "No credit facilities found for customer #USR-" + searchUserId + ".";
            sb.append(TUIBox.line("  " + ConsoleTheme.muted(msg), width)).append("\n");
        }

        sb.append(TUIBox.emptyLine(width)).append("\n");
        sb.append(TUIBox.divider(width)).append("\n");

        String statusDisplay = isError ? ConsoleTheme.error(statusMessage) : ConsoleTheme.muted(statusMessage);
        sb.append(TUIBox.line("Status: " + statusDisplay, width)).append("\n");
        sb.append(TUIBox.bottom(width)).append("\n");
        sb.append(ConsoleTheme.keyGuide("[Digits] Enter ID  •  [Enter] Search  •  [Backspace] Delete  •  [Esc] Back")).append("\n");

        return sb.toString();
    }
}
