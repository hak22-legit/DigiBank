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
 * LOAN OFFICER > PORTFOLIO PERFORMANCE & ACTIVE LOAN BOOK (82 Columns)
 * Overview of live credit facilities, outstanding asset balance, and risk profile.
 */
public class ActiveLoanBookScreen implements Screen {
    private static final Logger logger = LoggerFactory.getLogger(ActiveLoanBookScreen.class);

    private final AdminController adminController;
    private final LoanController loanController;

    public ActiveLoanBookScreen() {
        this(ControllerFactory.getAdminController(), ControllerFactory.getLoanController());
    }

    public ActiveLoanBookScreen(AdminController adminController, LoanController loanController) {
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

        String statusMessage = "Active loan book synchronized with credit ledger.";
        boolean isError = false;
        boolean firstRender = true;

        List<Loan> activeLoans;
        try {
            activeLoans = loanController.getActiveLoanBook(admin);
        } catch (Exception e) {
            logger.error("Error loading active loan book", e);
            activeLoans = Collections.emptyList();
            statusMessage = "Failed to load active loan book: " + e.getMessage();
            isError = true;
        }

        try {
            while (true) {
                String rendered = renderContent(activeLoans, statusMessage, isError, width);
                ScreenRenderer.render(rendered, firstRender);
                firstRender = false;

                KeyEvent event = TUIFormHelper.readKey(reader);
                if (event.action() == KeyAction.ESCAPE || (event.action() == KeyAction.CHAR && (event.ch() == 'b' || event.ch() == 'B' || event.ch() == '0'))) {
                    navigator.pop();
                    return;
                }
            }
        } catch (IOException e) {
            logger.error("Error in ActiveLoanBookScreen loop", e);
        } finally {
            terminal.setAttributes(origAttributes);
        }
    }

    public static String renderContent(List<Loan> activeLoans, String statusMessage, boolean isError, int width) {
        StringBuilder sb = new StringBuilder();
        DecimalFormat df = new DecimalFormat("#,##0.00");

        sb.append(TUIBox.top(width)).append("\n");
        sb.append(TUIBox.line(ConsoleTheme.primary("DIGIBANK CORE > LOAN OFFICER > PORTFOLIO PERFORMANCE & ACTIVE LOAN BOOK"), width)).append("\n");
        sb.append(TUIBox.divider(width)).append("\n");

        // Section 1: PORTFOLIO PERFORMANCE RADAR
        sb.append(TUIBox.line(" " + ConsoleTheme.bold("ACTIVE PORTFOLIO PERFORMANCE RADAR"), width)).append("\n");
        sb.append(TUIBox.emptyLine(width)).append("\n");

        long activeCount = activeLoans != null ? activeLoans.size() : 0;
        BigDecimal totalOutstanding = BigDecimal.ZERO;
        BigDecimal totalDisbursed = BigDecimal.ZERO;
        if (activeLoans != null) {
            for (Loan l : activeLoans) {
                if (l.getOutstandingBalance() != null) totalOutstanding = totalOutstanding.add(l.getOutstandingBalance());
                if (l.getApprovedAmount() != null) totalDisbursed = totalDisbursed.add(l.getApprovedAmount());
            }
        }

        sb.append(TUIBox.twoColumns(" Active Facilities  : " + (activeCount + (activeCount == 1 ? " Account" : " Accounts")), "Total Book Asset    : $ " + df.format(totalDisbursed) + " USD ", width)).append("\n");
        sb.append(TUIBox.twoColumns(" Outstanding Debt   : $ " + df.format(totalOutstanding), "Portfolio Health    : PRIME PERFORMING ", width)).append("\n");
        sb.append(TUIBox.emptyLine(width)).append("\n");
        sb.append(TUIBox.divider(width)).append("\n");

        // Section 2: ACTIVE LOAN BOOK DIRECTORY
        sb.append(TUIBox.line(" " + ConsoleTheme.bold("ACTIVE LOAN BOOK DIRECTORY"), width)).append("\n");
        sb.append(TUIBox.emptyLine(width)).append("\n");

        String th = String.format("  %-9s %-9s %-14s %-14s %-10s %-12s",
                "LOAN ID", "USER ID", "PRINCIPAL", "OUTSTANDING", "TERM", "RATE (%)");
        sb.append(TUIBox.line(th, width)).append("\n");
        sb.append(TUIBox.line("  " + "─".repeat(74), width)).append("\n");

        if (activeLoans != null && !activeLoans.isEmpty()) {
            for (int i = 0; i < Math.min(8, activeLoans.size()); i++) {
                Loan l = activeLoans.get(i);
                String lid = String.format("#LN-%04d", l.getLoanId());
                String uid = String.format("#USR-%02d", l.getUserId() != null ? l.getUserId() : 0);
                String princ = "$ " + df.format(l.getApprovedAmount() != null ? l.getApprovedAmount() : l.getRequestedAmount());
                String out = "$ " + df.format(l.getOutstandingBalance() != null ? l.getOutstandingBalance() : BigDecimal.ZERO);
                String term = (l.getTermMonths() != null ? l.getTermMonths() : 0) + " Mo";
                String rate = (l.getInterestRate() != null ? df.format(l.getInterestRate()) : "0.00") + "%";

                String row = String.format("  %-9s %-9s %-14s %-14s %-10s %-12s", lid, uid, princ, out, term, rate);
                if (row.length() > 74) row = row.substring(0, 74);
                sb.append(TUIBox.line(row, width)).append("\n");
            }
        } else {
            sb.append(TUIBox.line("  " + ConsoleTheme.muted("No active loans currently in the loan book."), width)).append("\n");
        }

        sb.append(TUIBox.emptyLine(width)).append("\n");
        sb.append(TUIBox.divider(width)).append("\n");

        String statusDisplay = isError ? ConsoleTheme.error(statusMessage) : ConsoleTheme.muted(statusMessage);
        sb.append(TUIBox.line("Status: " + statusDisplay, width)).append("\n");
        sb.append(TUIBox.bottom(width)).append("\n");
        sb.append(ConsoleTheme.keyGuide("[Esc / B] Return to Loan Officer Dashboard")).append("\n");

        return sb.toString();
    }
}
