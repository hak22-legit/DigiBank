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
import com.bank.model.dto.UserProfileDossier;
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
        // Drain lingering newline characters on entry
        TerminalInputHandler.drainBuffer(reader);

        StringBuilder idBuffer = new StringBuilder();
        Long searchUserId = null;
        List<Loan> userLoans = Collections.emptyList();
        UserProfileDossier dossier = null;

        int selectedIndex = 0;
        int filterIndex = 0; // 0 = ALL, 1 = ACTIVE, 2 = REJECTED
        int page = 1;
        final int pageSize = 6;

        String statusMessage = "Type Customer User ID and press [Enter] to inspect credit profile.";
        boolean isError = false;
        boolean firstRender = true;
        boolean inScreen = true;

        try {
            while (inScreen) {
                // Filter loans
                List<Loan> filteredLoans = new java.util.ArrayList<>();
                for (Loan l : userLoans) {
                    if (filterIndex == 1 && l.getStatus() != com.bank.model.enums.LoanStatus.ACTIVE) continue;
                    if (filterIndex == 2 && l.getStatus() != com.bank.model.enums.LoanStatus.REJECTED) continue;
                    filteredLoans.add(l);
                }

                int totalPages = Math.max(1, (int) Math.ceil((double) filteredLoans.size() / pageSize));
                if (page > totalPages) page = totalPages;
                if (page < 1) page = 1;

                int pageCount = Math.min(pageSize, Math.max(0, filteredLoans.size() - (page - 1) * pageSize));
                if (selectedIndex >= pageCount && pageCount > 0) {
                    selectedIndex = pageCount - 1;
                }

                String rendered = renderContent(idBuffer.toString(), searchUserId, dossier, userLoans, selectedIndex, filterIndex, page, statusMessage, isError, width);
                ScreenRenderer.render(rendered, firstRender);
                firstRender = false;

                TerminalInputHandler.KeyCode event = TerminalInputHandler.readKey(reader, true);
                if (event.code() == -1) {
                    inScreen = false;
                    navigator.pop();
                    break;
                }

                if (event.isDigit()) {
                    // Numeric Input Priority: Digits '0' through '9' exclusively append into the active search buffer string
                    if (idBuffer.length() < 10) {
                        idBuffer.append(event.ch());
                    }
                } else if (event.isBackspace()) {
                    if (!idBuffer.isEmpty()) {
                        idBuffer.deleteCharAt(idBuffer.length() - 1);
                    }
                } else if (event.isEnter()) {
                    if (!idBuffer.isEmpty()) {
                        try {
                            searchUserId = Long.parseLong(idBuffer.toString().trim());
                            userLoans = loanController.getCustomerBorrowingHistory(admin, searchUserId);
                            try {
                                dossier = adminController.getUserProfileDossier(admin, searchUserId);
                            } catch (Exception e) {
                                dossier = null;
                            }
                            filterIndex = 0;
                            page = 1;
                            selectedIndex = 0;
                            statusMessage = String.format("Showing ALL loan records for User #%d. Press [Tab] to toggle filter.", searchUserId);
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
                } else if (event.isTab()) {
                    // Filter cycling via [Tab]
                    filterIndex = (filterIndex + 1) % 3;
                    page = 1;
                    selectedIndex = 0;
                    String filterName = switch (filterIndex) {
                        case 1 -> "ACTIVE";
                        case 2 -> "REJECTED";
                        default -> "ALL";
                    };
                    if (searchUserId != null) {
                        statusMessage = String.format("Showing %s loan records for User #%d. Press [Tab] to toggle filter.", filterName, searchUserId);
                    } else {
                        statusMessage = "Filter: Showing " + filterName + " loan records.";
                    }
                } else if (event.isUp() || event.is('K') || event.is('k') || "\033[A".equals(event.rawSequence())) {
                    if (pageCount > 0) {
                        if (selectedIndex == 0) {
                            if (page > 1) {
                                page--;
                                int prevPageRows = Math.min(pageSize, Math.max(0, filteredLoans.size() - (page - 1) * pageSize));
                                selectedIndex = Math.max(0, prevPageRows - 1);
                                statusMessage = String.format("Page %d/%d. Showing records %d-%d of %d.",
                                        page, totalPages, (page - 1) * pageSize + 1, Math.min(page * pageSize, filteredLoans.size()), filteredLoans.size());
                                isError = false;
                            }
                        } else {
                            selectedIndex--;
                        }
                    }
                } else if (event.isDown() || event.is('J') || event.is('j') || "\033[B".equals(event.rawSequence())) {
                    if (pageCount > 0) {
                        if (selectedIndex == pageCount - 1) {
                            if (page < totalPages) {
                                page++;
                                selectedIndex = 0;
                                statusMessage = String.format("Page %d/%d. Showing records %d-%d of %d.",
                                        page, totalPages, (page - 1) * pageSize + 1, Math.min(page * pageSize, filteredLoans.size()), filteredLoans.size());
                                isError = false;
                            }
                        } else {
                            selectedIndex++;
                        }
                    }
                } else if (event.isRight() || event.is('N') || event.is('n') || "\033[C".equals(event.rawSequence())) {
                    if (page < totalPages) {
                        page++;
                        selectedIndex = 0;
                        statusMessage = String.format("Page %d/%d. Showing records %d-%d of %d.",
                                page, totalPages, (page - 1) * pageSize + 1, Math.min(page * pageSize, filteredLoans.size()), filteredLoans.size());
                        isError = false;
                    }
                } else if (event.isLeft() || event.is('P') || event.is('p') || "\033[D".equals(event.rawSequence())) {
                    if (page > 1) {
                        page--;
                        selectedIndex = 0;
                        statusMessage = String.format("Page %d/%d. Showing records %d-%d of %d.",
                                page, totalPages, (page - 1) * pageSize + 1, Math.min(page * pageSize, filteredLoans.size()), filteredLoans.size());
                        isError = false;
                    }
                } else if (event.isEscape() || (idBuffer.isEmpty() && (event.is('B') || event.is('b')))) {
                    if (!idBuffer.isEmpty()) {
                        idBuffer.setLength(0);
                        statusMessage = "Search input cleared. Press [Esc] to return.";
                        isError = false;
                    } else {
                        inScreen = false;
                        navigator.pop();
                        return;
                    }
                }
                // Unmapped keys discarded in default flow without exiting or throwing
            }
        } catch (IOException e) {
            logger.error("Error in BorrowingHistoryScreen loop", e);
        } finally {
            terminal.setAttributes(origAttributes);
        }
    }

    public static String renderContent(String searchInput, Long searchUserId, UserProfileDossier dossier,
                                       List<Loan> loans, String statusMessage, boolean isError, int width) {
        return renderContent(searchInput, searchUserId, dossier, loans, 0, 0, 1, statusMessage, isError, width);
    }

    public static String renderContent(String searchInput, Long searchUserId, UserProfileDossier dossier,
                                       List<Loan> loans, int selectedIndex, int filterIndex, int page,
                                       String statusMessage, boolean isError, int width) {
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

        if (searchUserId != null) {
            sb.append(TUIBox.line(" " + ConsoleTheme.bold("BORROWER DOSSIER & CREDIT METRICS"), width)).append("\n");
            sb.append(TUIBox.emptyLine(width)).append("\n");
            String custName = dossier != null && dossier.getFullName() != null ? dossier.getFullName() : ("Customer #" + searchUserId);
            String custStatus = dossier != null && dossier.getStatus() != null ? dossier.getStatus().name() : "ACTIVE";
            String custEmail = dossier != null && dossier.getEmail() != null ? dossier.getEmail() : "N/A";
            BigDecimal totalBorrowed = loans != null ? loans.stream()
                    .map(l -> l.getApprovedAmount() != null ? l.getApprovedAmount() : (l.getRequestedAmount() != null ? l.getRequestedAmount() : BigDecimal.ZERO))
                    .reduce(BigDecimal.ZERO, BigDecimal::add) : BigDecimal.ZERO;
            String r1 = String.format("  Customer Name : %-23s   Status         : %s", custName, custStatus);
            String r2 = String.format("  Email Address : %-23s   Total Borrowed : $ %s", custEmail, df.format(totalBorrowed));
            sb.append(TUIBox.line(r1, width)).append("\n");
            sb.append(TUIBox.line(r2, width)).append("\n");
            sb.append(TUIBox.emptyLine(width)).append("\n");
            sb.append(TUIBox.divider(width)).append("\n");
        }

        // Apply tab filtering
        List<Loan> allLoans = loans != null ? loans : Collections.emptyList();
        long allCount = allLoans.size();
        long activeCount = allLoans.stream().filter(l -> l.getStatus() == com.bank.model.enums.LoanStatus.ACTIVE).count();
        long rejCount = allLoans.stream().filter(l -> l.getStatus() == com.bank.model.enums.LoanStatus.REJECTED).count();

        List<Loan> filteredLoans = new java.util.ArrayList<>();
        for (Loan l : allLoans) {
            if (filterIndex == 1 && l.getStatus() != com.bank.model.enums.LoanStatus.ACTIVE) continue;
            if (filterIndex == 2 && l.getStatus() != com.bank.model.enums.LoanStatus.REJECTED) continue;
            filteredLoans.add(l);
        }

        final int pageSize = 6;
        int totalPages = Math.max(1, (int) Math.ceil((double) filteredLoans.size() / pageSize));
        if (page > totalPages) page = totalPages;
        if (page < 1) page = 1;

        String secTitle = String.format("HISTORICAL LOAN FACILITIES & REPAYMENT RECORD (Page %d/%d)", page, totalPages);
        sb.append(TUIBox.line(" " + ConsoleTheme.bold(secTitle), width)).append("\n");

        // Filter tabs line
        String tAll = String.format("[ ALL (%d) ]", allCount);
        String tActive = String.format("[ ACTIVE (%d) ]", activeCount);
        String tRej = String.format("[ REJECTED (%d) ]", rejCount);

        String tab0 = filterIndex == 0 ? ("\033[7m▸ " + tAll + "\033[0m") : ConsoleTheme.muted(tAll);
        String tab1 = filterIndex == 1 ? ("\033[7m▸ " + tActive + "\033[0m") : ConsoleTheme.muted(tActive);
        String tab2 = filterIndex == 2 ? ("\033[7m▸ " + tRej + "\033[0m") : ConsoleTheme.muted(tRej);

        String tabLine = String.format(" FILTER: %s %s   %s   %s",
                Ansi.yellow("[Tab]"), tab0, tab1, tab2);
        sb.append(TUIBox.line(tabLine, width)).append("\n");
        sb.append(TUIBox.emptyLine(width)).append("\n");

        String th = String.format("  %-9s %-14s %-10s %-12s %-12s %-11s",
                "LOAN ID", "AMOUNT", "TERM", "RATE (%)", "BALANCE", "STATUS");
        sb.append(TUIBox.line(th, width)).append("\n");
        sb.append(TUIBox.line("  " + "─".repeat(74), width)).append("\n");

        int startIdx = (page - 1) * pageSize;
        int endIdx = Math.min(startIdx + pageSize, filteredLoans.size());

        if (!filteredLoans.isEmpty()) {
            for (int i = 0; i < pageSize; i++) {
                int loanIdx = startIdx + i;
                if (loanIdx < endIdx) {
                    Loan l = filteredLoans.get(loanIdx);
                    boolean isSel = (selectedIndex == i);
                    String prefix = isSel ? "▸ " : "  ";
                    String lid = String.format("#LN-%04d", l.getLoanId());
                    String amt = "$ " + df.format(l.getRequestedAmount() != null ? l.getRequestedAmount() : BigDecimal.ZERO);
                    String term = (l.getTermMonths() != null ? l.getTermMonths() : 0) + " Mo";
                    String rate = (l.getInterestRate() != null ? df.format(l.getInterestRate()) : "0.00") + "%";
                    String bal = "$ " + df.format(l.getOutstandingBalance() != null ? l.getOutstandingBalance() : BigDecimal.ZERO);
                    String st = l.getStatus() != null ? l.getStatus().name() : "-";

                    String plainRow = String.format("%s%-8s %-14s %-10s %-12s %-12s %-14s", prefix, lid, amt, term, rate, bal, st);
                    if (plainRow.length() > 76) plainRow = plainRow.substring(0, 76);
                    plainRow = String.format("%-76s", plainRow);

                    if (isSel) {
                        sb.append(TUIBox.fullWidthInverted("  " + plainRow, width)).append("\n");
                    } else {
                        String stColored = switch (st) {
                            case "ACTIVE" -> Ansi.green(st);
                            case "REJECTED" -> Ansi.red(st);
                            case "PENDING" -> Ansi.yellow(st);
                            default -> ConsoleTheme.muted(st);
                        };
                        String renderedRow = String.format("%s%-8s %-14s %-10s %-12s %-12s %s",
                                prefix, lid, amt, term, rate, bal, stColored);
                        sb.append(TUIBox.line("  " + renderedRow, width)).append("\n");
                    }
                } else if (allLoans.isEmpty()) {
                    // Only show empty prompt if no loans searched at all
                    break;
                }
            }
        } else {
            String msg = searchUserId == null ? "Enter a customer ID above to view loan portfolio records."
                    : "No credit facilities found matching current filter.";
            sb.append(TUIBox.line("  " + ConsoleTheme.muted(msg), width)).append("\n");
        }

        sb.append(TUIBox.emptyLine(width)).append("\n");
        sb.append(TUIBox.divider(width)).append("\n");

        if (statusMessage != null && statusMessage.startsWith("Status: ")) {
            statusMessage = statusMessage.substring(8);
        }
        if (statusMessage != null && statusMessage.length() > 70) {
            statusMessage = statusMessage.substring(0, 67) + "...";
        }
        String statusDisplay = isError ? ConsoleTheme.error(statusMessage) : ConsoleTheme.muted(statusMessage);
        sb.append(TUIBox.line("Status: " + statusDisplay, width)).append("\n");
        sb.append(TUIBox.bottom(width)).append("\n");
        sb.append(ConsoleTheme.keyGuide("[↑/↓] Select  •  [N/P] Page  •  [Tab] Filter  •  [Digits] Search  •  [Esc] Back")).append("\n");

        return sb.toString();
    }
}
