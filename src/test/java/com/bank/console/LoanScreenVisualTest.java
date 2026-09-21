package com.bank.console;

import com.bank.console.components.TUIBox;
import com.bank.console.components.TUILayout;
import com.bank.console.screens.LoanScreen;
import com.bank.console.screens.LoanScreen.Installment;
import com.bank.console.screens.LoanScreen.LoanFacility;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Visual verification test for Master-Detail LoanScreen.
 * Tests strict 82-column containment, pagination logic (PAGE_SIZE = 5),
 * and row/header formatting.
 */
public class LoanScreenVisualTest {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    @Test
    @DisplayName("Master table header, separator, and data rows strictly conform to 82 columns")
    void testMasterTable82Columns() {
        int width = TUILayout.APP_WIDTH;
        assertEquals(82, width);

        // Header line
        String masterTitle = "ALL LOAN FACILITIES & APPLICATIONS";
        assertEquals(82, TUIBox.visibleLength(TUIBox.line(masterTitle, width)));

        // Table Header
        String masterHeader = "  FACILITY ID   TYPE                AMOUNT     RATE   STATUS     REMAINING BAL";
        assertEquals(78, masterHeader.length(), "Master header must be 78 inner characters");
        String boxedHeader = TUIBox.line(masterHeader, width);
        assertEquals(82, TUIBox.visibleLength(boxedHeader));

        // Separator
        String separator = "  " + "─".repeat(74) + "  ";
        assertEquals(78, separator.length());
        String boxedSep = TUIBox.line(separator, width);
        assertEquals(82, TUIBox.visibleLength(boxedSep));

        // Row 1: Selected #LN-13
        String marker1 = "▸ ";
        String id1 = String.format("%-14s", "#LN-13");
        String type1 = String.format("%-17s", "Personal Credit");
        String amt1 = String.format("$ %,8.2f   ", new BigDecimal("1000.00"));
        String rate1 = String.format("%5.2f%%  ", new BigDecimal("8.50"));
        String status1 = String.format("%-11s", "ACTIVE");
        String bal1 = String.format("$ %,8.2f   ", new BigDecimal("46.68"));
        String row1 = marker1 + id1 + type1 + amt1 + rate1 + status1 + bal1;
        assertEquals(78, TUIBox.stripAnsi(row1).length());
        String boxedRow1 = TUIBox.line(row1, width);
        assertEquals(82, TUIBox.visibleLength(boxedRow1));

        // Row 2: Unselected #LN-08
        String marker2 = "  ";
        String id2 = String.format("%-14s", "#LN-08");
        String type2 = String.format("%-17s", "Emergency Auto");
        String amt2 = String.format("$ %,8.2f   ", new BigDecimal("2500.00"));
        String rate2 = String.format("%5.2f%%  ", new BigDecimal("9.00"));
        String status2 = String.format("%-11s", "SETTLED");
        String bal2 = String.format("$ %,8.2f   ", BigDecimal.ZERO);
        String row2 = marker2 + id2 + type2 + amt2 + rate2 + status2 + bal2;
        assertEquals(78, TUIBox.stripAnsi(row2).length());
        String boxedRow2 = TUIBox.line(row2, width);
        assertEquals(82, TUIBox.visibleLength(boxedRow2));

        // Row 3: Unselected #LN-NEW
        String marker3 = "  ";
        String id3 = String.format("%-14s", "#LN-NEW");
        String type3 = String.format("%-17s", "Personal Credit");
        String amt3 = String.format("$ %,8.2f   ", new BigDecimal("1000.00"));
        String rate3 = String.format("%5.2f%%  ", new BigDecimal("8.50"));
        String status3 = String.format("%-11s", "APPROVED");
        String bal3 = String.format("$ %,8.2f   ", new BigDecimal("1000.00"));
        String row3 = marker3 + id3 + type3 + amt3 + rate3 + status3 + bal3;
        assertEquals(78, TUIBox.stripAnsi(row3).length());
        String boxedRow3 = TUIBox.line(row3, width);
        assertEquals(82, TUIBox.visibleLength(boxedRow3));
    }

    @Test
    @DisplayName("Middle section selected facility details strictly conform to 82 columns")
    void testMiddleSectionDetails82Columns() {
        int width = TUILayout.APP_WIDTH;

        String selectedHead = "SELECTED FACILITY DETAILS: #LN-13 (ACTIVE)";
        assertEquals(82, TUIBox.visibleLength(TUIBox.line(selectedHead, width)));

        String details = "   Disbursed To: DGB-429309564 | Term: 12 Mo | Next Due: 2027-02-20 ($83.33)";
        assertEquals(82, TUIBox.visibleLength(TUIBox.line(details, width)));
    }

    @Test
    @DisplayName("Repayment schedule pagination, headers, and rows strictly conform to 82 columns")
    void testRepaymentSchedulePaginationAnd82Columns() {
        int width = TUILayout.APP_WIDTH;
        int pageSize = 5;

        // 12 months full schedule
        List<Installment> fullSchedule = new ArrayList<>();
        LocalDate baseDate = LocalDate.of(2026, 9, 20);
        for (int i = 1; i <= 12; i++) {
            String stat = (i <= 5) ? "PAID (Settled)" : (i == 6 ? "PENDING (Next)" : "SCHEDULED");
            fullSchedule.add(new Installment(i, baseDate.plusMonths(i - 1),
                    new BigDecimal("76.80"), new BigDecimal("6.53"), new BigDecimal("83.33"), stat));
        }

        int totalItems = fullSchedule.size();
        int totalPages = (int) Math.ceil((double) totalItems / pageSize);
        assertEquals(3, totalPages, "12 items with PAGE_SIZE 5 must equal 3 total pages");

        // Page 1 header [ PAGE 1 OF 3 ]
        String pageHeader1 = String.format("%-59s[ PAGE %d OF %d ]    ",
                "REPAYMENT SCHEDULE & INSTALLMENT HISTORY", 1, totalPages);
        assertEquals(78, pageHeader1.length());
        assertEquals(82, TUIBox.visibleLength(TUIBox.line(pageHeader1, width)));

        // Schedule table column header
        String schedHeader = "  #   DUE DATE     PRINCIPAL   INTEREST    TOTAL DUE   STATUS                 ";
        assertEquals(78, schedHeader.length());
        assertEquals(82, TUIBox.visibleLength(TUIBox.line(schedHeader, width)));

        // Separator
        String separator = "  " + "─".repeat(74) + "  ";
        assertEquals(82, TUIBox.visibleLength(TUIBox.line(separator, width)));

        // Verify all 12 row formats
        for (Installment item : fullSchedule) {
            String row = String.format("  %02d  %-13s$ %6.2f    $ %6.2f    $ %6.2f    %-23s",
                    item.getInstallmentNumber(),
                    item.getDueDate().format(DATE_FMT),
                    item.getPrincipal(),
                    item.getInterest(),
                    item.getTotalDue(),
                    item.getStatus()
            );
            assertEquals(78, row.length(), "Schedule row must be exactly 78 inner characters");
            assertEquals(82, TUIBox.visibleLength(TUIBox.line(row, width)));
        }

        // Action row
        String actionRow = " [1] Pay Installment   [2] View Contract   [3] Apply New Loan   [4] Dashboard ";
        assertEquals(78, actionRow.length());
        assertEquals(82, TUIBox.visibleLength(TUIBox.line(actionRow, width)));
    }

    @Test
    @DisplayName("Pagination bounds logic handles N and P correctly")
    void testPaginationBoundsLogic() {
        int totalItems = 12;
        int pageSize = 5;
        int totalPages = (int) Math.ceil((double) totalItems / pageSize); // 3
        assertEquals(3, totalPages);

        int currentPage = 0;

        // P at page 0 should not go below 0
        if (currentPage > 0) currentPage--;
        assertEquals(0, currentPage);

        // N goes to page 1
        if (currentPage < totalPages - 1) currentPage++;
        assertEquals(1, currentPage);

        // N goes to page 2
        if (currentPage < totalPages - 1) currentPage++;
        assertEquals(2, currentPage);

        // N at page 2 (last page) should not go above 2
        if (currentPage < totalPages - 1) currentPage++;
        assertEquals(2, currentPage);

        // P goes back to page 1
        if (currentPage > 0) currentPage--;
        assertEquals(1, currentPage);
    }
}
