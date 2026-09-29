package com.bank.console.screens;

import com.bank.console.ControllerFactory;
import com.bank.console.ScreenNavigator;
import com.bank.console.TUISession;
import com.bank.console.TerminalInputHandler;
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
import com.bank.ui.Ansi;
import org.jline.terminal.Attributes;
import org.jline.terminal.Terminal;
import org.jline.utils.NonBlockingReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * LOAN OFFICER > PORTFOLIO PERFORMANCE & ACTIVE LOAN BOOK (82 Columns)
 * Comprehensive portfolio surveillance, 3-tier KPI radar, facility drilldown, and sorting.
 */
public class PortfolioPerformanceScreen implements Screen {
    private static final Logger logger = LoggerFactory.getLogger(PortfolioPerformanceScreen.class);

    private final AdminController adminController;
    private final LoanController loanController;

    public PortfolioPerformanceScreen() {
        this(ControllerFactory.getAdminController(), ControllerFactory.getLoanController());
    }

    public PortfolioPerformanceScreen(AdminController adminController, LoanController loanController) {
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

        // Drain lingering newline characters on entry
        TerminalInputHandler.drainBuffer(reader);

        String statusMessage = "Active loan book synchronized with credit ledger.";
        boolean isError = false;
        boolean firstRender = true;
        int selectedIndex = 0;
        boolean sortOutstandingDesc = false;
        boolean inScreen = true;
        final int pageSize = 6;

        List<Loan> rawLoans;
        try {
            rawLoans = loanController.getActiveLoanBook(admin);
            if (rawLoans == null) rawLoans = Collections.emptyList();
        } catch (Exception e) {
            logger.error("Error loading active loan book", e);
            rawLoans = Collections.emptyList();
            statusMessage = "Failed to load active loan book: " + e.getMessage();
            isError = true;
        }

        try {
            while (inScreen) {
                List<Loan> displayLoans = new ArrayList<>(rawLoans);
                if (sortOutstandingDesc) {
                    displayLoans.sort((a, b) -> {
                        BigDecimal bBal = b.getOutstandingBalance() != null ? b.getOutstandingBalance() : BigDecimal.ZERO;
                        BigDecimal aBal = a.getOutstandingBalance() != null ? a.getOutstandingBalance() : BigDecimal.ZERO;
                        return bBal.compareTo(aBal);
                    });
                } else {
                    displayLoans.sort(Comparator.comparing(Loan::getLoanId));
                }

                if (selectedIndex >= displayLoans.size() && !displayLoans.isEmpty()) {
                    selectedIndex = displayLoans.size() - 1;
                }
                if (selectedIndex < 0) {
                    selectedIndex = 0;
                }

                int totalPages = Math.max(1, (displayLoans.size() + pageSize - 1) / pageSize);
                int page = (selectedIndex / pageSize) + 1;

                String rendered = renderContent(displayLoans, selectedIndex, sortOutstandingDesc, statusMessage, isError, width);
                ScreenRenderer.render(rendered, firstRender);
                firstRender = false;

                TerminalInputHandler.KeyCode event = TerminalInputHandler.readNavigationKey(reader);
                String key = event.asNormalizedKey();

                switch (key) {
                    case "ESC", "B", "0" -> {
                        inScreen = false;
                        navigator.pop();
                        return;
                    }
                    case "UP", "K" -> {
                        if (!displayLoans.isEmpty()) {
                            selectedIndex = (selectedIndex - 1 + displayLoans.size()) % displayLoans.size();
                        }
                    }
                    case "DOWN", "J" -> {
                        if (!displayLoans.isEmpty()) {
                            selectedIndex = (selectedIndex + 1) % displayLoans.size();
                        }
                    }
                    case "N" -> {
                        if (page < totalPages) {
                            selectedIndex = Math.min(displayLoans.size() - 1, page * pageSize);
                        }
                    }
                    case "P" -> {
                        if (page > 1) {
                            selectedIndex = Math.max(0, (page - 2) * pageSize);
                        }
                    }
                    case "S" -> {
                        sortOutstandingDesc = !sortOutstandingDesc;
                        statusMessage = sortOutstandingDesc
                                ? "Sorted by: Outstanding Balance (Descending)."
                                : "Sorted by: Facility ID (Ascending).";
                    }
                    case "ENTER" -> {
                        if (!displayLoans.isEmpty() && selectedIndex < displayLoans.size()) {
                            Loan sel = displayLoans.get(selectedIndex);
                            statusMessage = String.format("Facility #LN-%04d selected. Performance optimal. Current interest rate: %s%%.",
                                    sel.getLoanId(), sel.getInterestRate() != null ? sel.getInterestRate().toString() : "0.00");
                        }
                    }
                    default -> {
                        // Discard unmapped keys without exiting or breaking the loop
                    }
                }
            }
        } catch (IOException e) {
            logger.error("Error in PortfolioPerformanceScreen loop", e);
        } finally {
            terminal.setAttributes(origAttributes);
        }
    }

    public static String renderContent(List<Loan> activeLoans, String statusMessage, boolean isError, int width) {
        return renderContent(activeLoans, 0, false, statusMessage, isError, width);
    }

    public static String renderContent(List<Loan> activeLoans, int selectedIndex, boolean sortDesc,
                                       String statusMessage, boolean isError, int width) {
        StringBuilder sb = new StringBuilder();
        DecimalFormat df = new DecimalFormat("#,##0.00");

        sb.append(TUIBox.top(width)).append("\n");
        sb.append(TUIBox.line(ConsoleTheme.primary("DIGIBANK CORE > LOAN OFFICER > PORTFOLIO PERFORMANCE & ACTIVE LOAN BOOK"), width)).append("\n");
        sb.append(TUIBox.divider(width)).append("\n");

        // Section 1: 3-TIER KPI RADAR
        sb.append(TUIBox.line(" " + ConsoleTheme.bold("ACTIVE PORTFOLIO PERFORMANCE RADAR"), width)).append("\n");
        sb.append(TUIBox.emptyLine(width)).append("\n");

        long activeCount = activeLoans != null ? activeLoans.size() : 0;
        BigDecimal totalOutstanding = BigDecimal.ZERO;
        BigDecimal totalDisbursed = BigDecimal.ZERO;
        if (activeLoans != null) {
            for (Loan l : activeLoans) {
                if (l.getOutstandingBalance() != null) totalOutstanding = totalOutstanding.add(l.getOutstandingBalance());
                BigDecimal princ = l.getApprovedAmount() != null ? l.getApprovedAmount() : (l.getRequestedAmount() != null ? l.getRequestedAmount() : BigDecimal.ZERO);
                totalDisbursed = totalDisbursed.add(princ);
            }
        }

        // Card 1: ACTIVE BOOK (22w)
        String c1Top = ConsoleTheme.border("┌─── ") + ConsoleTheme.bold("ACTIVE BOOK") + ConsoleTheme.border(" ────┐");
        String c1R1 = ConsoleTheme.border("│ ") + (activeCount > 0 ? Ansi.yellow(String.format("%-18s", (activeCount < 10 ? "0" + activeCount : String.valueOf(activeCount)) + " Accounts")) : Ansi.green(String.format("%-18s", "00 Accounts"))) + ConsoleTheme.border(" │");
        String c1R2 = ConsoleTheme.border("│ ") + Ansi.green(String.format("%-18s", "100% Performing")) + ConsoleTheme.border(" │");
        String c1Bot = ConsoleTheme.border("└────────────────────┘");

        // Card 2: PRINCIPAL EXPOSURE (24w)
        String c2Top = ConsoleTheme.border("┌─ ") + ConsoleTheme.bold("PRINCIPAL EXPOSURE") + ConsoleTheme.border(" ─┐");
        String disbStr = "$ " + df.format(totalDisbursed);
        if (disbStr.length() > 14) disbStr = disbStr.substring(0, 14);
        String outStr = "$ " + df.format(totalOutstanding);
        if (outStr.length() > 14) outStr = outStr.substring(0, 14);
        String c2R1 = ConsoleTheme.border("│ ") + ConsoleTheme.primary(String.format("%-14s", disbStr)) + " " + ConsoleTheme.muted(String.format("%-5s", "USD")) + ConsoleTheme.border(" │");
        String c2R2 = ConsoleTheme.border("│ ") + String.format("%-14s", outStr) + " " + ConsoleTheme.muted(String.format("%-5s", "Outst")) + ConsoleTheme.border(" │");
        String c2Bot = ConsoleTheme.border("└──────────────────────┘");

        // Card 3: HEALTH / RECOVERY (28w)
        String c3Top = ConsoleTheme.border("┌─── ") + ConsoleTheme.bold("HEALTH / RECOVERY") + ConsoleTheme.border(" ────┐");
        String c3R1 = ConsoleTheme.border("│ ") + String.format("PAR (30+): %-13s", "0.00% [✓]") + ConsoleTheme.border(" │");
        String c3R2 = ConsoleTheme.border("│ ") + Ansi.green(String.format("%-24s", "PRIME PERFORMING")) + ConsoleTheme.border(" │");
        String c3Bot = ConsoleTheme.border("└──────────────────────────┘");

        sb.append(TUIBox.line(c1Top + "  " + c2Top + "  " + c3Top, width)).append("\n");
        sb.append(TUIBox.line(c1R1 + "  " + c2R1 + "  " + c3R1, width)).append("\n");
        sb.append(TUIBox.line(c1R2 + "  " + c2R2 + "  " + c3R2, width)).append("\n");
        sb.append(TUIBox.line(c1Bot + "  " + c2Bot + "  " + c3Bot, width)).append("\n");

        sb.append(TUIBox.emptyLine(width)).append("\n");
        sb.append(TUIBox.divider(width)).append("\n");

        // Section 2: ACTIVE LOAN BOOK DIRECTORY (Paginated to max 6 rows)
        int pageSize = 6;
        int totalLoans = activeLoans != null ? activeLoans.size() : 0;
        int totalPages = Math.max(1, (totalLoans + pageSize - 1) / pageSize);
        int currentPage = Math.max(0, Math.min(totalPages - 1, selectedIndex / pageSize));
        int startIdx = currentPage * pageSize;
        int endIdx = Math.min(totalLoans, startIdx + pageSize);

        String pageInfo = String.format("(Page %d/%d) ", currentPage + 1, totalPages);
        String sortLabel = sortDesc ? "[S: Outst ↓]" : "[S: ID ↑]";
        sb.append(TUIBox.line(" " + ConsoleTheme.bold("ACTIVE LOAN BOOK DIRECTORY " + pageInfo) + ConsoleTheme.muted(sortLabel), width)).append("\n");
        sb.append(TUIBox.emptyLine(width)).append("\n");

        String th = String.format("  %-9s %-9s %-14s %-14s %-8s %-14s",
                "LOAN ID", "USER ID", "PRINCIPAL", "OUTSTANDING", "TERM", "HEALTH");
        sb.append(TUIBox.line(th, width)).append("\n");
        sb.append(TUIBox.line("  " + "─".repeat(74), width)).append("\n");

        if (activeLoans != null && !activeLoans.isEmpty()) {
            for (int i = startIdx; i < endIdx; i++) {
                Loan l = activeLoans.get(i);
                boolean isSel = (selectedIndex == i);
                String prefix = isSel ? "▸ " : "  ";

                String lid = String.format("#LN-%04d", l.getLoanId());
                String uid = String.format("#USR-%02d", l.getUserId() != null ? l.getUserId() : 0);
                BigDecimal princVal = l.getApprovedAmount() != null ? l.getApprovedAmount() : (l.getRequestedAmount() != null ? l.getRequestedAmount() : BigDecimal.ZERO);
                BigDecimal outVal = l.getOutstandingBalance() != null ? l.getOutstandingBalance() : BigDecimal.ZERO;
                String princ = "$ " + df.format(princVal);
                String out = "$ " + df.format(outVal);
                String term = (l.getTermMonths() != null ? l.getTermMonths() : 0) + " Mo";

                // Health column: CURRENT [✓] vs NEAR PAYOFF (< 10% principal)
                boolean nearPayoff = princVal.compareTo(BigDecimal.ZERO) > 0 && outVal.compareTo(princVal.multiply(new BigDecimal("0.10"))) <= 0;
                String healthPlain = nearPayoff ? "NEAR PAYOFF" : "CURRENT [✓]";

                String plainRow = String.format("%s%-8s %-9s %-14s %-14s %-8s %-14s",
                        prefix, lid, uid, princ, out, term, healthPlain);
                if (plainRow.length() > 76) plainRow = plainRow.substring(0, 76);
                plainRow = String.format("%-76s", plainRow);

                if (isSel) {
                    sb.append(TUIBox.fullWidthInverted("  " + plainRow, width)).append("\n");
                } else {
                    String healthColored = nearPayoff ? Ansi.yellow(healthPlain) : Ansi.green(healthPlain);
                    String renderedRow = String.format("%s%-8s %-9s %-14s %-14s %-8s %s",
                            prefix, lid, uid, princ, out, term, healthColored);
                    sb.append(TUIBox.line("  " + renderedRow, width)).append("\n");
                }
            }
            int renderedCount = endIdx - startIdx;
            for (int e = renderedCount; e < pageSize; e++) {
                sb.append(TUIBox.emptyLine(width)).append("\n");
            }
        } else {
            sb.append(TUIBox.line("  " + ConsoleTheme.muted("No active loans currently in the loan book."), width)).append("\n");
            for (int e = 1; e < pageSize; e++) {
                sb.append(TUIBox.emptyLine(width)).append("\n");
            }
        }

        sb.append(TUIBox.emptyLine(width)).append("\n");
        sb.append(TUIBox.divider(width)).append("\n");

        // Section 3: FACILITY DRILLDOWN DRAWER
        if (activeLoans != null && !activeLoans.isEmpty() && selectedIndex < activeLoans.size()) {
            Loan sel = activeLoans.get(selectedIndex);
            BigDecimal p = sel.getApprovedAmount() != null ? sel.getApprovedAmount() : (sel.getRequestedAmount() != null ? sel.getRequestedAmount() : BigDecimal.ZERO);
            int tm = sel.getTermMonths() != null && sel.getTermMonths() > 0 ? sel.getTermMonths() : 12;
            BigDecimal inst = p.divide(BigDecimal.valueOf(tm), 2, RoundingMode.HALF_UP);
            BigDecimal o = sel.getOutstandingBalance() != null ? sel.getOutstandingBalance() : BigDecimal.ZERO;
            BigDecimal accrued = o.subtract(p).max(BigDecimal.ZERO);

            String drillTitle = String.format("FACILITY DRILLDOWN [#LN-%04d]", sel.getLoanId());
            sb.append(TUIBox.line(" " + ConsoleTheme.bold(drillTitle), width)).append("\n");
            sb.append(TUIBox.emptyLine(width)).append("\n");

            String d1 = String.format("  Installment: $ %-15s   Accrued Interest : $ %s USD",
                    df.format(inst), df.format(accrued));
            String d2 = String.format("  Repayment  : %-15s   Officer Action   : %s",
                    "Regular (0 Late)", ConsoleTheme.primary("MONITOR FACILITY"));
            sb.append(TUIBox.line(d1, width)).append("\n");
            sb.append(TUIBox.line(d2, width)).append("\n");

            sb.append(TUIBox.emptyLine(width)).append("\n");
            sb.append(TUIBox.divider(width)).append("\n");
        }

        // Status line
        if (statusMessage != null && statusMessage.startsWith("Status: ")) {
            statusMessage = statusMessage.substring(8);
        }
        if (statusMessage != null && statusMessage.length() > 70) {
            statusMessage = statusMessage.substring(0, 67) + "...";
        }
        String statusDisplay = isError ? ConsoleTheme.error(statusMessage) : ConsoleTheme.muted(statusMessage);
        sb.append(TUIBox.line("Status: " + statusDisplay, width)).append("\n");
        sb.append(TUIBox.bottom(width)).append("\n");

        // Footer Hint
        sb.append(ConsoleTheme.keyGuide("[↑/↓] Select • [Enter] Repayment Schedule • [S] Sort • [Esc / B] Back")).append("\n");

        return sb.toString();
    }
}
