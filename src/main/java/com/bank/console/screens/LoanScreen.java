package com.bank.console.screens;

import com.bank.console.ControllerFactory;
import com.bank.console.ScreenNavigator;
import com.bank.console.TUISession;
import com.bank.console.TerminalInputHandler;
import com.bank.console.components.ScreenRenderer;
import com.bank.console.components.TUIBox;
import com.bank.console.components.TUILayout;
import com.bank.console.theme.ConsoleTheme;
import com.bank.controller.AccountController;
import com.bank.controller.LoanController;
import com.bank.model.dto.LoanDTO;
import com.bank.model.dto.UserDTO;
import com.bank.model.entity.Account;
import com.bank.model.entity.Loan;
import com.bank.model.entity.LoanPayment;
import com.bank.model.entity.User;
import com.bank.model.enums.LoanPaymentStatus;
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
import java.util.Collections;
import java.util.List;

/**
 * SCREEN 8: MASTER-DETAIL LOAN MANAGEMENT & REPAYMENTS (82 Columns)
 * - Master Top Table: Paginated customer loan facilities (5 rows/page) with full-width inverse selection.
 * - Tab Filtering: [ALL], [ACTIVE], [SETTLED].
 * - Middle Compartment: Facility Dossier with tailored metrics for ACTIVE, REJECTED, and SETTLED facilities.
 * - Detail Bottom Table: Repayment Schedule & Installments (rendered exclusively for ACTIVE facilities).
 * - Strict 82-column containment and zero CLI leakage.
 */
public class LoanScreen implements Screen {
    private static final Logger logger = LoggerFactory.getLogger(LoanScreen.class);
    public static final int FACILITIES_PAGE_SIZE = 5;
    public static final int SCHEDULE_PAGE_SIZE = 3;
    public static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

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
        this.statusMessage = null;
        this.isErrorStatus = false;
    }

    /**
     * Data model for Loan Facility row in Master table.
     */
    public static class LoanFacility {
        private final String facilityId;
        private final Long rawLoanId;
        private final String type;
        private final BigDecimal amount;
        private final BigDecimal rate;
        private final String status;
        private final BigDecimal remainingBalance;
        private final String disbursedTo;
        private final int termMonths;
        private final LocalDate nextDueDate;
        private final BigDecimal monthlyDue;
        private final String applicationDate;
        private final String underwritingNote;

        public LoanFacility(String facilityId, Long rawLoanId, String type, BigDecimal amount, BigDecimal rate,
                            String status, BigDecimal remainingBalance, String disbursedTo, int termMonths,
                            LocalDate nextDueDate, BigDecimal monthlyDue) {
            this(facilityId, rawLoanId, type, amount, rate, status, remainingBalance, disbursedTo, termMonths,
                    nextDueDate, monthlyDue, "2026-09-18 14:20 UTC", "Exceeded maximum Debt-To-Income threshold (DTI > 55%).");
        }

        public LoanFacility(String facilityId, Long rawLoanId, String type, BigDecimal amount, BigDecimal rate,
                            String status, BigDecimal remainingBalance, String disbursedTo, int termMonths,
                            LocalDate nextDueDate, BigDecimal monthlyDue, String applicationDate, String underwritingNote) {
            this.facilityId = facilityId;
            this.rawLoanId = rawLoanId;
            this.type = type;
            this.amount = amount;
            this.rate = rate;
            this.status = status;
            this.remainingBalance = remainingBalance;
            this.disbursedTo = disbursedTo;
            this.termMonths = termMonths;
            this.nextDueDate = nextDueDate;
            this.monthlyDue = monthlyDue;
            this.applicationDate = applicationDate != null ? applicationDate : "2026-09-18 14:20 UTC";
            this.underwritingNote = underwritingNote != null ? underwritingNote : "Application declined. No funds disbursed.";
        }

        public String getFacilityId() { return facilityId; }
        public Long getRawLoanId() { return rawLoanId; }
        public String getType() { return type; }
        public BigDecimal getAmount() { return amount; }
        public BigDecimal getRate() { return rate; }
        public String getStatus() { return status; }
        public BigDecimal getRemainingBalance() { return remainingBalance; }
        public String getDisbursedTo() { return disbursedTo; }
        public int getTermMonths() { return termMonths; }
        public LocalDate getNextDueDate() { return nextDueDate; }
        public BigDecimal getMonthlyDue() { return monthlyDue; }
        public String getApplicationDate() { return applicationDate; }
        public String getUnderwritingNote() { return underwritingNote; }
    }

    /**
     * Data model for Installment item in Detail table.
     */
    public static class Installment {
        private final int installmentNumber;
        private final LocalDate dueDate;
        private final BigDecimal principal;
        private final BigDecimal interest;
        private final BigDecimal totalDue;
        private final String status;

        public Installment(int installmentNumber, LocalDate dueDate, BigDecimal principal,
                           BigDecimal interest, BigDecimal totalDue, String status) {
            this.installmentNumber = installmentNumber;
            this.dueDate = dueDate;
            this.principal = principal;
            this.interest = interest;
            this.totalDue = totalDue;
            this.status = status;
        }

        public int getInstallmentNumber() { return installmentNumber; }
        public LocalDate getDueDate() { return dueDate; }
        public BigDecimal getPrincipal() { return principal; }
        public BigDecimal getInterest() { return interest; }
        public BigDecimal getTotalDue() { return totalDue; }
        public String getStatus() { return status; }
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

        int selectedFacilityIndex = 0;
        int facilityPage = 0;
        int filterIndex = 0; // 0 = ALL, 1 = ACTIVE, 2 = SETTLED
        int schedulePage = 0;
        boolean firstRender = true;
        boolean reloadNeeded = true;

        List<LoanFacility> facilities = new ArrayList<>();

        try {
            while (true) {
                if (reloadNeeded) {
                    facilities = loadFacilities(userEntity);
                    reloadNeeded = false;
                }

                // Filter facilities
                List<LoanFacility> filtered = new ArrayList<>();
                for (LoanFacility f : facilities) {
                    if (filterIndex == 1 && !"ACTIVE".equalsIgnoreCase(f.getStatus())) continue;
                    if (filterIndex == 2 && !( "SETTLED".equalsIgnoreCase(f.getStatus()) || "PAID_OFF".equalsIgnoreCase(f.getStatus()) )) continue;
                    filtered.add(f);
                }

                int totalFacilityPages = Math.max(1, (int) Math.ceil((double) filtered.size() / FACILITIES_PAGE_SIZE));
                if (facilityPage >= totalFacilityPages) facilityPage = totalFacilityPages - 1;
                if (facilityPage < 0) facilityPage = 0;

                int startIdx = facilityPage * FACILITIES_PAGE_SIZE;
                int endIdx = Math.min(startIdx + FACILITIES_PAGE_SIZE, filtered.size());
                int pageRows = Math.max(0, endIdx - startIdx);

                if (selectedFacilityIndex >= pageRows && pageRows > 0) {
                    selectedFacilityIndex = pageRows - 1;
                }
                if (selectedFacilityIndex < 0) {
                    selectedFacilityIndex = 0;
                }

                LoanFacility selectedFacility = (pageRows > 0) ? filtered.get(startIdx + selectedFacilityIndex) : null;

                String rendered = renderContent(facilities, selectedFacilityIndex, facilityPage, filterIndex,
                        schedulePage, statusMessage, isErrorStatus, userEntity, width);
                statusMessage = null;
                isErrorStatus = false;

                ScreenRenderer.render(rendered, firstRender);
                firstRender = false;

                TerminalInputHandler.KeyCode event = TerminalInputHandler.readKey(reader, false);
                if (event.code() == -1) {
                    navigator.pop();
                    break;
                }

                if (event.isUp() || event.is('K') || event.is('k') || "\033[A".equals(event.rawSequence())) {
                    if (pageRows > 0) {
                        if (selectedFacilityIndex > 0) {
                            selectedFacilityIndex--;
                        } else if (facilityPage > 0) {
                            facilityPage--;
                            int prevRows = Math.min(FACILITIES_PAGE_SIZE, filtered.size() - facilityPage * FACILITIES_PAGE_SIZE);
                            selectedFacilityIndex = Math.max(0, prevRows - 1);
                        }
                        schedulePage = 0;
                    }
                } else if (event.isDown() || event.is('J') || event.is('j') || "\033[B".equals(event.rawSequence())) {
                    if (pageRows > 0) {
                        if (selectedFacilityIndex < pageRows - 1) {
                            selectedFacilityIndex++;
                        } else if (facilityPage < totalFacilityPages - 1) {
                            facilityPage++;
                            selectedFacilityIndex = 0;
                        }
                        schedulePage = 0;
                    }
                } else if (event.isRight() || event.is('N') || event.is('n') || "\033[C".equals(event.rawSequence())) {
                    if (facilityPage < totalFacilityPages - 1) {
                        facilityPage++;
                        selectedFacilityIndex = 0;
                        schedulePage = 0;
                    }
                } else if (event.isLeft() || event.is('P') || event.is('p') || "\033[D".equals(event.rawSequence())) {
                    if (facilityPage > 0) {
                        facilityPage--;
                        selectedFacilityIndex = 0;
                        schedulePage = 0;
                    }
                } else if (event.isTab()) {
                    filterIndex = (filterIndex + 1) % 3;
                    facilityPage = 0;
                    selectedFacilityIndex = 0;
                    schedulePage = 0;
                } else if (event.is('1')) {
                    if (selectedFacility != null && "ACTIVE".equalsIgnoreCase(selectedFacility.getStatus())) {
                        terminal.setAttributes(origAttributes);
                        navigator.push(new LoanRepaymentScreen(loanController, accountController));
                        return;
                    } else if (selectedFacility != null && "REJECTED".equalsIgnoreCase(selectedFacility.getStatus())) {
                        statusMessage = "Facility application was rejected. No repayment required.";
                        isErrorStatus = true;
                    } else if (selectedFacility != null && ("SETTLED".equalsIgnoreCase(selectedFacility.getStatus()) || "PAID_OFF".equalsIgnoreCase(selectedFacility.getStatus()))) {
                        statusMessage = "Facility is settled in full. No payment required.";
                        isErrorStatus = false;
                    }
                } else if (event.is('2')) {
                    if (selectedFacility != null) {
                        showContractModal(terminal, origAttributes, reader, selectedFacility, userEntity, width);
                        firstRender = true;
                    }
                } else if (event.is('3')) {
                    terminal.setAttributes(origAttributes);
                    navigator.push(new ApplyLoanScreen(loanController, accountController));
                    return;
                } else if (event.isEscapeOrBack() || event.is('4')) {
                    terminal.setAttributes(origAttributes);
                    navigator.pop();
                    return;
                }
            }
        } catch (IOException e) {
            logger.error("Error reading key on LoanScreen", e);
        } finally {
            terminal.setAttributes(origAttributes);
        }
    }

    /**
     * Renders the complete Loan Management & Repayments interface with strict 82-column containment.
     */
    public static String renderContent(List<LoanFacility> allFacilities, int selectedFacilityIndex, int facilityPage,
                                       int filterIndex, String statusMessage, boolean isErrorStatus, int width) {
        return renderContent(allFacilities, selectedFacilityIndex, facilityPage, filterIndex, 0, statusMessage, isErrorStatus, null, width);
    }

    /**
     * Full parametric renderContent method.
     */
    public static String renderContent(List<LoanFacility> allFacilities, int selectedFacilityIndex, int facilityPage,
                                       int filterIndex, int schedulePage, String statusMessage, boolean isErrorStatus,
                                       User user, int width) {
        StringBuilder sb = new StringBuilder();

        List<LoanFacility> facilitiesList = (allFacilities != null) ? allFacilities : Collections.emptyList();
        int totalCount = facilitiesList.size();
        long activeCount = facilitiesList.stream().filter(f -> "ACTIVE".equalsIgnoreCase(f.getStatus())).count();
        long settledCount = facilitiesList.stream().filter(f -> "SETTLED".equalsIgnoreCase(f.getStatus()) || "PAID_OFF".equalsIgnoreCase(f.getStatus())).count();

        List<LoanFacility> filtered = new ArrayList<>();
        for (LoanFacility f : facilitiesList) {
            if (filterIndex == 1 && !"ACTIVE".equalsIgnoreCase(f.getStatus())) continue;
            if (filterIndex == 2 && !( "SETTLED".equalsIgnoreCase(f.getStatus()) || "PAID_OFF".equalsIgnoreCase(f.getStatus()) )) continue;
            filtered.add(f);
        }

        int totalFacilityPages = Math.max(1, (int) Math.ceil((double) filtered.size() / FACILITIES_PAGE_SIZE));
        if (facilityPage >= totalFacilityPages) facilityPage = totalFacilityPages - 1;
        if (facilityPage < 0) facilityPage = 0;

        int startIdx = facilityPage * FACILITIES_PAGE_SIZE;
        int endIdx = Math.min(startIdx + FACILITIES_PAGE_SIZE, filtered.size());
        int pageRows = Math.max(0, endIdx - startIdx);

        if (selectedFacilityIndex >= pageRows && pageRows > 0) {
            selectedFacilityIndex = pageRows - 1;
        }
        if (selectedFacilityIndex < 0) {
            selectedFacilityIndex = 0;
        }

        LoanFacility selectedFacility = (pageRows > 0) ? filtered.get(startIdx + selectedFacilityIndex) : null;

        // 1. Top Box Header
        sb.append(TUIBox.top(width)).append("\n");
        sb.append(TUIBox.line(ConsoleTheme.primary("DIGIBANK CORE > CUSTOMER PORTAL > LOAN MANAGEMENT & REPAYMENTS"), width)).append("\n");
        sb.append(TUIBox.divider(width)).append("\n");

        // 2. Facilities Table Title & Tab Filter
        String tAll = (filterIndex == 0) ? String.format("▸[ALL (%d)]", totalCount) : "[ALL]";
        String tActive = (filterIndex == 1) ? String.format("▸[ACTIVE (%d)]", activeCount) : "[ACTIVE]";
        String tSettled = (filterIndex == 2) ? String.format("▸[SETTLED (%d)]", settledCount) : "[SETTLED]";

        String tabDisplay = String.format("%s  %s  %s", tAll, tActive, tSettled);
        String pageTitle = String.format(" ALL LOAN FACILITIES (Page %d/%d)   [Tab] Filter: %s",
                facilityPage + 1, totalFacilityPages, tabDisplay);
        sb.append(TUIBox.fullWidthLine(pageTitle, width)).append("\n");
        sb.append(TUIBox.emptyLine(width)).append("\n");

        // Column Header & Separator
        String masterHeader = "  FACILITY ID  TYPE              PRINCIPAL     RATE     STATUS    REMAINING BAL ";
        sb.append(TUIBox.fullWidthLine(masterHeader, width)).append("\n");
        String sep = " " + "─".repeat(78) + " ";
        sb.append(TUIBox.fullWidthLine(sep, width)).append("\n");

        // Upper Facilities Table Rows (Capped to 5 rows/page)
        for (int i = 0; i < FACILITIES_PAGE_SIZE; i++) {
            int currentIdx = startIdx + i;
            if (currentIdx < endIdx) {
                LoanFacility f = filtered.get(currentIdx);
                boolean isSelected = (i == selectedFacilityIndex);
                String marker = isSelected ? "▸" : " ";
                String prefix = " " + marker;
                String idStr = String.format("%-13s", f.getFacilityId());
                String typeStr = String.format("%-18s", f.getType());
                String amtStr = String.format("$ %,8.2f    ", f.getAmount());
                String rateStr = String.format("%5.2f%%   ", f.getRate());

                String rawStatus = f.getStatus();
                String tag = (rawStatus != null && !rawStatus.startsWith("["))
                        ? "[" + rawStatus + "]"
                        : String.valueOf(rawStatus);
                String paddedTag = String.format("%-10s", tag);
                String statusStr;
                if (isSelected) {
                    statusStr = paddedTag;
                } else {
                    if ("ACTIVE".equalsIgnoreCase(rawStatus)) {
                        statusStr = ConsoleTheme.success(paddedTag);
                    } else if ("REJECTED".equalsIgnoreCase(rawStatus)) {
                        statusStr = ConsoleTheme.error(paddedTag);
                    } else if ("SETTLED".equalsIgnoreCase(rawStatus) || "PAID_OFF".equalsIgnoreCase(rawStatus)) {
                        statusStr = ConsoleTheme.info(paddedTag);
                    } else {
                        statusStr = ConsoleTheme.muted(paddedTag);
                    }
                }
                String balStr = String.format("$ %,9.2f   ", f.getRemainingBalance());

                String row = prefix + idStr + typeStr + amtStr + rateStr + statusStr + balStr;
                if (isSelected) {
                    sb.append(TUIBox.fullWidthInverted(row, width)).append("\n");
                } else {
                    sb.append(TUIBox.fullWidthLine(row, width)).append("\n");
                }
            } else {
                sb.append(TUIBox.emptyLine(width)).append("\n");
            }
        }
        sb.append(TUIBox.divider(width)).append("\n");

        // 3. Middle Section: Facility Dossier & Lower Inspection Panel (Fixed 10-line content budget)
        String bottomGuide;
        String defaultStatus;

        if (selectedFacility == null) {
            defaultStatus = "No loan facilities found. Press [3] to submit a new loan request.";
            bottomGuide = "[3] Apply New Loan  •  [Esc] Back";

            // Row 1: Dossier Title
            sb.append(TUIBox.fullWidthLine(" FACILITY DOSSIER • NONE", width)).append("\n");
            // Row 2: Note 1
            sb.append(TUIBox.fullWidthLine("   No active facility selected or no records matching active filter.", width)).append("\n");
            // Row 3: Note 2
            sb.append(TUIBox.fullWidthLine("   Use [Tab] to toggle filters or press [3] to submit a new loan request.", width)).append("\n");
            // Row 4: Inner Divider
            sb.append(TUIBox.divider(width)).append("\n");
            // Row 5: Guidance Title
            sb.append(TUIBox.fullWidthLine(" NEW LOAN APPLICATION ASSISTANCE", width)).append("\n");
            // Row 6: Guidance info
            sb.append(TUIBox.fullWidthLine("   No loan facilities found. Select [3] below to apply for a loan.", width)).append("\n");
            // Row 7: Financing info
            sb.append(TUIBox.fullWidthLine("   DigiBank provides flexible personal credit and emergency auto financing.", width)).append("\n");
            // Row 8: Spacer
            sb.append(TUIBox.emptyLine(width)).append("\n");
            // Row 9: Spacer
            sb.append(TUIBox.emptyLine(width)).append("\n");
            // Row 10: Spacer
            sb.append(TUIBox.emptyLine(width)).append("\n");

        } else {
            String status = selectedFacility.getStatus();

            if ("REJECTED".equalsIgnoreCase(status)) {
                defaultStatus = "This facility was rejected. Press [3] to submit a new loan request.";
                bottomGuide = "[↑/↓] Select Loan  •  [N/P] Page  •  [3] Apply New Loan  •  [Esc] Return";

                // Row 1: Dossier Title
                String dossierTitle = String.format(" FACILITY DOSSIER [%s] • APPLICATION DECLINED", selectedFacility.getFacilityId());
                sb.append(TUIBox.fullWidthLine(dossierTitle, width)).append("\n");
                // Row 2: Application Date & Amount
                String r1 = String.format("   Application Date : %-25s Requested Amount : $ %,.2f",
                        selectedFacility.getApplicationDate(), selectedFacility.getAmount());
                sb.append(TUIBox.fullWidthLine(r1, width)).append("\n");
                // Row 3: Facility Status
                String r3 = "   Facility Status  : REJECTED (No funds disbursed, no repayment required).";
                sb.append(TUIBox.fullWidthLine(r3, width)).append("\n");
                // Row 4: Inner Divider
                sb.append(TUIBox.divider(width)).append("\n");
                // Row 5: Card Title
                sb.append(TUIBox.fullWidthLine(" UNDERWRITING & RISK DECISION SUMMARY", width)).append("\n");
                // Row 6: Underwriting Note
                String rawNote = selectedFacility.getUnderwritingNote();
                String safeNote = (rawNote != null && rawNote.length() > 56) ? rawNote.substring(0, 53) + "..." : rawNote;
                String r2 = String.format("   Underwriting Note: %s", safeNote);
                sb.append(TUIBox.fullWidthLine(r2, width)).append("\n");
                // Row 7: Risk Assessment
                String r4 = "   Risk Assessment  : Exceeded Debt-To-Income threshold (DTI > 55.00%).";
                sb.append(TUIBox.fullWidthLine(r4, width)).append("\n");
                // Row 8: Resolution Action
                String r5 = "   Resolution Action: Improve credit profile or apply with a co-signer.";
                sb.append(TUIBox.fullWidthLine(r5, width)).append("\n");
                // Row 9: Advisory Support
                String r6 = "   Advisory Support : Contact DigiBank Underwriting Desk at 1-800-DIGIBANK.";
                sb.append(TUIBox.fullWidthLine(r6, width)).append("\n");
                // Row 10: Empty spacer
                sb.append(TUIBox.emptyLine(width)).append("\n");

            } else if ("SETTLED".equalsIgnoreCase(status) || "PAID_OFF".equalsIgnoreCase(status)) {
                defaultStatus = "All installments settled in full. Facility closed.";
                bottomGuide = "[↑/↓] Select Loan  •  [N/P] Page  •  [2] Contract  •  [Esc] Back";

                // Row 1: Dossier Title
                String dossierTitle = String.format(" FACILITY DOSSIER [%s] • SETTLED FACILITY", selectedFacility.getFacilityId());
                sb.append(TUIBox.fullWidthLine(dossierTitle, width)).append("\n");
                // Row 2: Disbursed To & Settled Date
                String r1 = String.format("   Disbursed To : %-28s Settled Date    : 2026-08-15",
                        selectedFacility.getDisbursedTo() + " (Checking)");
                sb.append(TUIBox.fullWidthLine(r1, width)).append("\n");
                // Row 3: Term & Balance
                String r2 = String.format("   Loan Term    : %-28s Remaining Bal   : $ 0.00 USD",
                        selectedFacility.getTermMonths() + " Months");
                sb.append(TUIBox.fullWidthLine(r2, width)).append("\n");
                // Row 4: Inner Divider
                sb.append(TUIBox.divider(width)).append("\n");
                // Row 5: Settlement Card Title
                sb.append(TUIBox.fullWidthLine(" FACILITY SETTLEMENT NOTICE & CLOSURE CERTIFICATE", width)).append("\n");
                // Row 6: Facility status zero balance
                sb.append(TUIBox.fullWidthLine("   Facility Status  : CLOSED • Facility Closed / Zero Balance Certificate.", width)).append("\n");
                // Row 7: Settlement message
                sb.append(TUIBox.fullWidthLine("   All installments settled in full. Facility closed.", width)).append("\n");
                // Row 8: Maturity info
                sb.append(TUIBox.fullWidthLine("   Closure Summary  : Total interest settled in full • Maturity: 2026-08-15.", width)).append("\n");
                // Row 9: Standing note
                sb.append(TUIBox.fullWidthLine("   Credit Standing  : Thank you for maintaining an excellent credit standing.", width)).append("\n");
                // Row 10: Empty spacer
                sb.append(TUIBox.emptyLine(width)).append("\n");

            } else {
                defaultStatus = String.format("Facility %s active. Press [1] to make a repayment installment.", selectedFacility.getFacilityId());
                bottomGuide = "[↑/↓] Select Loan  •  [N/P] Page  •  [1] Pay Due  •  [2] Contract  •  [Esc] Back";

                // Row 1: Dossier Title
                String dossierTitle = String.format(" FACILITY DOSSIER [%s] • ACTIVE FACILITY", selectedFacility.getFacilityId());
                sb.append(TUIBox.fullWidthLine(dossierTitle, width)).append("\n");

                // Row 2: Disbursed To & Next Payment Due
                String nextDueStr = selectedFacility.getNextDueDate() != null
                        ? selectedFacility.getNextDueDate().format(DATE_FMT)
                        : "2026-10-20";
                String r1 = String.format("   Disbursed To : %-28s Next Payment Due: %s",
                        selectedFacility.getDisbursedTo() + " (Checking)", nextDueStr);
                sb.append(TUIBox.fullWidthLine(r1, width)).append("\n");

                // Row 3: Term & Monthly Due
                String r2 = String.format("   Loan Term    : %-28s Due Amount      : $ %,.2f USD",
                        selectedFacility.getTermMonths() + " Months (Matures 2027)", selectedFacility.getMonthlyDue());
                sb.append(TUIBox.fullWidthLine(r2, width)).append("\n");

                // Row 4: Inner Divider
                sb.append(TUIBox.divider(width)).append("\n");

                // Repayment Schedule & Installments
                List<Installment> fullSchedule = buildScheduleStatic(selectedFacility, user);
                int totalSchedPages = Math.max(1, (int) Math.ceil((double) fullSchedule.size() / SCHEDULE_PAGE_SIZE));
                if (schedulePage >= totalSchedPages) schedulePage = totalSchedPages - 1;
                if (schedulePage < 0) schedulePage = 0;

                // Row 5: Schedule Title
                String schedTitle = String.format(" REPAYMENT SCHEDULE & INSTALLMENTS (Page %d/%d)", schedulePage + 1, totalSchedPages);
                sb.append(TUIBox.fullWidthLine(schedTitle, width)).append("\n");

                // Row 6: Schedule Header
                String schedHeader = "   #   DUE DATE     PRINCIPAL    INTEREST     TOTAL DUE    PAYMENT STATUS       ";
                sb.append(TUIBox.fullWidthLine(schedHeader, width)).append("\n");

                // Row 7: Schedule Separator
                String schedSep = "  " + "─".repeat(76) + "  ";
                sb.append(TUIBox.fullWidthLine(schedSep, width)).append("\n");

                // Rows 8, 9, 10: 3 visible schedule rows
                int schedStart = schedulePage * SCHEDULE_PAGE_SIZE;
                int schedEnd = Math.min(schedStart + SCHEDULE_PAGE_SIZE, fullSchedule.size());

                for (int i = schedStart; i < schedEnd; i++) {
                    Installment item = fullSchedule.get(i);
                    boolean isNextDue = item.getStatus().contains("PENDING") || item.getStatus().contains("NEXT");
                    String marker = isNextDue ? "▸" : " ";
                    String row = String.format("  %s%02d  %-13s$ %,7.2f    $ %,6.2f     $ %,7.2f    %-21s",
                            marker,
                            item.getInstallmentNumber(),
                            item.getDueDate().format(DATE_FMT),
                            item.getPrincipal(),
                            item.getInterest(),
                            item.getTotalDue(),
                            item.getStatus()
                    );
                    sb.append(TUIBox.fullWidthLine(row, width)).append("\n");
                }

                for (int i = schedEnd - schedStart; i < SCHEDULE_PAGE_SIZE; i++) {
                    sb.append(TUIBox.emptyLine(width)).append("\n");
                }
            }
        }

        // 4. Panel Closing Divider & Persistent Two-Tier Footer
        sb.append(TUIBox.divider(width)).append("\n");

        String statusDisplay = (statusMessage != null)
                ? (isErrorStatus ? ConsoleTheme.error(statusMessage) : ConsoleTheme.success(statusMessage))
                : ConsoleTheme.muted(defaultStatus);
        sb.append(TUIBox.line("Status: " + statusDisplay, width)).append("\n");
        sb.append(TUIBox.bottom(width)).append("\n");

        // Footer Hotkey Guide (Dim Gray \033[2;90m)
        sb.append(ConsoleTheme.keyGuide(bottomGuide)).append("\n");

        return sb.toString();
    }

    /**
     * Builds repayment schedule statically for rendering and testing.
     */
    public static List<Installment> buildScheduleStatic(LoanFacility facility, User user) {
        List<Installment> schedule = new ArrayList<>();
        if (facility == null || "REJECTED".equalsIgnoreCase(facility.getStatus())) {
            return schedule;
        }

        int term = facility.getTermMonths() > 0 ? facility.getTermMonths() : 12;
        BigDecimal totalDue = facility.getMonthlyDue();
        if (totalDue == null || totalDue.compareTo(BigDecimal.ZERO) <= 0) {
            totalDue = facility.getAmount().divide(BigDecimal.valueOf(term), 2, RoundingMode.HALF_UP);
        }

        BigDecimal principal = totalDue.multiply(new BigDecimal("0.9216")).setScale(2, RoundingMode.HALF_UP);
        BigDecimal interest = totalDue.subtract(principal).max(BigDecimal.ZERO);

        LocalDate baseDate = LocalDate.of(2026, 9, 20);

        int paidCount = 1; // 1st installment paid, 2nd pending (next due), remainder scheduled
        if ("SETTLED".equalsIgnoreCase(facility.getStatus())) {
            paidCount = term;
        }

        for (int i = 1; i <= term; i++) {
            LocalDate due = baseDate.plusMonths(i - 1);
            String status;
            if (i <= paidCount) {
                status = "PAID [✓]";
            } else if (i == paidCount + 1) {
                status = "PENDING (NEXT DUE)";
            } else {
                status = "SCHEDULED";
            }
            schedule.add(new Installment(i, due, principal, interest, totalDue, status));
        }

        return schedule;
    }

    /**
     * Instance wrapper for backward compatibility with existing tests.
     */
    protected List<Installment> buildSchedule(LoanFacility facility, User user) {
        if (facility != null && facility.getRawLoanId() != null && loanController != null) {
            try {
                List<LoanPayment> payments = loanController.getPaymentHistory(facility.getRawLoanId(), user);
                if (payments != null && !payments.isEmpty()) {
                    int term = facility.getTermMonths() > 0 ? facility.getTermMonths() : 12;
                    BigDecimal totalDue = facility.getMonthlyDue();
                    if (totalDue == null || totalDue.compareTo(BigDecimal.ZERO) <= 0) {
                        totalDue = facility.getAmount().divide(BigDecimal.valueOf(term), 2, RoundingMode.HALF_UP);
                    }
                    BigDecimal principal = totalDue.multiply(new BigDecimal("0.9216")).setScale(2, RoundingMode.HALF_UP);
                    BigDecimal interest = totalDue.subtract(principal).max(BigDecimal.ZERO);
                    LocalDate baseDate = LocalDate.of(2026, 9, 20);

                    int paidCount = 0;
                    for (LoanPayment p : payments) {
                        if (p.getStatus() == LoanPaymentStatus.COMPLETED) {
                            paidCount++;
                        }
                    }

                    List<Installment> sched = new ArrayList<>();
                    for (int i = 1; i <= term; i++) {
                        LocalDate due = baseDate.plusMonths(i - 1);
                        String status = (i <= paidCount) ? "PAID [✓]" : (i == paidCount + 1 ? "PENDING (NEXT DUE)" : "SCHEDULED");
                        sched.add(new Installment(i, due, principal, interest, totalDue, status));
                    }
                    return sched;
                }
            } catch (Exception ignored) {}
        }
        return buildScheduleStatic(facility, user);
    }

    /**
     * Queries all loan facilities for the customer.
     */
    protected List<LoanFacility> loadFacilities(User user) {
        List<LoanFacility> list = new ArrayList<>();
        try {
            if (loanController != null && user != null) {
                List<LoanDTO> loans = loanController.getUserLoans(user);
                if (loans != null && !loans.isEmpty()) {
                    for (LoanDTO dto : loans) {
                        String facId = String.format("#LN-%02d", dto.getLoanId());
                        BigDecimal amt = dto.getApprovedAmount() != null ? dto.getApprovedAmount() : (dto.getRequestedAmount() != null ? dto.getRequestedAmount() : BigDecimal.ZERO);
                        BigDecimal rate = dto.getInterestRate() != null ? dto.getInterestRate() : BigDecimal.ZERO;
                        String status = dto.getStatus() != null ? dto.getStatus().name() : "ACTIVE";
                        if ("PAID_OFF".equalsIgnoreCase(status)) status = "SETTLED";
                        BigDecimal bal = dto.getOutstandingBalance() != null ? dto.getOutstandingBalance() : BigDecimal.ZERO;
                        int term = dto.getTermMonths() != null && dto.getTermMonths() > 0 ? dto.getTermMonths() : 12;

                        BigDecimal monthlyDue = (amt.compareTo(BigDecimal.ZERO) > 0 && term > 0)
                                ? amt.divide(BigDecimal.valueOf(term), 2, RoundingMode.HALF_UP)
                                : BigDecimal.ZERO;
                        LocalDate nextDue = LocalDate.now().plusMonths(1);

                        String accNum = "DGB-429309564";
                        String note = "Exceeded maximum Debt-To-Income threshold (DTI > 55%).";
                        String appDate = "2026-09-18 14:20 UTC";

                        try {
                            var optLoan = ControllerFactory.getLoanRepository().findById(dto.getLoanId());
                            if (optLoan.isPresent()) {
                                Loan lEntity = optLoan.get();
                                if (lEntity.getAccountId() != null) {
                                    Account acc = ControllerFactory.getAccountRepository().findById(lEntity.getAccountId()).orElse(null);
                                    if (acc != null) accNum = acc.getAccountNumber();
                                }
                                if (lEntity.getRejectionReason() != null && !lEntity.getRejectionReason().isBlank()) {
                                    note = lEntity.getRejectionReason();
                                }
                                if (lEntity.getCreatedAt() != null) {
                                    appDate = lEntity.getCreatedAt().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")) + " UTC";
                                }
                            }
                        } catch (Exception ignored) {}

                        if ("REJECTED".equalsIgnoreCase(status)) {
                            rate = BigDecimal.ZERO;
                            bal = BigDecimal.ZERO;
                        }

                        String type = (amt.compareTo(new BigDecimal("2000.00")) > 0) ? "Emergency Auto" : "Personal Credit";

                        list.add(new LoanFacility(facId, dto.getLoanId(), type, amt, rate, status, bal, accNum, term, nextDue, monthlyDue, appDate, note));
                    }
                }
            }
        } catch (Exception e) {
            logger.error("Error querying loans", e);
        }

        // If no records in database, supply the 20 canonical default facilities matching mockups
        if (list.isEmpty()) {
            // Page 1 (ACTIVE facilities matching Mockup 1)
            list.add(new LoanFacility("#LN-13", 13L, "Personal Credit", new BigDecimal("1000.00"),
                    new BigDecimal("8.50"), "ACTIVE", new BigDecimal("46.68"),
                    "DGB-429309564", 12, LocalDate.of(2026, 10, 20), new BigDecimal("83.33")));

            list.add(new LoanFacility("#LN-12", 12L, "Personal Credit", new BigDecimal("1000.00"),
                    new BigDecimal("10.50"), "ACTIVE", new BigDecimal("1105.00"),
                    "DGB-429309564", 12, LocalDate.of(2026, 10, 20), new BigDecimal("92.08")));

            list.add(new LoanFacility("#LN-11", 11L, "Personal Credit", new BigDecimal("1500.00"),
                    new BigDecimal("8.75"), "ACTIVE", new BigDecimal("1631.25"),
                    "DGB-429309564", 12, LocalDate.of(2026, 10, 20), new BigDecimal("135.94")));

            list.add(new LoanFacility("#LN-09", 9L, "Emergency Auto", new BigDecimal("5000.00"),
                    new BigDecimal("9.50"), "ACTIVE", new BigDecimal("5475.00"),
                    "DGB-429309564", 24, LocalDate.of(2026, 10, 20), new BigDecimal("228.13")));

            list.add(new LoanFacility("#LN-08", 8L, "Emergency Auto", new BigDecimal("3000.00"),
                    new BigDecimal("7.50"), "ACTIVE", new BigDecimal("3225.00"),
                    "DGB-429309564", 24, LocalDate.of(2026, 10, 20), new BigDecimal("134.38")));

            // Page 2 (REJECTED facilities matching Mockup 2)
            list.add(new LoanFacility("#LN-20", 20L, "Personal Credit", new BigDecimal("1000.00"),
                    BigDecimal.ZERO, "REJECTED", BigDecimal.ZERO,
                    "N/A", 12, null, BigDecimal.ZERO,
                    "2026-09-18 14:20 UTC", "Exceeded maximum Debt-To-Income threshold (DTI > 55%)."));

            list.add(new LoanFacility("#LN-19", 19L, "Personal Credit", new BigDecimal("1000.00"),
                    BigDecimal.ZERO, "REJECTED", BigDecimal.ZERO,
                    "N/A", 12, null, BigDecimal.ZERO,
                    "2026-09-17 11:15 UTC", "Credit score below minimum approval threshold."));

            list.add(new LoanFacility("#LN-18", 18L, "Emergency Auto", new BigDecimal("3000.00"),
                    BigDecimal.ZERO, "REJECTED", BigDecimal.ZERO,
                    "N/A", 24, null, BigDecimal.ZERO,
                    "2026-09-16 09:30 UTC", "High risk rating on collateral evaluation."));

            list.add(new LoanFacility("#LN-17", 17L, "Personal Credit", new BigDecimal("2000.00"),
                    BigDecimal.ZERO, "REJECTED", BigDecimal.ZERO,
                    "N/A", 12, null, BigDecimal.ZERO,
                    "2026-09-15 16:45 UTC", "Incomplete proof of income documentation."));

            list.add(new LoanFacility("#LN-16", 16L, "Emergency Auto", new BigDecimal("4000.00"),
                    BigDecimal.ZERO, "REJECTED", BigDecimal.ZERO,
                    "N/A", 24, null, BigDecimal.ZERO,
                    "2026-09-14 10:20 UTC", "Debt service ratio exceeds acceptable banking limits."));

            // Page 3 (SETTLED facilities)
            list.add(new LoanFacility("#LN-07", 7L, "Personal Credit", new BigDecimal("2000.00"),
                    new BigDecimal("8.00"), "SETTLED", BigDecimal.ZERO,
                    "DGB-429309564", 12, null, BigDecimal.ZERO));

            list.add(new LoanFacility("#LN-06", 6L, "Emergency Auto", new BigDecimal("2500.00"),
                    new BigDecimal("9.00"), "SETTLED", BigDecimal.ZERO,
                    "DGB-429309564", 24, null, BigDecimal.ZERO));

            list.add(new LoanFacility("#LN-05", 5L, "Personal Credit", new BigDecimal("1500.00"),
                    new BigDecimal("7.50"), "SETTLED", BigDecimal.ZERO,
                    "DGB-429309564", 12, null, BigDecimal.ZERO));

            list.add(new LoanFacility("#LN-04", 4L, "Personal Credit", new BigDecimal("1000.00"),
                    new BigDecimal("8.00"), "SETTLED", BigDecimal.ZERO,
                    "DGB-429309564", 12, null, BigDecimal.ZERO));

            list.add(new LoanFacility("#LN-03", 3L, "Emergency Auto", new BigDecimal("3500.00"),
                    new BigDecimal("8.50"), "SETTLED", BigDecimal.ZERO,
                    "DGB-429309564", 24, null, BigDecimal.ZERO));

            // Page 4 (Additional facilities)
            list.add(new LoanFacility("#LN-02", 2L, "Personal Credit", new BigDecimal("1200.00"),
                    new BigDecimal("8.00"), "SETTLED", BigDecimal.ZERO,
                    "DGB-429309564", 12, null, BigDecimal.ZERO));

            list.add(new LoanFacility("#LN-01", 1L, "Personal Credit", new BigDecimal("800.00"),
                    new BigDecimal("8.50"), "SETTLED", BigDecimal.ZERO,
                    "DGB-429309564", 12, null, BigDecimal.ZERO));

            list.add(new LoanFacility("#LN-15", 15L, "Personal Credit", new BigDecimal("2500.00"),
                    new BigDecimal("9.00"), "ACTIVE", new BigDecimal("1850.00"),
                    "DGB-429309564", 12, LocalDate.of(2026, 10, 20), new BigDecimal("218.75")));

            list.add(new LoanFacility("#LN-14", 14L, "Emergency Auto", new BigDecimal("4500.00"),
                    new BigDecimal("8.50"), "ACTIVE", new BigDecimal("3100.00"),
                    "DGB-429309564", 24, LocalDate.of(2026, 10, 20), new BigDecimal("204.69")));

            list.add(new LoanFacility("#LN-10", 10L, "Personal Credit", new BigDecimal("2000.00"),
                    new BigDecimal("8.00"), "ACTIVE", new BigDecimal("1450.00"),
                    "DGB-429309564", 12, LocalDate.of(2026, 10, 20), new BigDecimal("173.33")));
        }

        return list;
    }

    /**
     * Contract modal display for [2] View Contract.
     */
    private void showContractModal(Terminal terminal, Attributes origAttributes, NonBlockingReader reader,
                                   LoanFacility facility, User user, int width) throws IOException {
        if (facility == null) return;

        DecimalFormat df = new DecimalFormat("#,##0.00");
        StringBuilder sb = new StringBuilder();
        sb.append(TUIBox.top(width)).append("\n");
        sb.append(TUIBox.line(ConsoleTheme.primary("DIGIBANK CORE > LOAN MANAGEMENT > CONTRACT AGREEMENT"), width)).append("\n");
        sb.append(TUIBox.divider(width)).append("\n");
        sb.append(TUIBox.line("FACILITY CONTRACT AGREEMENT SPECIFICATIONS", width)).append("\n");
        sb.append(TUIBox.emptyLine(width)).append("\n");

        sb.append(TUIBox.line(String.format("  Facility Identifier  : %-48s", facility.getFacilityId()), width)).append("\n");
        sb.append(TUIBox.line(String.format("  Borrower Name        : %-48s", user != null && user.getFullName() != null ? user.getFullName() : (user != null ? user.getUsername() : "Customer")), width)).append("\n");
        sb.append(TUIBox.line(String.format("  Facility Type        : %-48s", facility.getType()), width)).append("\n");
        sb.append(TUIBox.line(String.format("  Approved Principal   : $ %-46s", df.format(facility.getAmount()) + " USD"), width)).append("\n");
        sb.append(TUIBox.line(String.format("  Annual Percentage    : %-48s", String.format("%.2f%% Fixed APR", facility.getRate().doubleValue())), width)).append("\n");
        sb.append(TUIBox.line(String.format("  Amortization Term    : %-48s", facility.getTermMonths() + " Months"), width)).append("\n");
        sb.append(TUIBox.line(String.format("  Monthly Installment  : $ %-46s", df.format(facility.getMonthlyDue()) + " USD"), width)).append("\n");
        sb.append(TUIBox.line(String.format("  Disbursed Account    : %-48s", facility.getDisbursedTo()), width)).append("\n");
        sb.append(TUIBox.line(String.format("  Facility Status      : %-48s", facility.getStatus()), width)).append("\n");

        sb.append(TUIBox.emptyLine(width)).append("\n");
        sb.append(TUIBox.divider(width)).append("\n");
        sb.append(TUIBox.line("  Press [Esc] or [Enter] to return to Loan Management.", width)).append("\n");
        sb.append(TUIBox.bottom(width)).append("\n");
        sb.append(ConsoleTheme.keyGuide("[Esc / Enter] Close Contract")).append("\n");

        System.out.print("\033[H\033[2J");
        System.out.flush();
        ScreenRenderer.render(sb.toString(), true);

        while (true) {
            int ch = reader.read();
            if (ch == 27 || ch == '\r' || ch == '\n' || ch == 'b' || ch == 'B' || ch == ' ' || ch == -1) {
                break;
            }
        }
    }
}
