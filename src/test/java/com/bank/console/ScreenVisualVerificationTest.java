package com.bank.console;

import com.bank.console.components.ConsoleFormatter;
import com.bank.console.components.TUIBox;
import com.bank.console.components.TUIFormHelper;
import com.bank.console.components.TUILayout;
import com.bank.console.screens.*;
import com.bank.console.theme.ConsoleTheme;
import com.bank.model.TransactionView;
import com.bank.model.dto.*;
import com.bank.model.entity.*;
import com.bank.model.enums.*;
import com.bank.util.CurrencyConverter;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.util.*;
import com.bank.model.enums.Currency;

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

    @Test
    @DisplayName("Verify TransferScreen account number normalization")
    void testTransferAccountNumberNormalization() {
        assertEquals("DGB-429309564", TransferScreen.normalizeAccountNumber("429309564"));
        assertEquals("DGB-429309564", TransferScreen.normalizeAccountNumber("dgb-429309564"));
        assertEquals("DGB-429309564", TransferScreen.normalizeAccountNumber("DGB-429309564"));
        assertEquals("DGB-429309564", TransferScreen.normalizeAccountNumber("  429309564  "));
        assertEquals("", TransferScreen.normalizeAccountNumber(""));
        assertEquals("", TransferScreen.normalizeAccountNumber(null));
    }

    @Test
    @DisplayName("Verify TransferScreen form and confirmation layouts strictly conform to 82 columns")
    void testTransferScreenLayoutWidth() {
        int width = TUILayout.APP_WIDTH;
        assertEquals(82, width);

        // Header & Details
        assertEquals(82, TUIBox.visibleLength(TUIBox.top(width)));
        assertEquals(82, TUIBox.visibleLength(TUIBox.line(ConsoleTheme.primary("DIGIBANK CORE > MONEY MOVEMENT > TRANSFER"), width)));
        assertEquals(82, TUIBox.visibleLength(TUIBox.divider(width)));
        assertEquals(82, TUIBox.visibleLength(TUIBox.line("TRANSFER DETAILS", width)));
        assertEquals(82, TUIBox.visibleLength(TUIBox.emptyLine(width)));

        // Field rows
        String srcRow = TUIFormHelper.formatFieldRow("Source Account", "DGB-788635551 (SAVINGS - Bal: $14,400.00 USD)", false, 18, 50);
        assertEquals(82, TUIBox.visibleLength(srcRow));

        String dstRow = TUIFormHelper.formatFieldRow("Destination Acc", "DGB-429309564", false, 18, 50);
        assertEquals(82, TUIBox.visibleLength(dstRow));

        String benRow = TUIFormHelper.formatInfoRow("Beneficiary Name", "KEO SOKHA (Verified)", 18, 50);
        assertEquals(82, TUIBox.visibleLength(benRow));

        String amtRow = TUIFormHelper.formatFieldRow("Transfer Amount", "2,500.00 USD", false, 18, 50);
        assertEquals(82, TUIBox.visibleLength(amtRow));

        String remRow = TUIFormHelper.formatFieldRow("Remark (Optional)", "For your new IPhone 18 Pro Max, babe", false, 18, 50);
        assertEquals(82, TUIBox.visibleLength(remRow));

        String catRow = TUIFormHelper.formatFieldRow("Category", "(6) Bills & Utilities", false, 18, 50);
        assertEquals(82, TUIBox.visibleLength(catRow));

        // Action compartment
        assertEquals(82, TUIBox.visibleLength(TUIBox.divider(width)));
        assertEquals(82, TUIBox.visibleLength(TUIBox.line("  ACTION", width)));
        assertEquals(82, TUIBox.visibleLength(TUIBox.emptyLine(width)));

        String btnRow = TUIBox.line("  ▸ [1] Review & Submit Transfer                  [2] Cancel & Return", width);
        assertEquals(82, TUIBox.visibleLength(btnRow));

        assertEquals(82, TUIBox.visibleLength(TUIBox.bottom(width)));

        // Confirmation Screen
        assertEquals(82, TUIBox.visibleLength(TUIBox.top(width)));
        assertEquals(82, TUIBox.visibleLength(TUIBox.line(ConsoleTheme.primary("DIGIBANK CORE > MONEY MOVEMENT > CONFIRM TRANSFER"), width)));
        assertEquals(82, TUIBox.visibleLength(TUIBox.divider(width)));
        assertEquals(82, TUIBox.visibleLength(TUIBox.line("TRANSACTION VERIFICATION", width)));
        assertEquals(82, TUIBox.visibleLength(TUIBox.emptyLine(width)));
        assertEquals(82, TUIBox.visibleLength(TUIBox.line("  Source Account    : DGB-788635551 (SAVINGS - USD)", width)));
        assertEquals(82, TUIBox.visibleLength(TUIBox.line("  Available Balance : $ 14,400.00 USD", width)));
        assertEquals(82, TUIBox.visibleLength(TUIBox.emptyLine(width)));
        assertEquals(82, TUIBox.visibleLength(TUIBox.line("  Destination Acc   : DGB-429309564 (CHECKING - USD)", width)));
        assertEquals(82, TUIBox.visibleLength(TUIBox.line("  Beneficiary Name  : KEO SOKHA", width)));
        assertEquals(82, TUIBox.visibleLength(TUIBox.emptyLine(width)));
        assertEquals(82, TUIBox.visibleLength(TUIBox.line("  Transfer Amount   : $  2,500.00 USD", width)));
        assertEquals(82, TUIBox.visibleLength(TUIBox.line("  Transfer Fee      : $      0.00 USD (Internal DigiBank Transfer)", width)));
        assertEquals(82, TUIBox.visibleLength(TUIBox.line("  Remaining Balance : $ 11,900.00 USD", width)));
        String catName = "Bills & Utilities";
        String memoStr = "For your new IPhone 18 Pro Max, babe";
        int maxMemoLen = width - 4 - 24 - catName.length() - 4;
        if (maxMemoLen > 3 && memoStr.length() > maxMemoLen) {
            memoStr = memoStr.substring(0, maxMemoLen - 3) + "...";
        }
        String catMemo = String.format("  Category / Memo   : %s / \"%s\"", catName, memoStr);
        assertEquals(82, TUIBox.visibleLength(TUIBox.line(catMemo, width)));
        assertEquals(82, TUIBox.visibleLength(TUIBox.emptyLine(width)));
        assertEquals(82, TUIBox.visibleLength(TUIBox.divider(width)));
        assertEquals(82, TUIBox.visibleLength(TUIBox.line("  CONFIRM EXECUTION", width)));
        assertEquals(82, TUIBox.visibleLength(TUIBox.emptyLine(width)));
        String confBtnRow = TUIBox.line("  ▸ [1] Authorize & Send Transfer                 [2] Back to Edit Details", width);
        assertEquals(82, TUIBox.visibleLength(confBtnRow));
        assertEquals(82, TUIBox.visibleLength(TUIBox.bottom(width)));
    }

    @Test
    @DisplayName("Verify WithdrawScreen layout and action controls strictly conform to 82 columns")
    void testWithdrawScreenLayoutWidth() {
        int width = TUILayout.APP_WIDTH;
        assertEquals(82, width);

        // Header & Details
        assertEquals(82, TUIBox.visibleLength(TUIBox.top(width)));
        assertEquals(82, TUIBox.visibleLength(TUIBox.line(ConsoleTheme.primary("DIGIBANK CORE > CASH OPERATIONS > WITHDRAW AMOUNT"), width)));
        assertEquals(82, TUIBox.visibleLength(TUIBox.divider(width)));
        assertEquals(82, TUIBox.visibleLength(TUIBox.line("WITHDRAWAL DETAILS", width)));
        assertEquals(82, TUIBox.visibleLength(TUIBox.emptyLine(width)));

        String srcRow = TUIFormHelper.formatFieldRow("Source Account", "DGB-788635551 (SAVINGS - USD)", false, 18, 50);
        assertEquals(82, TUIBox.visibleLength(srcRow));
        String balRow = TUIFormHelper.formatInfoRow("Available Balance", "$ 14,400.00 USD", 18, 50);
        assertEquals(82, TUIBox.visibleLength(balRow));

        String amtRow = TUIFormHelper.formatFieldRow("Withdrawal Amount", "$ 2,500.00", false, 18, 50);
        assertEquals(82, TUIBox.visibleLength(amtRow));
        String remRow = TUIFormHelper.formatFieldRow("Remark (Optional)", "To buy new IPhone 18 Pro Max to my girl", false, 18, 50);
        assertEquals(82, TUIBox.visibleLength(remRow));
        String catRow = TUIFormHelper.formatFieldRow("Expense Category", "(4) Entertainment", false, 18, 50);
        assertEquals(82, TUIBox.visibleLength(catRow));

        // Action Compartment & Status Line
        assertEquals(82, TUIBox.visibleLength(TUIBox.divider(width)));
        assertEquals(82, TUIBox.visibleLength(TUIBox.line("  ACTION", width)));
        assertEquals(82, TUIBox.visibleLength(TUIBox.emptyLine(width)));

        String actRow = TUIBox.line("  ▸ [1] Authorize & Dispense Cash                 [2] Cancel & Return", width);
        assertEquals(82, TUIBox.visibleLength(actRow));

        assertEquals(82, TUIBox.visibleLength(TUIBox.divider(width)));
        String statusRow = TUIBox.line("Status: Ready", width);
        assertEquals(82, TUIBox.visibleLength(statusRow));
        assertEquals(82, TUIBox.visibleLength(TUIBox.bottom(width)));
    }

    @Test
    @DisplayName("Verify DepositScreen layout and action controls strictly conform to 82 columns")
    void testDepositScreenLayoutWidth() {
        int width = TUILayout.APP_WIDTH;
        assertEquals(82, width);

        // Header & Details
        assertEquals(82, TUIBox.visibleLength(TUIBox.top(width)));
        assertEquals(82, TUIBox.visibleLength(TUIBox.line(ConsoleTheme.primary("DIGIBANK CORE > CASH OPERATIONS > CASH DEPOSIT"), width)));
        assertEquals(82, TUIBox.visibleLength(TUIBox.divider(width)));
        assertEquals(82, TUIBox.visibleLength(TUIBox.line("DEPOSIT DETAILS", width)));
        assertEquals(82, TUIBox.visibleLength(TUIBox.emptyLine(width)));

        String tgtRow = TUIFormHelper.formatFieldRow("Target Account", "DGB-429309564 (CHECKING - USD)", false, 18, 50);
        assertEquals(82, TUIBox.visibleLength(tgtRow));
        String balRow = TUIFormHelper.formatInfoRow("Current Balance", "$ 2,700.00 USD", 18, 50);
        assertEquals(82, TUIBox.visibleLength(balRow));

        String amtRow = TUIFormHelper.formatFieldRow("Deposit Amount", "$ 1,000.00", false, 18, 50);
        assertEquals(82, TUIBox.visibleLength(amtRow));
        String remRow = TUIFormHelper.formatFieldRow("Remark (Optional)", "yes", false, 18, 50);
        assertEquals(82, TUIBox.visibleLength(remRow));
        String catRow = TUIFormHelper.formatFieldRow("Category", "(0) None / Skip (Default)", false, 18, 50);
        assertEquals(82, TUIBox.visibleLength(catRow));

        // Action Compartment & Status Line
        assertEquals(82, TUIBox.visibleLength(TUIBox.divider(width)));
        assertEquals(82, TUIBox.visibleLength(TUIBox.line("  ACTION", width)));
        assertEquals(82, TUIBox.visibleLength(TUIBox.emptyLine(width)));

        String actRow = TUIBox.line("  ▸ [1] Authorize & Accept Deposit                [2] Cancel & Return", width);
        assertEquals(82, TUIBox.visibleLength(actRow));

        assertEquals(82, TUIBox.visibleLength(TUIBox.divider(width)));
        String statusRow = TUIBox.line("Status: Ready", width);
        assertEquals(82, TUIBox.visibleLength(statusRow));
        assertEquals(82, TUIBox.visibleLength(TUIBox.bottom(width)));
    }

    @Test
    @DisplayName("Verify ConsoleFormatter multi-currency symbol mapping and balance formatting")
    void testConsoleFormatterMultiCurrency() {
        assertEquals("$", ConsoleFormatter.getCurrencySymbol("USD"));
        assertEquals("៛", ConsoleFormatter.getCurrencySymbol("KHR"));
        assertEquals("€", ConsoleFormatter.getCurrencySymbol("EUR"));
        assertEquals("¥", ConsoleFormatter.getCurrencySymbol("JPY"));

        // Currency balances
        assertEquals("$ 4,700.00 USD", ConsoleFormatter.formatAccountBalance(new BigDecimal("4700.00"), "USD"));
        assertEquals("៛ 320,000 KHR", ConsoleFormatter.formatAccountBalance(new BigDecimal("320000"), "KHR"));
        assertEquals("៛ 80.50 KHR", ConsoleFormatter.formatAccountBalance(new BigDecimal("80.50"), "KHR"));
        assertEquals("€ 4,700.00 EUR", ConsoleFormatter.formatAccountBalance(new BigDecimal("4700.00"), "EUR"));
        assertEquals("¥ 12,000 JPY", ConsoleFormatter.formatAccountBalance(new BigDecimal("12000"), "JPY"));

        // Aligned balances for modals (symbol + space + 10-char right-aligned amount + space + code)
        assertEquals("$  4,700.00 USD", ConsoleFormatter.formatAlignedBalance(new BigDecimal("4700.00"), "USD"));
        assertEquals("$ 13,400.00 USD", ConsoleFormatter.formatAlignedBalance(new BigDecimal("13400.00"), "USD"));
        assertEquals("៛   320,000 KHR", ConsoleFormatter.formatAlignedBalance(new BigDecimal("320000"), "KHR"));
    }

    @Test
    @DisplayName("Verify DepositAccountSelectorModal layout conforms strictly to 82 columns and displays aligned multi-currency rows")
    void testDepositAccountSelectorModalLayout() {
        List<AccountDTO> accounts = new ArrayList<>();
        accounts.add(AccountDTO.builder().accountId(1L).accountNumber("DGB-429309564").accountType(AccountType.CHECKING).balance(new BigDecimal("4700.00")).currency(Currency.USD).status(AccountStatus.ACTIVE).build());
        accounts.add(AccountDTO.builder().accountId(2L).accountNumber("DGB-788635551").accountType(AccountType.SAVINGS).balance(new BigDecimal("13400.00")).currency(Currency.USD).status(AccountStatus.ACTIVE).build());
        accounts.add(AccountDTO.builder().accountId(3L).accountNumber("DGB-134672316").accountType(AccountType.SAVINGS).balance(new BigDecimal("320000")).currency(Currency.KHR).status(AccountStatus.ACTIVE).build());

        String modalOutput = DepositAccountSelectorModal.renderContent(accounts, 0, 82);
        String[] lines = modalOutput.split("\n");
        for (int i = 0; i < lines.length - 1; i++) { // Excluding trailing hint footer
            String line = lines[i];
            assertEquals(82, TUIBox.visibleLength(line), "Line " + i + " must be 82 cols: " + line);
        }

        assertTrue(modalOutput.contains("DIGIBANK CORE > CASH OPERATIONS > SELECT DEPOSIT ACCOUNT"));
        assertTrue(modalOutput.contains("AVAILABLE TARGET ACCOUNTS"));
        assertTrue(modalOutput.contains("DGB-429309564"));
        assertTrue(modalOutput.contains("Bal: $  4,700.00 USD"));
        assertTrue(modalOutput.contains("Bal: $ 13,400.00 USD"));
        assertTrue(modalOutput.contains("Bal: ៛   320,000 KHR"));
        assertTrue(modalOutput.contains("Tip: Funds deposited will credit to the selected account immediately."));
    }

    @Test
    @DisplayName("Verify WithdrawAccountSelectorModal layout conforms strictly to 82 columns and displays aligned multi-currency rows")
    void testWithdrawAccountSelectorModalLayout() {
        List<AccountDTO> accounts = new ArrayList<>();
        accounts.add(AccountDTO.builder().accountId(1L).accountNumber("DGB-429309564").accountType(AccountType.CHECKING).balance(new BigDecimal("4700.00")).currency(Currency.USD).status(AccountStatus.ACTIVE).build());
        accounts.add(AccountDTO.builder().accountId(2L).accountNumber("DGB-788635551").accountType(AccountType.SAVINGS).balance(new BigDecimal("13400.00")).currency(Currency.USD).status(AccountStatus.ACTIVE).build());
        accounts.add(AccountDTO.builder().accountId(3L).accountNumber("DGB-134672316").accountType(AccountType.SAVINGS).balance(new BigDecimal("320000")).currency(Currency.KHR).status(AccountStatus.ACTIVE).build());

        String modalOutput = WithdrawAccountSelectorModal.renderContent(accounts, 0, 82);
        String[] lines = modalOutput.split("\n");
        for (int i = 0; i < lines.length - 1; i++) {
            String line = lines[i];
            assertEquals(82, TUIBox.visibleLength(line), "Line " + i + " must be 82 cols: " + line);
        }

        assertTrue(modalOutput.contains("DIGIBANK CORE > CASH OPERATIONS > SELECT WITHDRAWAL ACCOUNT"));
        assertTrue(modalOutput.contains("AVAILABLE SOURCE ACCOUNTS"));
        assertTrue(modalOutput.contains("Available: $  4,700.00 USD"));
        assertTrue(modalOutput.contains("Available: ៛   320,000 KHR"));
        assertTrue(modalOutput.contains("Tip: Ensure selected account has sufficient funds for cash withdrawal."));
    }

    @Test
    @DisplayName("Verify LoginScreen horizontal 3-button action bar and enclosed status bar conform to 82 columns")
    void testLoginScreenLayoutWidth() {
        int width = TUILayout.APP_WIDTH;
        assertEquals(82, width);

        assertEquals(82, TUIBox.visibleLength(TUIBox.top(width)));
        assertEquals(82, TUIBox.visibleLength(TUIBox.line(ConsoleTheme.primary("DIGIBANK CORE > SYSTEM ACCESS GATEWAY"), width)));
        assertEquals(82, TUIBox.visibleLength(TUIBox.divider(width)));
        assertEquals(82, TUIBox.visibleLength(TUIBox.line("AUTHENTICATION CREDENTIALS", width)));
        assertEquals(82, TUIBox.visibleLength(TUIBox.emptyLine(width)));

        String userRow = "  Username / Email  : [ admin                                            ]";
        assertEquals(82, TUIBox.visibleLength(TUIBox.line(userRow, width)));
        String passRow = "  Password          : [ ••••••••                                         ]";
        assertEquals(82, TUIBox.visibleLength(TUIBox.line(passRow, width)));

        assertEquals(82, TUIBox.visibleLength(TUIBox.divider(width)));
        assertEquals(82, TUIBox.visibleLength(TUIBox.line("ACTION", width)));
        assertEquals(82, TUIBox.visibleLength(TUIBox.emptyLine(width)));

        String btnRow = "  ▸ [1] Sign In          [2] Forgot Password?          [3] Back to Welcome";
        assertEquals(82, TUIBox.visibleLength(TUIBox.line(btnRow, width)));

        assertEquals(82, TUIBox.visibleLength(TUIBox.emptyLine(width)));
        assertEquals(82, TUIBox.visibleLength(TUIBox.divider(width)));
        assertEquals(82, TUIBox.visibleLength(TUIBox.line("Status: Ready", width)));
        assertEquals(82, TUIBox.visibleLength(TUIBox.bottom(width)));
    }

    @Test
    @DisplayName("Verify TransferScreen layout with interactive Source Account field conforms to 82 columns")
    void testTransferScreenInteractiveField0() {
        int width = TUILayout.APP_WIDTH;
        assertEquals(82, width);

        assertEquals(82, TUIBox.visibleLength(TUIBox.top(width)));
        assertEquals(82, TUIBox.visibleLength(TUIBox.line(ConsoleTheme.primary("DIGIBANK CORE > MONEY MOVEMENT > TRANSFER"), width)));
        assertEquals(82, TUIBox.visibleLength(TUIBox.divider(width)));
        assertEquals(82, TUIBox.visibleLength(TUIBox.line("TRANSFER DETAILS", width)));
        assertEquals(82, TUIBox.visibleLength(TUIBox.emptyLine(width)));

        String srcRow = TUIFormHelper.formatFieldRow("Source Account", "DGB-429309564 (CHECKING - Bal: $ 4,700.00 USD)", true, 18, 50);
        assertEquals(82, TUIBox.visibleLength(srcRow));
        String dstRow = TUIFormHelper.formatFieldRow("Destination Acc", "DGB-788635551", false, 18, 50);
        assertEquals(82, TUIBox.visibleLength(dstRow));
        String benRow = TUIFormHelper.formatInfoRow("Beneficiary Name", "John Doe (Verified)", 18, 50);
        assertEquals(82, TUIBox.visibleLength(benRow));

        String amtRow = TUIFormHelper.formatFieldRow("Transfer Amount", "$ 200.00 USD", false, 18, 50);
        assertEquals(82, TUIBox.visibleLength(amtRow));
        String remRow = TUIFormHelper.formatFieldRow("Remark (Optional)", "Lunch split", false, 18, 50);
        assertEquals(82, TUIBox.visibleLength(remRow));
        String catRow = TUIFormHelper.formatFieldRow("Category", "(2) Food & Dining", false, 18, 50);
        assertEquals(82, TUIBox.visibleLength(catRow));

        assertEquals(82, TUIBox.visibleLength(TUIBox.divider(width)));
        assertEquals(82, TUIBox.visibleLength(TUIBox.line("  ACTION", width)));
        assertEquals(82, TUIBox.visibleLength(TUIBox.emptyLine(width)));

        String actRow = TUIBox.line("  ▸ [1] Review & Submit Transfer                  [2] Cancel & Return", width);
        assertEquals(82, TUIBox.visibleLength(actRow));
        assertEquals(82, TUIBox.visibleLength(TUIBox.bottom(width)));
    }

    @Test
    @DisplayName("Verify TransferScreen renderInputRow and renderInfoRow strictly conform to 82 columns")
    void testTransferScreenInputRowsExact82Width() {
        int width = TUILayout.APP_WIDTH;
        assertEquals(82, width);

        // Source Account (with hint, unfocused and focused)
        String srcUnfocused = TransferScreen.renderInputRow("Source Account", "DGB-429309564 (CHECKING - $ 8,050.00)", "[Space: Swap]", false, width);
        assertEquals(82, TUIBox.visibleLength(srcUnfocused));
        String srcFocused = TransferScreen.renderInputRow("Source Account", "DGB-429309564 (CHECKING - $ 8,050.00)", "[Space: Swap]", true, width);
        assertEquals(82, TUIBox.visibleLength(srcFocused));

        // Destination Acc (without hint, unfocused and focused)
        String dstUnfocused = TransferScreen.renderInputRow("Destination Acc", "DGB-788635551", null, false, width);
        assertEquals(82, TUIBox.visibleLength(dstUnfocused));
        String dstFocused = TransferScreen.renderInputRow("Destination Acc", "DGB-788635551|", null, true, width);
        assertEquals(82, TUIBox.visibleLength(dstFocused));

        // Beneficiary Name (info row, unfocused and focused)
        String benUnfocused = TransferScreen.renderInfoRow("Beneficiary Name", "Awaiting destination account...", false, width);
        assertEquals(82, TUIBox.visibleLength(benUnfocused));
        String benFocused = TransferScreen.renderInfoRow("Beneficiary Name", "Awaiting destination account...", true, width);
        assertEquals(82, TUIBox.visibleLength(benFocused));

        // Transfer Amount (without hint)
        String amtUnfocused = TransferScreen.renderInputRow("Transfer Amount", "2,500.00 USD", null, false, width);
        assertEquals(82, TUIBox.visibleLength(amtUnfocused));
        String amtFocused = TransferScreen.renderInputRow("Transfer Amount", "2,500.00 USD|", null, true, width);
        assertEquals(82, TUIBox.visibleLength(amtFocused));

        // Remark (Optional)
        String remUnfocused = TransferScreen.renderInputRow("Remark (Optional)", "", null, false, width);
        assertEquals(82, TUIBox.visibleLength(remUnfocused));
        String remFocused = TransferScreen.renderInputRow("Remark (Optional)", "Lunch|", null, true, width);
        assertEquals(82, TUIBox.visibleLength(remFocused));

        // Category (with hint)
        String catUnfocused = TransferScreen.renderInputRow("Category", "(6) Bills & Utilities", "[Space: Swap]", false, width);
        assertEquals(82, TUIBox.visibleLength(catUnfocused));
        String catFocused = TransferScreen.renderInputRow("Category", "(6) Bills & Utilities", "[Space: Swap]", true, width);
        assertEquals(82, TUIBox.visibleLength(catFocused));
    }

    @Test
    @DisplayName("Verify LoanScreen 82-column layout, table columns, and horizontal action bar")
    void testLoanScreenLayout() {
        int width = TUILayout.APP_WIDTH;
        assertEquals(82, width);

        // Header, facility rows, and divider
        assertEquals(82, TUIBox.visibleLength(TUIBox.top(width)));
        assertEquals(82, TUIBox.visibleLength(TUIBox.line(ConsoleTheme.primary("DIGIBANK CORE > LOAN MANAGEMENT & REPAYMENTS"), width)));
        assertEquals(82, TUIBox.visibleLength(TUIBox.divider(width)));
        assertEquals(82, TUIBox.visibleLength(TUIBox.line("ACTIVE FACILITY: #LN-4 (Personal Credit Loan)", width)));
        assertEquals(82, TUIBox.visibleLength(TUIBox.emptyLine(width)));

        String row1 = "  Requested  : $ 500.00          Approved  : $ 500.00         Interest : 2.00%";
        assertEquals(82, TUIBox.visibleLength(TUIBox.line(row1, width)));
        String row2 = "  Term       : 10 Months         Balance   : $ 400.00        Risk Tier: MEDIUM";
        assertEquals(82, TUIBox.visibleLength(TUIBox.line(row2, width)));
        String row3 = "  Status     : ACTIVE            Disbursed : DGB-429309564";
        assertEquals(82, TUIBox.visibleLength(TUIBox.line(row3, width)));

        assertEquals(82, TUIBox.visibleLength(TUIBox.divider(width)));
        assertEquals(82, TUIBox.visibleLength(TUIBox.line("REPAYMENT SCHEDULE & INSTALLMENT HISTORY", width)));

        // Table Header & Separator (78 chars inner width)
        String header = String.format("  %-3s  %-12s  %11s   %9s   %11s   %-16s ",
                "#", "DUE DATE", "PRINCIPAL", "INTEREST", "TOTAL DUE", "STATUS");
        assertEquals(82, TUIBox.visibleLength(TUIBox.line(header, width)));

        String separator = " " + "─".repeat(76) + " ";
        assertEquals(82, TUIBox.visibleLength(TUIBox.line(separator, width)));

        // Data Row with pure ASCII status token (78 chars)
        String dataRow = String.format("  %02d   %-12s  %11s   %9s   %11s   %-16s ",
                1, "2026-09-01", "$  49.18", "$  0.82", "$  50.00", "PAID (Settled)");
        assertEquals(82, TUIBox.visibleLength(TUIBox.line(dataRow, width)));

        // Horizontal Action Bar
        String actRow = "  ▸ [1] Pay Next Installment ($50.00)     [2] Apply for Loan     [3] Dashboard";
        assertEquals(82, TUIBox.visibleLength(TUIBox.line(actRow, width)));
        assertEquals(82, TUIBox.visibleLength(TUIBox.bottom(width)));
    }

    @Test
    @DisplayName("Verify LoanScreen empty state layout, empty message, and dynamic 2-option action bar")
    void testLoanScreenEmptyStateLayout() {
        int width = TUILayout.APP_WIDTH;
        assertEquals(82, width);

        assertEquals(82, TUIBox.visibleLength(TUIBox.top(width)));
        assertEquals(82, TUIBox.visibleLength(TUIBox.line(ConsoleTheme.primary("DIGIBANK CORE > LOAN MANAGEMENT & REPAYMENTS"), width)));
        assertEquals(82, TUIBox.visibleLength(TUIBox.divider(width)));
        assertEquals(82, TUIBox.visibleLength(TUIBox.line("ACTIVE FACILITY: None", width)));
        assertEquals(82, TUIBox.visibleLength(TUIBox.line(ConsoleTheme.muted("  No active loan facilities found. Select [1] below to apply for a loan."), width)));
        assertEquals(82, TUIBox.visibleLength(TUIBox.divider(width)));
        assertEquals(82, TUIBox.visibleLength(TUIBox.line("REPAYMENT SCHEDULE & INSTALLMENT HISTORY", width)));
        assertEquals(82, TUIBox.visibleLength(TUIBox.line(ConsoleTheme.muted("  No active repayment schedule found."), width)));
        assertEquals(82, TUIBox.visibleLength(TUIBox.divider(width)));

        // Dynamic 2-option action bar
        String actRow = "  ▸ [1] Apply for Loan     [2] Dashboard";
        assertEquals(82, TUIBox.visibleLength(TUIBox.line(actRow, width)));
        assertEquals(82, TUIBox.visibleLength(TUIBox.bottom(width)));
    }

    @Test
    @DisplayName("Verify ForgotPasswordWizard 3-step layouts and 82-column box conformity")
    void testForgotPasswordWizardLayout() {
        int width = TUILayout.APP_WIDTH;
        assertEquals(82, width);

        // Step 1: Identify Account
        assertEquals(82, TUIBox.visibleLength(TUIBox.top(width)));
        assertEquals(82, TUIBox.visibleLength(TUIBox.line(ConsoleTheme.primary("DIGIBANK CORE > ACCOUNT RECOVERY > IDENTIFY ACCOUNT"), width)));
        assertEquals(82, TUIBox.visibleLength(TUIBox.line("RECOVERY INITIATION", width)));
        String idField = String.format("  Account Identifier   : [ %-46s ]", "men.senghak@gmail.com");
        assertEquals(82, TUIBox.visibleLength(TUIBox.line(idField, width)));
        assertEquals(82, TUIBox.visibleLength(TUIBox.line("SECURITY NOTICE", width)));
        assertEquals(82, TUIBox.visibleLength(TUIBox.line("  ▸ [1] Dispatch Recovery OTP                     [2] Cancel & Return", width)));
        assertEquals(82, TUIBox.visibleLength(TUIBox.bottom(width)));

        // Step 2: Verify OTP
        assertEquals(82, TUIBox.visibleLength(TUIBox.top(width)));
        assertEquals(82, TUIBox.visibleLength(TUIBox.line(ConsoleTheme.primary("DIGIBANK CORE > ACCOUNT RECOVERY > VERIFY OTP"), width)));
        assertEquals(82, TUIBox.visibleLength(TUIBox.line("SECURITY CHALLENGE", width)));
        assertEquals(82, TUIBox.visibleLength(TUIBox.line("  Destination Target   : m***k@gmail.com (Masked for Privacy)", width)));
        String otpField = String.format("  Enter 6-Digit OTP    : [ %-46s ]", "849201");
        assertEquals(82, TUIBox.visibleLength(TUIBox.line(otpField, width)));
        assertEquals(82, TUIBox.visibleLength(TUIBox.line("ATTEMPTS REMAINING: 3 / 3", width)));
        assertEquals(82, TUIBox.visibleLength(TUIBox.line("  ▸ [1] Verify & Proceed    [2] Resend OTP (Wait 60s)    [3] Abort Recovery", width)));
        assertEquals(82, TUIBox.visibleLength(TUIBox.bottom(width)));

        // Step 3: Reset Credentials
        assertEquals(82, TUIBox.visibleLength(TUIBox.top(width)));
        assertEquals(82, TUIBox.visibleLength(TUIBox.line(ConsoleTheme.primary("DIGIBANK CORE > ACCOUNT RECOVERY > RESET CREDENTIALS"), width)));
        assertEquals(82, TUIBox.visibleLength(TUIBox.line("SET NEW SECURE PASSWORD", width)));
        assertEquals(82, TUIBox.visibleLength(TUIBox.line("  Target Account       : MEN SENGHAK (#USR-3)", width)));
        assertEquals(82, TUIBox.visibleLength(TUIBox.line("PASSWORD COMPLEXITY CHECK", width)));
        String row1 = "  [✔] Min 8 Characters        [✔] Uppercase & Lowercase Letter";
        String row2 = "  [✔] Numeric Digit (0-9)     [✔] Special Symbol (@, #, $, %, etc.)";
        assertEquals(82, TUIBox.visibleLength(TUIBox.line(row1, width)));
        assertEquals(82, TUIBox.visibleLength(TUIBox.line(row2, width)));
        assertEquals(82, TUIBox.visibleLength(TUIBox.line("  ▸ [1] Commit Password Change                   [2] Discard & Exit", width)));
        assertEquals(82, TUIBox.visibleLength(TUIBox.bottom(width)));
    }

    @Test
    @DisplayName("Verify StaffResetCredentialsScreen 82-column layout and compartments")
    void testStaffResetCredentialsScreenLayout() {
        int width = TUILayout.APP_WIDTH;
        assertEquals(82, width);

        assertEquals(82, TUIBox.visibleLength(TUIBox.top(width)));
        assertEquals(82, TUIBox.visibleLength(TUIBox.line(ConsoleTheme.primary("DIGIBANK CORE > SUPER ADMIN > STAFF MANAGEMENT > RESET CREDENTIALS"), width)));
        assertEquals(82, TUIBox.visibleLength(TUIBox.divider(width)));
        assertEquals(82, TUIBox.visibleLength(TUIBox.line("TARGET ADMINISTRATOR / STAFF", width)));
        assertEquals(82, TUIBox.visibleLength(TUIBox.line("  Staff Identifier      : #ADM-102 (Teller_Sophea)", width)));
        assertEquals(82, TUIBox.visibleLength(TUIBox.line("CREDENTIAL OVERRIDE", width)));
        String tempPassField = String.format("  Temporary Password   : [ %-46s ]", "TempPass@2026");
        assertEquals(82, TUIBox.visibleLength(TUIBox.line(tempPassField, width)));
        assertEquals(82, TUIBox.visibleLength(TUIBox.line("AUDIT CONFIRMATION", width)));
        assertEquals(82, TUIBox.visibleLength(TUIBox.line("  Super Admin Authid    : superadmin (#ADM-001)", width)));
        String reasonField = String.format("  Reason for Reset     : [ %-46s ]", "Staff reported forgotten credentials");
        assertEquals(82, TUIBox.visibleLength(TUIBox.line(reasonField, width)));
        assertEquals(82, TUIBox.visibleLength(TUIBox.line("  ▸ [1] Authorize & Reset Password                [2] Discard & Return", width)));
        assertEquals(82, TUIBox.visibleLength(TUIBox.bottom(width)));
    }

    @Test
    @DisplayName("Verify LoanRepaymentScreen layout, separator width, and payment verification card")
    void testLoanRepaymentScreenLayout() {
        int width = TUILayout.APP_WIDTH;
        assertEquals(82, width);

        assertEquals(82, TUIBox.visibleLength(TUIBox.top(width)));
        assertEquals(82, TUIBox.visibleLength(TUIBox.line(ConsoleTheme.primary("DIGIBANK CORE > LOAN REPAYMENT & INSTALLMENT SCHEDULE"), width)));
        assertEquals(82, TUIBox.visibleLength(TUIBox.divider(width)));
        assertEquals(82, TUIBox.visibleLength(TUIBox.line("ACTIVE FACILITY SUMMARY", width)));

        String th = String.format("  %-6s  %-14s  %11s    %8s    %10s      %-8s ",
                "INST#", "DUE DATE", "PRINCIPAL", "INTEREST", "TOTAL DUE", "STATUS");
        assertEquals(82, TUIBox.visibleLength(TUIBox.line(th, width)));

        String sep = " " + "─".repeat(76) + " ";
        assertEquals(82, TUIBox.visibleLength(TUIBox.line(sep, width)));

        String dataRow = String.format("   #%02d    %-14s  %11s    %8s    %10s      %-8s ",
                6, "2026-10-01", "$     49.26", "$   0.74", "$    50.00", "DUE NEXT");
        assertEquals(82, TUIBox.visibleLength(TUIBox.line(dataRow, width)));

        String actRow = "  ▸ [1] Pay Next Installment ($50.00)            [0] Return to Main Menu";
        assertEquals(82, TUIBox.visibleLength(TUIBox.line(actRow, width)));
        assertEquals(82, TUIBox.visibleLength(TUIBox.bottom(width)));

        // Verification Card
        assertEquals(82, TUIBox.visibleLength(TUIBox.line(ConsoleTheme.primary("DIGIBANK CORE > LOAN REPAYMENT > VERIFY REPAYMENT"), width)));
        assertEquals(82, TUIBox.visibleLength(TUIBox.line("PAYMENT BREAKDOWN (#LN-0004 - Installment #06)", width)));
        assertEquals(82, TUIBox.visibleLength(TUIBox.line("  Debit Account       : DGB-429309564 (CHECKING - USD)", width)));
        assertEquals(82, TUIBox.visibleLength(TUIBox.line("  Available Balance   : $   4,700.00 USD", width)));
        assertEquals(82, TUIBox.visibleLength(TUIBox.line("  Principal Portion   : $      49.26 USD", width)));
        assertEquals(82, TUIBox.visibleLength(TUIBox.line("  Interest Portion    : $       0.74 USD", width)));
        assertEquals(82, TUIBox.visibleLength(TUIBox.line("  Total Due Now       : $      50.00 USD", width)));
        assertEquals(82, TUIBox.visibleLength(TUIBox.line("  Remaining Loan Bal  : $     350.74 USD (After payment)", width)));
        assertEquals(82, TUIBox.visibleLength(TUIBox.line("  New Account Balance : $   4,650.00 USD", width)));
        assertEquals(82, TUIBox.visibleLength(TUIBox.line("  ▸ [1] Authorize & Post Repayment                [2] Cancel & Return", width)));
    }

    @Test
    @DisplayName("Verify ApplyLoanScreen real-time underwriting calculations and contract confirmation")
    void testApplyLoanRealTimeUnderwriting() {
        int width = TUILayout.APP_WIDTH;
        assertEquals(82, width);

        // Test APR Tier determination
        assertEquals(new BigDecimal("6.50"), ApplyLoanScreen.calculateAPR(750));
        assertEquals(new BigDecimal("8.50"), ApplyLoanScreen.calculateAPR(600));
        assertEquals(new BigDecimal("12.00"), ApplyLoanScreen.calculateAPR(550));

        // Test Monthly Payment Amortization ($1,000 at 8.50% for 12 months = $87.22)
        BigDecimal monthly = ApplyLoanScreen.calculateMonthlyPayment(new BigDecimal("1000.00"), new BigDecimal("8.50"), 12);
        assertEquals(new BigDecimal("87.22"), monthly);

        // Test DTI calculation: (Existing Debt + Monthly Payment) / Income
        // ($300.00 + $87.22) / $2,000.00 * 100 = 19.36%
        BigDecimal dti = ApplyLoanScreen.calculateDTI(new BigDecimal("2000.00"), new BigDecimal("300.00"), monthly);
        assertEquals(new BigDecimal("19.36"), dti);

        // Test Risk Rating
        assertEquals("LOW RISK", ApplyLoanScreen.calculateRiskRating(720, new BigDecimal("35.00")));
        assertEquals("MODERATE RISK", ApplyLoanScreen.calculateRiskRating(600, new BigDecimal("45.00")));
        assertEquals("HIGH RISK", ApplyLoanScreen.calculateRiskRating(500, new BigDecimal("55.00")));

        // Test Contract Confirmation line lengths
        assertEquals(82, TUIBox.visibleLength(TUIBox.line(ConsoleTheme.primary("DIGIBANK CORE > LOAN MANAGEMENT > CONTRACT CONFIRMATION"), width)));
        assertEquals(82, TUIBox.visibleLength(TUIBox.line("FACILITY OFFER & DISBURSEMENT SUMMARY", width)));
        assertEquals(82, TUIBox.visibleLength(TUIBox.line("  Facility Type        : Personal Credit Facility (#LN-NEW)", width)));
        assertEquals(82, TUIBox.visibleLength(TUIBox.line("  Principal Disbursed  : $   1,000.00 USD", width)));
        assertEquals(82, TUIBox.visibleLength(TUIBox.line("  Interest Rate (APR)  : 8.50% Fixed Annual Rate", width)));
        assertEquals(82, TUIBox.visibleLength(TUIBox.line("  Tenor & Frequency    : 12 Months (Monthly Amortization)", width)));
        assertEquals(82, TUIBox.visibleLength(TUIBox.line("  Total Interest Due   : $      46.64 USD", width)));
        assertEquals(82, TUIBox.visibleLength(TUIBox.line("  Total Repayable      : $   1,046.64 USD", width)));
        assertEquals(82, TUIBox.visibleLength(TUIBox.line("  Fixed Monthly Due    : $      87.22 USD / Month", width)));
        assertEquals(82, TUIBox.visibleLength(TUIBox.line("  ▸ [1] Accept Terms & Disburse Funds             [2] Back to Edit Form", width)));
    }

    @Test
    @DisplayName("Verify BudgetScreen tabbed view, 5-row pagination, and strict 82-column layout")
    void testBudgetScreenTabbedAndPagedLayout() {
        int width = TUILayout.APP_WIDTH;
        assertEquals(82, width);

        assertEquals(82, TUIBox.visibleLength(TUIBox.top(width)));
        assertEquals(82, TUIBox.visibleLength(TUIBox.line(ConsoleTheme.primary("DIGIBANK CORE > FINANCIAL PLANNING & WEALTH TARGETS"), width)));
        assertEquals(82, TUIBox.visibleLength(TUIBox.divider(width)));

        // Tab strip line
        String tabStrip = String.format(" %-40s    %-32s", "▸ [1] MONTHLY BUDGETS (Page 1/2)", "  [2] SAVINGS GOALS (Page 1/2)");
        assertEquals(82, TUIBox.visibleLength(TUIBox.line(tabStrip, width)));
        assertEquals(82, TUIBox.visibleLength(TUIBox.divider(width)));

        // Budget Table Header & Separator
        String bHeader = " CATEGORY       LIMIT        SPENT      REMAINING    USAGE PROGRESS           ";
        assertEquals(82, TUIBox.visibleLength(TUIBox.line(bHeader, width)));
        assertEquals(82, TUIBox.visibleLength(TUIBox.line(" " + "─".repeat(76) + " ", width)));

        // Budget Row format
        String bRow = String.format(" %-15s %11s  %10s  %10s   [%-16s] %3d%%",
                "Food & Dining", "$   200.00", "$    45.50", "$   154.50", "████░░░░░░░░░░░░", 22);
        assertEquals(82, TUIBox.visibleLength(TUIBox.line(bRow, width)));

        // Metadata row
        String metaRow = String.format("Page: [ %d / %d ]   │ Filter: [ALL CATEGORIES]     │ Total Budgets: %-4d", 1, 2, 7);
        assertEquals(82, TUIBox.visibleLength(TUIBox.line(metaRow, width)));

        // Tab 1 Action bar
        String act1 = "  ▸ [S] Set Budget Limit        [D] Delete Budget           [Esc] Dashboard";
        assertEquals(82, TUIBox.visibleLength(TUIBox.line(act1, width)));

        // Savings Goal Table Header & Separator
        String gHeader = " GOAL NAME      TARGET       SAVED       DEADLINE    PROGRESS STATUS          ";
        assertEquals(82, TUIBox.visibleLength(TUIBox.line(gHeader, width)));
        assertEquals(82, TUIBox.visibleLength(TUIBox.line(" " + "─".repeat(76) + " ", width)));

        // Goal Row format
        String gRow = String.format(" %-15s %11s  %10s   %-10s [%-16s] %3d%%",
                "New iPhone 18", "$  2,500.00", "$  1,000.00", "2026-12-01", "██████░░░░░░░░░░", 40);
        assertEquals(82, TUIBox.visibleLength(TUIBox.line(gRow, width)));

        // Tab 2 Action bar
        String act2 = "  ▸ [C] Create Goal    [D] Deposit to Goal    [W] Withdraw/Claim    [Esc] Back";
        assertEquals(82, TUIBox.visibleLength(TUIBox.line(act2, width)));
        assertEquals(82, TUIBox.visibleLength(TUIBox.bottom(width)));
    }

    @Test
    @DisplayName("Verify CategorySelectorModal and GoalSelectorModal conform strictly to 82 columns")
    void testCategoryAndGoalModalsLayout() {
        int width = TUILayout.APP_WIDTH;
        assertEquals(82, width);

        List<Category> categories = new ArrayList<>();
        categories.add(Category.builder().categoryId(1L).name("Food & Dining").system(true).description("Groceries & restaurants").build());
        categories.add(Category.builder().categoryId(2L).name("Utilities").system(true).description("Electricity & Water").build());

        String catModalOutput = CategorySelectorModal.renderModalContent(categories, 0, width);
        String[] catLines = catModalOutput.split("\n");
        for (int i = 0; i < catLines.length - 1; i++) {
            assertEquals(82, TUIBox.visibleLength(catLines[i]), "Category line " + i + " must be 82 cols: " + catLines[i]);
        }
        assertTrue(catModalOutput.contains("DIGIBANK CORE > CATEGORIES > SELECT EXPENSE CATEGORY"));
        assertTrue(catModalOutput.contains("AVAILABLE EXPENSE CATEGORIES"));
        assertTrue(catModalOutput.contains("Food & Dining"));

        List<SavingGoal> goals = new ArrayList<>();
        goals.add(SavingGoal.builder().goalId(1L).name("Emergency Fund").targetAmount(new BigDecimal("5000.00")).currentAmount(new BigDecimal("3250.00")).build());
        goals.add(SavingGoal.builder().goalId(2L).name("New Laptop").targetAmount(new BigDecimal("1500.00")).currentAmount(new BigDecimal("1500.00")).build());

        String goalModalOutput = GoalSelectorModal.renderModalContent(goals, 0, width);
        String[] goalLines = goalModalOutput.split("\n");
        for (int i = 0; i < goalLines.length - 1; i++) {
            assertEquals(82, TUIBox.visibleLength(goalLines[i]), "Goal line " + i + " must be 82 cols: " + goalLines[i]);
        }
        assertTrue(goalModalOutput.contains("DIGIBANK CORE > SAVINGS GOALS > SELECT TARGET GOAL"));
        assertTrue(goalModalOutput.contains("AVAILABLE ACTIVE SAVINGS GOALS"));
        assertTrue(goalModalOutput.contains("Emergency Fund"));
    }

    @Test
    @DisplayName("Verify TransferScreen destination account placeholder and normalization")
    void testTransferScreenDestinationAccountField() {
        TransferScreen transferScreen = new TransferScreen();

        // Empty focused -> contains dimmed placeholder + cursor
        String placeholderFieldFocused = transferScreen.renderDestinationAccountField("", true, 55);
        assertTrue(placeholderFieldFocused.contains("\033[90m"));
        assertTrue(placeholderFieldFocused.contains("e.g. 788635551 or DGB-788635551"));
        assertTrue(placeholderFieldFocused.contains("_"));

        // Empty unfocused -> contains dimmed placeholder, no cursor
        String placeholderFieldUnfocused = transferScreen.renderDestinationAccountField("", false, 55);
        assertTrue(placeholderFieldUnfocused.contains("\033[90m"));
        assertTrue(placeholderFieldUnfocused.contains("e.g. 788635551 or DGB-788635551"));
        assertFalse(placeholderFieldUnfocused.contains("_"));

        // Typed value -> clear placeholder, standard text
        String typedField = transferScreen.renderDestinationAccountField("DGB-788635551", true, 55);
        assertFalse(typedField.contains("\033[90m"));
        assertTrue(typedField.contains("DGB-788635551"));
        assertTrue(typedField.contains("_"));

        // 82-column layout conformance when embedded in form
        String fieldInForm = transferScreen.renderDestinationAccountField("", true, 50);
        String formRow = TUIBox.line("  Destination Acc   : " + fieldInForm, TUILayout.APP_WIDTH);
        assertEquals(82, TUIBox.visibleLength(formRow));

        // Account number normalization
        assertEquals("DGB-788635551", TransferScreen.normalizeAccountNumber("788635551"));
        assertEquals("DGB-788635551", TransferScreen.normalizeAccountNumber("dgb-788635551"));
        assertEquals("DGB-788635551", TransferScreen.normalizeAccountNumber("DGB-788635551"));
        assertEquals("", TransferScreen.normalizeAccountNumber(""));
        assertEquals("", TransferScreen.normalizeAccountNumber(null));
    }

    @Test
    @DisplayName("Verify SelectSenderAccountModal conforms strictly to 82 columns and aligns currency glyphs")
    void testSelectSenderAccountModalLayout() {
        int width = TUILayout.APP_WIDTH;
        assertEquals(82, width);

        List<AccountDTO> accounts = new ArrayList<>();
        accounts.add(AccountDTO.builder().accountId(1L).accountNumber("DGB-429309564").accountType(AccountType.CHECKING).balance(new BigDecimal("4700.00")).currency(Currency.USD).status(AccountStatus.ACTIVE).build());
        accounts.add(AccountDTO.builder().accountId(2L).accountNumber("DGB-788635551").accountType(AccountType.SAVINGS).balance(new BigDecimal("13400.00")).currency(Currency.USD).status(AccountStatus.ACTIVE).build());
        accounts.add(AccountDTO.builder().accountId(3L).accountNumber("DGB-134672316").accountType(AccountType.SAVINGS).balance(new BigDecimal("320000")).currency(Currency.KHR).status(AccountStatus.ACTIVE).build());

        String modalOutput = SelectSenderAccountModal.renderContent(accounts, 0, width);
        String[] lines = modalOutput.split("\n");
        for (int i = 0; i < lines.length - 1; i++) {
            assertEquals(82, TUIBox.visibleLength(lines[i]), "Line " + i + " must be 82 cols: " + lines[i]);
        }

        assertTrue(modalOutput.contains("DIGIBANK CORE > MONEY MOVEMENT > SELECT SENDER ACCOUNT"));
        assertTrue(modalOutput.contains("AVAILABLE SOURCE ACCOUNTS"));
        assertTrue(modalOutput.contains("Bal: $  4,700.00 USD"));
        assertTrue(modalOutput.contains("Bal: ៛   320,000 KHR"));
        assertTrue(modalOutput.contains("Tip: Select an account with sufficient balance for this transfer."));
    }

    @Test
    @DisplayName("Verify AccountSelectorModal.renderModal with Account entities conforms to 82 columns")
    void testAccountSelectorModalEntityLayout() {
        int width = TUILayout.APP_WIDTH;
        assertEquals(82, width);

        List<Account> accounts = new ArrayList<>();
        accounts.add(Account.builder().accountId(1L).accountNumber("DGB-429309564").accountType(AccountType.CHECKING).balance(new BigDecimal("4700.00")).currency(Currency.USD).status(AccountStatus.ACTIVE).build());
        accounts.add(Account.builder().accountId(2L).accountNumber("DGB-134672316").accountType(AccountType.SAVINGS).balance(new BigDecimal("320000")).currency(Currency.KHR).status(AccountStatus.ACTIVE).build());

        String rendered = AccountSelectorModal.renderModal(accounts, 0, "DIGIBANK CORE > SELECT ACCOUNT");
        String[] lines = rendered.split("\n");
        for (int i = 0; i < lines.length - 1; i++) {
            assertEquals(82, TUIBox.visibleLength(lines[i]), "Entity modal line " + i + " must be 82 cols: " + lines[i]);
        }
        assertTrue(rendered.contains("Bal: $  4,700.00 USD"));
        assertTrue(rendered.contains("Bal: ៛   320,000 KHR"));
    }

    @Test
    @DisplayName("Verify WelcomeScreen System Gateway conforms strictly to 82 columns and exact specification")
    void testWelcomeScreenLayout() {
        int width = TUILayout.APP_WIDTH;
        assertEquals(82, width);

        String rendered = WelcomeScreen.renderContent(0, width);
        String[] lines = rendered.split("\n");

        // The first lines (inside the box) must all be exactly 82 cols
        // The last line is the footer navigation hint outside the box
        for (int i = 0; i < lines.length - 1; i++) {
            assertEquals(82, TUIBox.visibleLength(lines[i]), "WelcomeScreen line " + i + " must be 82 cols: " + lines[i]);
        }

        assertTrue(rendered.contains("DIGIBANK CORE > SYSTEM GATEWAY"));
        assertTrue(rendered.contains("[BUILD: v1.4.2]"));
        assertTrue(rendered.contains("ENTERPRISE CORE BANKING & LEDGER ENGINE"));
        assertTrue(rendered.contains("High-Performance  •  ACID-Compliant  •  Zero-Trust"));
        assertTrue(rendered.contains("PORTAL SELECTION"));
        assertTrue(rendered.contains("[1] Sign In"));
        assertTrue(rendered.contains("[2] Open Bank Account"));
        assertTrue(rendered.contains("[3] Exit System"));
        assertTrue(rendered.contains("[↑/↓] Navigate  •  [Enter] Confirm  •  [1-3] Quick Jump  •  [Esc] Exit"));
    }

    @Test
    @DisplayName("Verify SuperAdminDashboardScreen conforms strictly to 82 columns")
    void testSuperAdminDashboardScreenLayout() {
        int width = TUILayout.APP_WIDTH;
        assertEquals(82, width);

        AdminDTO admin = AdminDTO.builder().adminId(1L).username("superadmin").role(AdminRole.SUPER_ADMIN).build();
        List<FraudAlert> alerts = List.of(
                FraudAlert.builder().alertId(1L).riskLevel(RiskLevel.HIGH).accountId(904123L).description("Rapid Velocity > 5 tx/min").status(FraudStatus.OPEN).build()
        );

        String rendered = SuperAdminDashboardScreen.renderContent(admin, 1, 10, 1, 0, 24, alerts, 0,
                "Super Admin session active. All core banking modules nominal.", false, width);
        String[] lines = rendered.split("\n");
        for (int i = 0; i < lines.length - 1; i++) {
            assertEquals(82, TUIBox.visibleLength(lines[i]), "SuperAdmin line " + i + " must be 82 cols: " + lines[i]);
        }
        String stripped = TUIBox.stripAnsi(rendered);
        assertTrue(stripped.contains("SYSTEM HEALTH & RADAR"));
        assertTrue(stripped.contains("FRAUD ALERTS & THREATS"));
        assertTrue(stripped.contains("ADMINISTRATIVE ACTIONS"));
        assertTrue(stripped.contains("[1] User Directory & Profiles"));
        assertTrue(stripped.contains("[2] Review Loan Underwriting Queue"));
        assertTrue(stripped.contains("[3] Global Ledger & Vault"));
        assertTrue(stripped.contains("[4] Forensic Audit Trail"));
        assertTrue(stripped.contains("[5] FX Engine & Settings"));
        assertTrue(stripped.contains("[6] Security & Credential Operations"));
        assertTrue(stripped.contains("[0] Sign Out & Terminate Session"));
    }

    @Test
    @DisplayName("Verify UserDirectoryScreen conforms strictly to 82 columns")
    void testUserDirectoryScreenLayout() {
        int width = TUILayout.APP_WIDTH;
        assertEquals(82, width);

        List<UserDirectoryItem> items = List.of(
                UserDirectoryItem.builder().userId(1L).username("sophea").fullName("Sophea Chan").totalBalance(new BigDecimal("12450.00")).status(UserStatus.ACTIVE).accountCount(2).build(),
                UserDirectoryItem.builder().userId(2L).username("ratha_dev").fullName("Ratha Sok").totalBalance(new BigDecimal("320.00")).status(UserStatus.FROZEN).accountCount(1).build()
        );

        String rendered = UserDirectoryScreen.renderContent(items, 1, 4, 0, "Account directory loaded.", false, width);
        String[] lines = rendered.split("\n");
        for (int i = 0; i < lines.length - 1; i++) {
            assertEquals(82, TUIBox.visibleLength(lines[i]), "UserDirectory line " + i + " must be 82 cols: " + lines[i]);
        }
        assertTrue(rendered.contains("USER DIRECTORY (Page 1/4)"));
        assertTrue(rendered.contains("[Enter] View Profile"));
        assertTrue(rendered.contains("[F] Toggle Freeze"));
    }

    @Test
    @DisplayName("Verify UserProfileModal conforms strictly to 82 columns")
    void testUserProfileModalLayout() {
        int width = TUILayout.APP_WIDTH;
        assertEquals(82, width);

        List<Account> accounts = List.of(
                Account.builder().accountId(10L).accountNumber("ACC-88421094").accountType(AccountType.CHECKING).currency(Currency.USD).balance(new BigDecimal("4210.00")).status(AccountStatus.ACTIVE).build()
        );
        UserProfileDossier dossier = UserProfileDossier.builder()
                .userId(42L)
                .username("sophea")
                .fullName("Sophea Chan")
                .email("sophea@gmail.com")
                .phone("+855 12 345 678")
                .status(UserStatus.ACTIVE)
                .registrationDate(java.time.LocalDate.of(2025, 1, 15))
                .kycVerificationLevel("LEVEL_2_VERIFIED")
                .securityMode("STANDARD_2FA")
                .linkedAccounts(accounts)
                .build();

        String rendered = UserProfileModal.renderContent(dossier, "Profile loaded.", false, width);
        String[] lines = rendered.split("\n");
        for (int i = 0; i < lines.length - 1; i++) {
            assertEquals(82, TUIBox.visibleLength(lines[i]), "UserProfileModal line " + i + " must be 82 cols: " + lines[i]);
        }
        assertTrue(rendered.contains("CUSTOMER IDENTITY DOSSIER"));
        assertTrue(rendered.contains("LINKED BANK ACCOUNTS"));
        assertTrue(rendered.contains("[F] Toggle Freeze"));
        assertTrue(rendered.contains("[R] Reset Token"));
    }

    @Test
    @DisplayName("Verify GlobalLedgerScreen conforms strictly to 82 columns")
    void testGlobalLedgerScreenLayout() {
        int width = TUILayout.APP_WIDTH;
        assertEquals(82, width);

        List<GlobalLedgerItem> items = List.of(
                GlobalLedgerItem.builder().accountId(1L).accountNumber("ACC-88421094").ownerName("Sophea Chan").accountType(AccountType.CHECKING).currency(Currency.USD).balance(new BigDecimal("12450.00")).riskLevel("LOW").status(AccountStatus.ACTIVE).build()
        );
        Map<Currency, BigDecimal> totals = Map.of(Currency.USD, new BigDecimal("412850.00"), Currency.KHR, new BigDecimal("45200000"));

        String rendered = GlobalLedgerScreen.renderContent(items, totals, 1, 5, 0, null, null,
                "Master ledger synchronized. Vault integrity intact.", false, width);
        String[] lines = rendered.split("\n");
        for (int i = 0; i < lines.length - 1; i++) {
            assertEquals(82, TUIBox.visibleLength(lines[i]), "GlobalLedger line " + i + " must be 82 cols: " + lines[i]);
        }
        assertTrue(rendered.contains("TOTAL VAULT ASSETS :"));
        assertTrue(rendered.contains("CCY:"));
        assertTrue(rendered.contains("[1-3/Tab] Switch CCY"));
    }

    @Test
    @DisplayName("Verify LoanUnderwritingScreen conforms strictly to 82 columns")
    void testLoanUnderwritingScreenLayout() {
        int width = TUILayout.APP_WIDTH;
        assertEquals(82, width);

        Loan loan = Loan.builder()
                .loanId(42L)
                .userId(42L)
                .requestedAmount(new BigDecimal("15000.00"))
                .termMonths(24)
                .monthlyIncome(new BigDecimal("3500.00"))
                .creditScore(742)
                .existingDebt(BigDecimal.ZERO)
                .interestRate(new BigDecimal("12.50"))
                .status(LoanStatus.PENDING)
                .build();
        Account acct = Account.builder().accountId(1L).accountNumber("ACC-88421094").accountType(AccountType.CHECKING).currency(Currency.USD).balance(new BigDecimal("4210.00")).status(AccountStatus.ACTIVE).build();
        UserProfileDossier dossier = UserProfileDossier.builder().userId(42L).fullName("Sophea Chan").linkedAccounts(List.of(acct)).build();

        String rendered = LoanUnderwritingScreen.renderContent(loan, dossier, acct, 0, 3,
                new BigDecimal("15000.00"), new BigDecimal("12.50"),
                "Reviewing applicant dossier.", false, width);
        String[] lines = rendered.split("\n");
        for (int i = 0; i < lines.length - 1; i++) {
            assertEquals(82, TUIBox.visibleLength(lines[i]), "LoanUnderwriting line " + i + " must be 82 cols: " + lines[i]);
        }
        assertTrue(rendered.contains("UNDERWRITING QUEUE: Application 1 of 3"));
        assertTrue(rendered.contains("BORROWER FINANCIAL PROFILE"));
        assertTrue(rendered.contains("UNDERWRITING DECISION PARAMETERS"));
        assertTrue(rendered.contains("[A] Approve"));
        assertTrue(rendered.contains("[R] Reject"));
    }

    @Test
    @DisplayName("Verify AuditLogScreen conforms strictly to 82 columns")
    void testAuditLogScreenLayout() {
        int width = TUILayout.APP_WIDTH;
        assertEquals(82, width);

        List<AuditLog> logs = List.of(
                AuditLog.builder().logId(1L).adminId(1L).action("FREEZE_ACCOUNT").targetTable("accounts").targetId(104L).createdAt(java.time.LocalDateTime.of(2026, 9, 15, 14, 22)).build()
        );

        String rendered = AuditLogScreen.renderContent(logs, 1, 14, 138, 0, null,
                "Forensic audit trail synchronized.", false, width);
        String[] lines = rendered.split("\n");
        for (int i = 0; i < lines.length - 1; i++) {
            assertEquals(82, TUIBox.visibleLength(lines[i]), "AuditLog line " + i + " must be 82 cols: " + lines[i]);
        }
        assertTrue(rendered.contains("FORENSIC AUDIT TRAIL"));
        assertTrue(rendered.contains("PERIOD:"));
        assertTrue(rendered.contains("[1-4] Period"));
        assertTrue(rendered.contains("[Enter] Inspect"));
        assertTrue(rendered.contains("[C] Custom"));
    }

    @Test
    @DisplayName("Verify FxConfigScreen conforms strictly to 82 columns")
    void testFxConfigScreenLayout() {
        int width = TUILayout.APP_WIDTH;
        assertEquals(82, width);

        Map<String, BigDecimal> rates = Map.of(
                "KHR", new BigDecimal("4085.00"),
                "EUR", new BigDecimal("0.9200"),
                "GBP", new BigDecimal("0.7880"),
                "JPY", new BigDecimal("153.95")
        );

        String rendered = FxConfigScreen.renderContent(rates, new BigDecimal("0.00"), new BigDecimal("1.50"),
                new BigDecimal("10000.00"), new BigDecimal("5000.00"), 0,
                "Configuration active.", false, width);
        String[] lines = rendered.split("\n");
        for (int i = 0; i < lines.length - 1; i++) {
            assertEquals(82, TUIBox.visibleLength(lines[i]), "FxConfig line " + i + " must be 82 cols: " + lines[i]);
        }
        assertTrue(rendered.contains("CURRENCY EXCHANGE RATES (BASE: USD)"));
        assertTrue(rendered.contains("PLATFORM FEES & TRANSACTION CONTROLS"));
        assertTrue(rendered.contains("[S] Save"));
        assertTrue(rendered.contains("[R] Sync Rates"));
    }

    @Test
    @DisplayName("Verify StaffManagementScreen conforms strictly to 82 columns")
    void testStaffManagementScreenLayout() {
        int width = TUILayout.APP_WIDTH;
        assertEquals(82, width);

        List<Admin> staffList = List.of(
                Admin.builder().adminId(1L).username("superadmin").role(AdminRole.SUPER_ADMIN).status(AdminStatus.ACTIVE).build(),
                Admin.builder().adminId(2L).username("compliance1").role(AdminRole.COMPLIANCE_OFFICER).status(AdminStatus.ACTIVE).build()
        );

        String rendered = StaffManagementScreen.renderContent(staffList, 0, "Staff directory synchronized.", false, width);
        String[] lines = rendered.split("\n");
        for (int i = 0; i < lines.length - 1; i++) {
            assertEquals(82, TUIBox.visibleLength(lines[i]), "StaffManagement line " + i + " must be 82 cols: " + lines[i]);
        }
        assertTrue(rendered.contains("INTERNAL STAFF DIRECTORY"));
        assertTrue(rendered.contains("[C] New"));
        assertTrue(rendered.contains("[R] Reset"));
        assertTrue(rendered.contains("[T] Role"));
        assertTrue(rendered.contains("[F] Suspend"));
    }

    @Test
    @DisplayName("Verify FraudInvestigationScreen conforms strictly to 82 columns with dynamic triage actions")
    void testFraudInvestigationScreenLayout() {
        int width = TUILayout.APP_WIDTH;
        assertEquals(82, width);

        FraudAlert alert = FraudAlert.builder()
                .alertId(1L)
                .accountId(9L)
                .transactionId(28L)
                .riskLevel(RiskLevel.HIGH)
                .status(FraudStatus.UNDER_INVESTIGATION)
                .description("High-value velocity threshold exceeded ($15,000.00 USD)")
                .build();

        Account account = Account.builder()
                .accountId(9L)
                .accountNumber("DGB-000000009")
                .status(com.bank.model.enums.AccountStatus.ACTIVE)
                .build();

        String rendered = FraudInvestigationScreen.renderContent(alert, account, 0, 3, 0, "Ready.", false, width);
        String[] lines = rendered.split("\n");
        for (int i = 0; i < lines.length - 1; i++) {
            assertEquals(82, TUIBox.visibleLength(lines[i]), "FraudInvestigation line " + i + " must be 82 cols: " + lines[i]);
        }
        assertTrue(rendered.contains("ACTIVE INCIDENT DOSSIER"));
        assertTrue(rendered.contains("QUEUE: 1 OF 3 INCIDENTS"));
        assertTrue(rendered.contains("TRIAGE ACTIONS FOR DGB-000000009"));
        assertTrue(rendered.contains("[1] Freeze Compromised Account (Immediate Restriction)"));
        assertTrue(rendered.contains("[2] Mark Incident as Confirmed Fraud (Audit Trail Committal)"));
        assertTrue(rendered.contains("[3] Dismiss Alert as False Positive (Clear Flag)"));
        assertTrue(rendered.contains("[4] Next Incident Dossier (→)"));
    }

    @Test
    @DisplayName("Verify ComplianceUserForensicsScreen conforms strictly to 82 columns")
    void testComplianceUserForensicsScreenLayout() {
        int width = TUILayout.APP_WIDTH;
        assertEquals(82, width);

        Account acc = Account.builder()
                .accountId(3L)
                .accountNumber("DGB-000000003")
                .status(com.bank.model.enums.AccountStatus.FROZEN)
                .build();

        com.bank.model.dto.UserProfileDossier dossier = com.bank.model.dto.UserProfileDossier.builder()
                .userId(3L)
                .username("senghak")
                .fullName("Men Senghak")
                .kycVerificationLevel("VERIFIED")
                .linkedAccounts(List.of(acc))
                .build();

        List<com.bank.model.dto.TransactionSummaryDTO> activities = List.of(
                com.bank.model.dto.TransactionSummaryDTO.builder()
                        .transactionId(28L)
                        .createdAt(java.time.LocalDateTime.of(2026, 9, 17, 12, 20))
                        .transactionType("TRANSFER")
                        .amount(new BigDecimal("15000.00"))
                        .destination("DGB-000000009")
                        .status("FLAGGED")
                        .build(),
                com.bank.model.dto.TransactionSummaryDTO.builder()
                        .transactionId(24L)
                        .createdAt(java.time.LocalDateTime.of(2026, 9, 16, 9, 14))
                        .transactionType("DEPOSIT")
                        .amount(new BigDecimal("16000.00"))
                        .destination("VAULT CASH")
                        .status("CLEARED")
                        .build()
        );

        String rendered = ComplianceUserForensicsScreen.renderContent(dossier, activities, 0, "Account DGB-000000003 placed on restriction.", false, width);
        String[] lines = rendered.split("\n");
        for (int i = 0; i < lines.length - 1; i++) {
            assertEquals(82, TUIBox.visibleLength(lines[i]), "ComplianceUserForensics line " + i + " must be 82 cols: " + lines[i]);
        }
        assertTrue(rendered.contains("USER PROFILE DOSSIER"));
        assertTrue(rendered.contains("FINANCIAL ACTIVITY & TRANSACTION AUDIT"));
        assertTrue(rendered.contains("COMPLIANCE INTERVENTION CONTROLS"));
        assertTrue(rendered.contains("Men Senghak"));
        assertTrue(rendered.contains("@senghak"));
        assertTrue(rendered.contains("DGB-000000003 [FROZEN]"));
        assertTrue(rendered.contains("[1] Unfreeze Customer Account (DGB-000000003)"));
    }

    @Test
    @DisplayName("Verify FraudInvestigationScreen empty state conforms strictly to 82 columns")
    void testFraudInvestigationScreenEmptyState() {
        int width = TUILayout.APP_WIDTH;
        assertEquals(82, width);

        String rendered = FraudInvestigationScreen.renderContent(null, 0, null, false, width);
        String[] lines = rendered.split("\n");
        for (int i = 0; i < lines.length - 1; i++) {
            assertEquals(82, TUIBox.visibleLength(lines[i]), "FraudInvestigation empty state line " + i + " must be 82 cols: " + lines[i]);
        }
        assertTrue(rendered.contains("ACTIVE INCIDENT DOSSIER"));
        assertTrue(rendered.contains("NO ACTIVE FRAUD ALERTS REQUIRING ATTENTION"));
        assertTrue(rendered.contains("All account transaction streams are nominal. Integrity OK."));
        assertTrue(rendered.contains("[1] Refresh Alert Feed"));
        assertTrue(rendered.contains("[2] Return to Administrative Console"));
        assertTrue(rendered.contains("Threat radar clear. Zero pending incidents in queue."));
    }

    @Test
    @DisplayName("Verify AuditLogScreen access restricted view conforms strictly to 82 columns")
    void testAuditLogScreenAccessRestricted() {
        int width = TUILayout.APP_WIDTH;
        assertEquals(82, width);

        String rendered = AuditLogScreen.renderAccessRestrictedContent(width);
        String[] lines = rendered.split("\n");
        for (int i = 0; i < lines.length - 1; i++) {
            assertEquals(82, TUIBox.visibleLength(lines[i]), "AuditLog access restricted line " + i + " must be 82 cols: " + lines[i]);
        }
        assertTrue(rendered.contains("DIGIBANK CORE > SYSTEM AUDIT TRAILS & FORENSIC LOGS"));
        assertTrue(rendered.contains("ACCESS CONTROL & AUDIT VAULT"));
        assertTrue(rendered.contains("ACCESS RESTRICTED: SUPER ADMIN PRIVILEGE REQUIRED"));
        assertTrue(rendered.contains("Your current staff role does not possess forensic audit clearance."));
        assertTrue(rendered.contains("This access attempt has been logged for security review."));
        assertTrue(rendered.contains("NAVIGATION"));
        assertTrue(rendered.contains("[1] Return to Main Staff Console"));
        assertTrue(rendered.contains("Clearance denied. Press [Enter] or [Esc] to return to previous menu."));
    }
}

