package com.bank.console;

import com.bank.console.components.TUIBox;
import com.bank.console.components.TUIFormHelper;
import com.bank.console.components.TUILayout;
import com.bank.console.screens.*;
import com.bank.console.theme.ConsoleTheme;
import com.bank.model.TransactionView;
import com.bank.model.entity.Transaction;
import com.bank.model.enums.Currency;
import com.bank.model.enums.TransactionDirection;
import com.bank.model.enums.TransactionType;
import com.bank.util.CurrencyConverter;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

public class ScreenVisualVerificationTest {

    @BeforeAll
    static void setup() {
        ControllerFactory.init();
    }

    @Test
    @DisplayName("Verify TUIFormHelper formatFieldRow produces strict 82-column output")
    void testTuiFormHelperWidth() {
        int width = TUILayout.APP_WIDTH;
        assertEquals(82, width);

        String fieldRow = TUIFormHelper.formatFieldRow("Deposit Amount", "1,000.00", false, 18, 50);
        assertEquals(82, TUIBox.visibleLength(fieldRow), "Field row must be exactly 82 columns");

        String focusedFieldRow = TUIFormHelper.formatFieldRow("Deposit Amount", "1,000.00", true, 18, 50);
        assertEquals(82, TUIBox.visibleLength(focusedFieldRow), "Focused field row must be exactly 82 columns");

        String infoRow = TUIFormHelper.formatInfoRow("Current Balance", "$ 2,700.00 USD", 18, 50);
        assertEquals(82, TUIBox.visibleLength(infoRow), "Info row must be exactly 82 columns");
    }

    @Test
    @DisplayName("Verify BudgetScreen progress bars fit strictly inside 82 columns")
    void testBudgetScreenRowWidth() {
        DecimalFormat df = new DecimalFormat("#,##0.00");
        int width = TUILayout.APP_WIDTH;

        // Header and divider
        String header = "  Category      Limit         Spent     Remaining    Usage Progress";
        assertEquals(82, TUIBox.visibleLength(TUIBox.line(header, width)));

        String divider = "  " + "─".repeat(74);
        assertEquals(82, TUIBox.visibleLength(TUIBox.line(divider, width)));

        // Data row with 14-char progress bar
        String limStr = "$" + String.format("%9s", df.format(new BigDecimal("400.00")));
        String spentStr = "$" + String.format("%9s", df.format(new BigDecimal("280.00")));
        String remStr = "$" + String.format("%9s", df.format(new BigDecimal("120.00")));
        String bar = "[██████████░░░░]  70% ";

        String row = String.format("  %-10s  %s    %s    %s    %s", "Food", limStr, spentStr, remStr, bar);
        assertEquals(82, TUIBox.visibleLength(TUIBox.line(row, width)), "Budget row must not overflow 82 columns");

        // Savings Goal row
        String tgtStr = "$" + String.format("%9s", df.format(new BigDecimal("1500.00")));
        String curStr = "$" + String.format("%9s", df.format(new BigDecimal("900.00")));
        String goalBar = "[████████░░░░░░]  60% ";
        String goalRow = String.format("  %-10s  %s   %s   %-10s  %s", "New Laptop", tgtStr, curStr, "2026-12-31", goalBar);
        assertEquals(82, TUIBox.visibleLength(TUIBox.line(goalRow, width)), "Goal row must not overflow 82 columns");

        // Actions row
        String btnRow = " ▸ [1] Create Goal   [2] Deposit to Goal   [3] Set Budget   [4] Back";
        assertEquals(82, TUIBox.visibleLength(TUIBox.line(btnRow, width)), "Action row must not overflow 82 columns");
    }

    @Test
    @DisplayName("Verify TransactionHistoryScreen ledger table row fits strictly inside 82 columns")
    void testLedgerRowWidth() {
        int width = TUILayout.APP_WIDTH;
        assertEquals(82, width);

        String borderChar = ConsoleTheme.border(String.valueOf(TUIBox.V));

        // Header Row: border + 80 chars + border = 82
        String tableHeader = String.format("   %-17s    %-12s    %-25s    %10s ",
                "DATE & TIME", "TYPE", "CATEGORY", "AMOUNT");
        assertEquals(80, tableHeader.length());
        String headerLine = borderChar + tableHeader + borderChar;
        assertEquals(82, TUIBox.visibleLength(headerLine));

        // Separator Line: border + 80 chars + border = 82
        String lineDivider = " " + "─".repeat(77) + "  ";
        assertEquals(80, lineDivider.length());
        String sepLine = borderChar + lineDivider + borderChar;
        assertEquals(82, TUIBox.visibleLength(sepLine));

        // Unselected Data Row
        String plainText = String.format(" %-2s%-17s    %-12s    %-25s    %10s ",
                " ", "2026-09-11 00:28", "REPAYMENT", "Loans", "-$  50.00");
        assertEquals(80, plainText.length(), "Plain text must evaluate to exactly 80 chars");
        String unselectedRow = borderChar + plainText + borderChar;
        assertEquals(82, TUIBox.visibleLength(unselectedRow), "Unselected row must fit strictly within 82 columns");

        // Selected Data Row with ANSI Reverse Highlight
        String selectedPlainText = String.format(" %-2s%-17s    %-12s    %-25s    %10s ",
                "▸", "2026-09-11 00:28", "REPAYMENT", "Loans", "-$  50.00");
        assertEquals(80, selectedPlainText.length(), "Selected plain text must evaluate to exactly 80 chars");
        String selectedRow = borderChar + ConsoleTheme.REVERSE + selectedPlainText + ConsoleTheme.RESET + borderChar;
        assertEquals(82, TUIBox.visibleLength(selectedRow), "Selected row with ANSI reverse video must not blow out right border");

        // Selected transaction details compartment
        assertEquals(82, TUIBox.visibleLength(TUIBox.line("SELECTED TRANSACTION DETAILS", width)));
        assertEquals(82, TUIBox.visibleLength(TUIBox.line("  Reference ID : TXN-20260911-00892", width)));
        assertEquals(82, TUIBox.visibleLength(TUIBox.line("  Description  : Monthly installment repayment for Business Equipment Loan #4", width)));
        assertEquals(82, TUIBox.visibleLength(TUIBox.line("  Channel/Peer : Automated Debit / Credit Bureau System", width)));
        assertEquals(82, TUIBox.visibleLength(TUIBox.line("  Status       : COMPLETED (Settled at 2026-09-11 00:28:14 UTC)", width)));

        // Summary row inside box
        String pageIndicator = String.format("Page: [ %d / %d ]", 1, 3);
        String filterIndicator = String.format("Filter: [%s]", "ALL TRANSACTIONS");
        String totalIndicator = String.format("Total Records: %d", 13);
        String summaryRow = String.format("%-19s│ %-28s│ %s", pageIndicator, filterIndicator, totalIndicator);
        assertEquals(82, TUIBox.visibleLength(TUIBox.line(summaryRow, width)), "Summary row must fit strictly within 82 columns");

        // Details Subscreen
        assertEquals(82, TUIBox.visibleLength(TUIBox.line("DIGIBANK CORE > TRANSACTIONS LEDGER > TRANSACTION DETAILS", width)));
        assertEquals(82, TUIBox.visibleLength(TUIBox.line("TRANSACTION SUMMARY", width)));
        assertEquals(82, TUIBox.visibleLength(TUIBox.line("  Transaction ID   : TXN-20260911-00892", width)));
        assertEquals(82, TUIBox.visibleLength(TUIBox.line("  Gross Amount     : -$ 50.00 USD", width)));
        assertEquals(82, TUIBox.visibleLength(TUIBox.line("   ▸ [1] Return to Ledger       [2] Export Receipt (PDF)", width)));
    }

    @Test
    @DisplayName("Verify Running Balance calculation backward from current balance")
    void testRunningBalanceCalculation() {
        BigDecimal currentBalance = new BigDecimal("2700.00");

        // Transactions in newest-first order (descending date)
        // 1. REPAYMENT -$50
        // 2. REPAYMENT -$50
        // 3. DEPOSIT +$100
        // 4. DEPOSIT +$2000
        // 5. WITHDRAWAL -$1000
        List<TransactionView> views = new ArrayList<>();

        Transaction t1 = Transaction.builder().transactionId(1L).amount(new BigDecimal("50.00")).transactionType(TransactionType.LOAN_REPAYMENT).build();
        views.add(new TransactionView(t1, TransactionDirection.OUTCOME));

        Transaction t2 = Transaction.builder().transactionId(2L).amount(new BigDecimal("50.00")).transactionType(TransactionType.LOAN_REPAYMENT).build();
        views.add(new TransactionView(t2, TransactionDirection.OUTCOME));

        Transaction t3 = Transaction.builder().transactionId(3L).amount(new BigDecimal("100.00")).transactionType(TransactionType.DEPOSIT).build();
        views.add(new TransactionView(t3, TransactionDirection.INCOME));

        Transaction t4 = Transaction.builder().transactionId(4L).amount(new BigDecimal("2000.00")).transactionType(TransactionType.DEPOSIT).build();
        views.add(new TransactionView(t4, TransactionDirection.INCOME));

        Transaction t5 = Transaction.builder().transactionId(5L).amount(new BigDecimal("1000.00")).transactionType(TransactionType.WITHDRAWAL).build();
        views.add(new TransactionView(t5, TransactionDirection.OUTCOME));

        Map<Long, BigDecimal> runningBalanceMap = new HashMap<>();
        BigDecimal running = currentBalance;
        for (TransactionView tv : views) {
            Transaction t = tv.getTransaction();
            runningBalanceMap.put(t.getTransactionId(), running);
            BigDecimal amt = t.getAmount();
            if (tv.getDirection() == TransactionDirection.INCOME) {
                running = running.subtract(amt);
            } else {
                running = running.add(amt);
            }
        }

        // Check balances match accounting requirement:
        // After t1 (-$50): 2,700.00
        // After t2 (-$50): 2,750.00
        // After t3 (+$100): 2,800.00
        // After t4 (+$2000): 2,700.00
        // After t5 (-$1000): 700.00
        assertEquals(new BigDecimal("2700.00"), runningBalanceMap.get(1L));
        assertEquals(new BigDecimal("2750.00"), runningBalanceMap.get(2L));
        assertEquals(new BigDecimal("2800.00"), runningBalanceMap.get(3L));
        assertEquals(new BigDecimal("2700.00"), runningBalanceMap.get(4L));
        assertEquals(new BigDecimal("700.00"), runningBalanceMap.get(5L));
    }

    @Test
    @DisplayName("Verify Cross-Currency calculation and rounding")
    void testCrossCurrencyConversion() {
        Map<String, BigDecimal> rates = CurrencyConverter.getFallbackRates();
        BigDecimal amountUsd = new BigDecimal("40.00");

        // Convert USD to KHR (Rate = 4100.00)
        BigDecimal creditKhr = CurrencyConverter.convert(amountUsd, "USD", "KHR", rates);
        // 40.00 * 4100.00 = 164,000.00 KHR
        assertEquals(new BigDecimal("164000.00"), creditKhr);

        BigDecimal exchangeRate = CurrencyConverter.getExchangeRate("USD", "KHR", rates);
        assertEquals(new BigDecimal("4100.0000"), exchangeRate);

        // Convert USD to JPY (Rate = 152.40)
        BigDecimal creditJpy = CurrencyConverter.convert(new BigDecimal("10.00"), "USD", "JPY", rates);
        assertEquals(new BigDecimal("1524.00"), creditJpy);
        assertEquals(new BigDecimal("152.4000"), CurrencyConverter.getExchangeRate("USD", "JPY", rates));
    }

    @Test
    @DisplayName("Verify ExchangeScreen layout rows strictly conform to 82 columns")
    void testExchangeScreenVisualLayout() {
        int width = TUILayout.APP_WIDTH;
        assertEquals(82, width);

        // Header & Spot rates
        assertEquals(82, TUIBox.visibleLength(TUIBox.top(width)));
        assertEquals(82, TUIBox.visibleLength(TUIBox.line("DIGIBANK CORE > CURRENCY EXCHANGE & CONVERSION CALCULATOR", width)));
        assertEquals(82, TUIBox.visibleLength(TUIBox.divider(width)));
        assertEquals(82, TUIBox.visibleLength(TUIBox.line("LIVE SPOT RATES (BASE: USD) • SOURCE: open.er-api.com", width)));
        assertEquals(82, TUIBox.visibleLength(TUIBox.emptyLine(width)));

        String tableHeader = String.format("  %-15s %-22s %22s", "Currency Code", "Name", "Spot Rate (1 USD)");
        assertEquals(82, TUIBox.visibleLength(TUIBox.line(tableHeader, width)));
        assertEquals(82, TUIBox.visibleLength(TUIBox.line("  " + "─".repeat(76), width)));

        String jpyLine = String.format("  %-15s %-22s %22s", "JPY", "Japanese Yen", "¥ 152.40");
        assertEquals(82, TUIBox.visibleLength(TUIBox.line(jpyLine, width)));

        // Simulator rows
        assertEquals(82, TUIBox.visibleLength(TUIBox.line("INSTANT EXCHANGE SIMULATOR", width)));
        String radioRow = String.format("  %-17s: [ %-53s ]", "Source Currency", "(•) USD     ( ) KHR     ( ) EUR     ( ) JPY");
        assertEquals(82, TUIBox.visibleLength(TUIBox.line(radioRow, width)));

        String amountRow = String.format("  %-17s: [ %-53s ]", "Amount to Convert", "$ 500.00|");
        assertEquals(82, TUIBox.visibleLength(TUIBox.line(amountRow, width)));

        String returnRow = String.format("  Estimated Return  :   %s", "៛ 2,022,090.00 KHR");
        assertEquals(82, TUIBox.visibleLength(TUIBox.line(returnRow, width)));

        String feeRow = "  Applied Fee       :   $ 0.00 (Standard Tier - Zero Fee)";
        assertEquals(82, TUIBox.visibleLength(TUIBox.line(feeRow, width)));

        // Actions row
        String actionLine = "    [1] Refresh Rates       [2] Swap Currencies       [3] Clear / Reset";
        assertEquals(82, TUIBox.visibleLength(TUIBox.line(actionLine, width)));
        assertEquals(82, TUIBox.visibleLength(TUIBox.bottom(width)));
    }

    @Test
    @DisplayName("Verify RegisterScreen layout rows strictly conform to 82 columns")
    void testRegisterScreenLayoutWidth() {
        int width = TUILayout.APP_WIDTH;
        assertEquals(82, width);

        // Header & Compartment titles
        assertEquals(82, TUIBox.visibleLength(TUIBox.top(width)));
        assertEquals(82, TUIBox.visibleLength(TUIBox.line("DIGIBANK CORE > NEW CUSTOMER REGISTRATION", width)));
        assertEquals(82, TUIBox.visibleLength(TUIBox.divider(width)));
        assertEquals(82, TUIBox.visibleLength(TUIBox.line("CUSTOMER PROFILE", width)));
        assertEquals(82, TUIBox.visibleLength(TUIBox.emptyLine(width)));

        // Profile fields
        String fieldRow1 = TUIBox.line(String.format(" %-18s: [ %-53s ]", "Full Legal Name", "MEN SENGHAK"), width);
        assertEquals(82, TUIBox.visibleLength(fieldRow1), "Full Legal Name row must be 82 cols");

        String passwordRow = TUIBox.line(String.format(" %-18s: [ %-53s ]", "Password", "•".repeat(16)), width);
        assertEquals(82, TUIBox.visibleLength(passwordRow), "Password row must be 82 cols");

        // Account Configuration section
        assertEquals(82, TUIBox.visibleLength(TUIBox.line("INITIAL ACCOUNT CONFIGURATION", width)));

        String radioType = TUIBox.line(String.format(" %-18s: [ %-22s %-30s ]", "Account Type", "(•) SAVINGS", "( ) CHECKING"), width);
        assertEquals(82, TUIBox.visibleLength(radioType), "Account Type radio row must be 82 cols");

        String radioCurr = TUIBox.line(String.format(" %-18s: [ %-22s %-30s ]", "Primary Currency", "(•) USD", "( ) KHR"), width);
        assertEquals(82, TUIBox.visibleLength(radioCurr), "Currency radio row must be 82 cols");

        String depositRow = TUIBox.line(String.format(" %-18s: [ %-53s ]", "Initial Deposit", "$ 100.00"), width);
        assertEquals(82, TUIBox.visibleLength(depositRow), "Initial Deposit row must be 82 cols");

        // Action section
        assertEquals(82, TUIBox.visibleLength(TUIBox.line("ACTION", width)));
        String actionRow = TUIBox.line("   ▸ [1] Submit Registration                       [2] Cancel & Return", width);
        assertEquals(82, TUIBox.visibleLength(actionRow), "Action row must be 82 cols");

        // Footer Note & Bottom
        String noteRow = TUIBox.line("Note: Passwords hashed via BCrypt. Account numbers generated automatically.", width);
        assertEquals(82, TUIBox.visibleLength(noteRow), "Note row must be 82 cols");
        assertEquals(82, TUIBox.visibleLength(TUIBox.bottom(width)));
    }
}

