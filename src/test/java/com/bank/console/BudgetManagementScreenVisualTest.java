package com.bank.console;

import com.bank.console.components.TUIBox;
import com.bank.console.components.TUILayout;
import com.bank.console.screens.BudgetManagementScreen;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.text.DecimalFormat;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Visual verification test for the dedicated Monthly Expense Budgets screen.
 * Validates strict 82-column grid conformity, formatProgress behavior,
 * metrics formatting, and header/footer styling.
 */
public class BudgetManagementScreenVisualTest {

    @Test
    @DisplayName("formatProgress clamps percent at >999% and caps visual bar to 12 blocks")
    void testFormatProgress() {
        // Case 1: Over-budget food spending ($10,000 spent vs $200 limit -> 5000% > 999%)
        String overProgress = BudgetManagementScreen.formatProgress(10000.00, 200.00);
        assertTrue(overProgress.contains(">999%"), "Percentage over 999.9% must be clamped to >999%");
        assertTrue(overProgress.contains("\033[31m"), "Over-budget bar must be colored red (\\033[31m)");
        assertTrue(overProgress.contains("████████████"), "Over-budget bar must have all 12 blocks filled");
        assertEquals(20, TUIBox.stripAnsi(overProgress).length(), "Stripped progress length for >999% must be 20 chars");

        // Case 2: Under-budget entertainment spending ($80 spent vs $300 limit -> 26.7%)
        String underProgress = BudgetManagementScreen.formatProgress(80.00, 300.00);
        assertTrue(underProgress.contains("26.7%"), "Percentage must format as 26.7%");
        assertTrue(underProgress.contains("\033[32m"), "Under-budget bar must be colored green (\\033[32m)");
        assertTrue(underProgress.contains("███░░░░░░░░░"), "Bar for 26.7% must have 3 filled blocks and 9 empty blocks");
        assertEquals(21, TUIBox.stripAnsi(underProgress).length(), "Stripped progress length for 26.7% must be 21 chars");

        // Case 3: Zero limit / zero spent
        String zeroProgress = BudgetManagementScreen.formatProgress(0.0, 0.0);
        assertTrue(zeroProgress.contains("0.0%"));
        assertTrue(zeroProgress.contains("░░░░░░░░░░░░"));
    }

    @Test
    @DisplayName("renderMetrics produces two-column metrics lines strictly adhering to 82 columns")
    void testRenderMetrics82Columns() {
        int width = TUILayout.APP_WIDTH;
        assertEquals(82, width);

        StringBuilder sb = new StringBuilder();
        BudgetManagementScreen.renderMetrics(sb, width, 500.00, 10080.00, 2016.0, 2, 1);

        String[] lines = sb.toString().split("\n");
        for (String line : lines) {
            assertEquals(82, TUIBox.visibleLength(line), "Metric line must be strictly 82 columns: " + line);
        }

        String stripped = TUIBox.stripAnsi(sb.toString());
        assertTrue(stripped.contains("Total Monthly Cap : $ 500.00 USD"));
        assertTrue(stripped.contains("Active Categories : 2"));
        assertTrue(stripped.contains("Total MTD Spent   : $ 10,080.00 USD (2016.0%)"));
        assertTrue(stripped.contains("Over-budget Items : 1 Cat"));
    }

    @Test
    @DisplayName("Table header, separator, and data rows strictly conform to 82 columns")
    void testTableRowsAndHeader82Columns() {
        int width = TUILayout.APP_WIDTH;
        assertEquals(82, width);
        DecimalFormat df = new DecimalFormat("#,##0.00");

        // Header
        String header = "CATEGORY       BUDGET LIMIT        SPENT    REMAINING     USAGE PROGRESS      ";
        assertEquals(78, header.length());
        String boxedHeader = TUIBox.line(header, width);
        assertEquals(82, TUIBox.visibleLength(boxedHeader));

        // Separator
        String separator = "─".repeat(78);
        String boxedSeparator = TUIBox.line(separator, width);
        assertEquals(82, TUIBox.visibleLength(boxedSeparator));

        // Row 1: Food ($200 limit, $10,000 spent, -$9,800 remaining, >999%)
        String prefix1 = " ";
        String cat1 = String.format("%-16s", "Food");
        String limit1 = String.format("$ %9s", df.format(200.00));
        String spent1 = String.format("$ %9s", df.format(10000.00));
        String rem1 = String.format("-$ %8s", df.format(9800.00));
        String prog1 = BudgetManagementScreen.formatProgress(10000.00, 200.00);
        int progPad1 = Math.max(0, 22 - TUIBox.stripAnsi(prog1).length());
        String row1 = prefix1 + cat1 + limit1 + "  " + spent1 + "  " + rem1 + "  " + prog1 + " ".repeat(progPad1);
        assertEquals(78, TUIBox.stripAnsi(row1).length());
        String boxedRow1 = TUIBox.line(row1, width);
        assertEquals(82, TUIBox.visibleLength(boxedRow1));
        assertTrue(TUIBox.stripAnsi(boxedRow1).contains("Food"));
        assertTrue(TUIBox.stripAnsi(boxedRow1).contains("$    200.00"));
        assertTrue(TUIBox.stripAnsi(boxedRow1).contains("$ 10,000.00"));
        assertTrue(TUIBox.stripAnsi(boxedRow1).contains("-$ 9,800.00"));

        // Row 2: Entertainment (selected, $300 limit, $80 spent, $220 remaining, 26.7%)
        String prefix2 = "▸";
        String cat2 = String.format("%-16s", "Entertainment");
        String limit2 = String.format("$ %9s", df.format(300.00));
        String spent2 = String.format("$ %9s", df.format(80.00));
        String rem2 = String.format(" $ %8s", df.format(220.00));
        String prog2 = BudgetManagementScreen.formatProgress(80.00, 300.00);
        int progPad2 = Math.max(0, 22 - TUIBox.stripAnsi(prog2).length());
        String row2 = prefix2 + cat2 + limit2 + "  " + spent2 + "  " + rem2 + "  " + prog2 + " ".repeat(progPad2);
        assertEquals(78, TUIBox.stripAnsi(row2).length());
        String boxedRow2 = TUIBox.line(row2, width);
        assertEquals(82, TUIBox.visibleLength(boxedRow2));
        assertTrue(TUIBox.stripAnsi(boxedRow2).contains("▸Entertainment"));
        assertTrue(TUIBox.stripAnsi(boxedRow2).contains("$    300.00"));
        assertTrue(TUIBox.stripAnsi(boxedRow2).contains("$     80.00"));
        assertTrue(TUIBox.stripAnsi(boxedRow2).contains("$   220.00"));
    }

    @Test
    @DisplayName("Header with action button strictly adheres to 82 columns")
    void testHeaderWithActionButton82Columns() {
        int width = TUILayout.APP_WIDTH;
        String headerTitle = "ACTIVE BUDGETARY LIMITS (SEPTEMBER 2026)";
        String btn = "\033[36m[+N] CREATE NEW BUDGET\033[0m";
        int leftPad = Math.max(1, 78 - headerTitle.length() - TUIBox.stripAnsi(btn).length() - 4);
        String headerContent = headerTitle + " ".repeat(leftPad) + btn + "    ";
        assertEquals(78, TUIBox.stripAnsi(headerContent).length());

        String boxedHeader = TUIBox.line(headerContent, width);
        assertEquals(82, TUIBox.visibleLength(boxedHeader));
        assertTrue(boxedHeader.contains("[+N] CREATE NEW BUDGET"));
    }
}
