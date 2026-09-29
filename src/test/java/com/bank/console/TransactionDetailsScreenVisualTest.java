package com.bank.console;

import com.bank.console.components.TUIBox;
import com.bank.console.components.TUILayout;
import com.bank.console.screens.TransactionDetailView;
import com.bank.console.screens.TransactionDetailsScreen;
import com.bank.model.TransactionView;
import com.bank.model.dto.AccountDTO;
import com.bank.model.entity.Transaction;
import com.bank.model.entity.User;
import com.bank.model.enums.AccountType;
import com.bank.model.enums.Currency;
import com.bank.model.enums.TransactionDirection;
import com.bank.model.enums.TransactionType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class TransactionDetailsScreenVisualTest {

    private TransactionView createSampleTransferView() {
        Transaction tx = Transaction.builder()
                .transactionId(91L)
                .accountId(1L)
                .relatedAccountId(2L)
                .amount(new BigDecimal("100.00"))
                .transactionType(TransactionType.TRANSFER)
                .description("Fund Transfer [Exchanged 100.00 USD -> 404,891.45 KHR @ 1 USD = 4048.9100 KHR]")
                .transactionDate(LocalDateTime.of(2026, 9, 28, 13, 25, 21))
                .status(com.bank.model.enums.TransactionStatus.COMPLETED)
                .build();

        return new TransactionView(tx, TransactionDirection.OUTCOME);
    }

    private AccountDTO createSampleAccount() {
        return AccountDTO.builder()
                .accountId(1L)
                .accountNumber("DGB-429309564")
                .accountType(AccountType.CHECKING)
                .currency(Currency.USD)
                .balance(new BigDecimal("5000.00"))
                .build();
    }

    private User createSampleUser() {
        return User.builder()
                .userId(6L)
                .username("chheng")
                .fullName("Hokchheng")
                .email("chheng12@gmail.com")
                .build();
    }

    @Test
    @DisplayName("Verify Transaction Details Screen renders strict 82 columns on all lines without broken borders")
    void testStrict82ColumnsFullFrame() {
        int width = TUILayout.APP_WIDTH;
        assertEquals(82, width);

        TransactionView tv = createSampleTransferView();
        AccountDTO account = createSampleAccount();
        User user = createSampleUser();

        // 1. Initial State (feedbackMsg is null)
        String renderedInitial = TransactionDetailsScreen.renderContent(
                tv, account, user, Collections.emptyMap(), 0, null, width);
        assertNotNull(renderedInitial);

        String[] linesInitial = renderedInitial.split("\n");
        // Verify every line of the box is strictly 82 columns
        for (int i = 0; i < linesInitial.length; i++) {
            String line = linesInitial[i];
            if (i < linesInitial.length - 1) { // Lines inside the box
                assertEquals(82, TUIBox.visibleLength(line),
                        "Line " + i + " must have visible length 82: " + line);
                assertTrue(line.contains("│") || line.contains("┌") || line.contains("├") || line.contains("└"),
                        "Line " + i + " must contain valid border characters");
            }
        }

        // Verify bottom border is present and intact
        String bottomLine = linesInitial[linesInitial.length - 2];
        assertEquals(82, TUIBox.visibleLength(bottomLine));
        assertTrue(bottomLine.contains("└") && bottomLine.contains("┘"),
                "Bottom line must have closing bottom border corners └ and ┘");
    }

    @Test
    @DisplayName("Verify Export Receipt PDF status line handles long path without overflowing 82 columns")
    void testExportPdfStatusLineLengthSafety() {
        int width = TUILayout.APP_WIDTH;
        TransactionView tv = createSampleTransferView();
        AccountDTO account = createSampleAccount();
        User user = createSampleUser();

        // Simulated long output path from statement export
        String rawOutputPath = "statements\\statement_DGB-429309564_1790580105317.pdf";
        String truncatedPath = TransactionDetailsScreen.formatExportPath(rawOutputPath, 50);
        assertTrue(truncatedPath.length() <= 50, "Truncated path must be <= 50 chars");

        String feedbackMsg = "[✓] Receipt exported: " + truncatedPath;

        String rendered = TransactionDetailsScreen.renderContent(
                tv, account, user, Collections.emptyMap(), 1, feedbackMsg, width);

        String[] lines = rendered.split("\n");
        for (int i = 0; i < lines.length - 1; i++) {
            String line = lines[i];
            assertEquals(82, TUIBox.visibleLength(line),
                    "Export status frame line " + i + " must be strictly 82 columns: " + line);
            String stripped = TUIBox.stripAnsi(line);
            assertTrue(stripped.startsWith("│") || stripped.startsWith("┌") || stripped.startsWith("├") || stripped.startsWith("└"),
                    "Line " + i + " must start with border: " + stripped);
            assertTrue(stripped.endsWith("│") || stripped.endsWith("┐") || stripped.endsWith("┤") || stripped.endsWith("┘"),
                    "Line " + i + " must end with border: " + stripped);
        }

        // Verify status line content
        assertTrue(TUIBox.stripAnsi(rendered).contains("[✓] Receipt exported:"),
                "Rendered screen must contain checkmark receipt export");
    }

    @Test
    @DisplayName("Verify path truncation preserves filename and directory cleanly")
    void testPathTruncationHelper() {
        // Case 1: Standard timestamped output path
        String path1 = "statements\\statement_DGB-429309564_1790580105317.pdf";
        String res1 = TransactionDetailsScreen.formatExportPath(path1, 50);
        assertTrue(res1.length() <= 50);
        assertEquals("...\\statements\\statement_DGB-429309564.pdf", res1);

        // Case 2: Short path
        String path2 = "statements/receipt.pdf";
        String res2 = TransactionDetailsScreen.formatExportPath(path2, 50);
        assertTrue(res2.length() <= 50);
        assertEquals(".../statements/receipt.pdf", res2);

        // Case 3: Very long nested path
        String path3 = "C:\\Users\\Admin\\AppData\\Local\\Temp\\DigiBank\\statements\\statement_DGB-429309564_9999999999999.pdf";
        String res3 = TransactionDetailsScreen.formatExportPath(path3, 45);
        assertTrue(res3.length() <= 45);
        assertTrue(res3.contains("..."));
    }

    @Test
    @DisplayName("Verify currency exchange memo formatting formats cleanly onto one line")
    void testAuditMemoFormatting() {
        // Raw transfer description with bracketed rate
        String raw = "Fund Transfer [Exchanged 100.00 USD -> 404,891.45 KHR @ 1 USD = 4048.9100 KHR]";
        String formatted = TransactionDetailsScreen.formatAuditMemo(raw);

        assertEquals("Fund Transfer: Exchanged 100.00 USD -> 404,891.45 KHR @ 4,048.91 KHR", formatted);

        // Verify wrapping doesn't split words or exchange rates
        List<String> wrapped = TransactionDetailsScreen.wrapDescription(formatted, 74);
        assertEquals(1, wrapped.size(), "Memo should fit comfortably on 1 line within 74 chars");
        assertEquals("\"Fund Transfer: Exchanged 100.00 USD -> 404,891.45 KHR @ 4,048.91 KHR\"", wrapped.get(0));
    }

    @Test
    @DisplayName("Verify action button toggling preserves strict 82 column width")
    void testActionButtonToggling() {
        int width = TUILayout.APP_WIDTH;
        TransactionView tv = createSampleTransferView();
        AccountDTO account = createSampleAccount();
        User user = createSampleUser();

        // Action 0 focused: [1] Return to Ledger
        String rendered0 = TransactionDetailsScreen.renderContent(
                tv, account, user, Collections.emptyMap(), 0, null, width);
        for (String line : rendered0.split("\n")) {
            if (line.contains("[1] Return to Ledger")) {
                assertEquals(82, TUIBox.visibleLength(line));
            }
        }

        // Action 1 focused: [2] Export Receipt (PDF)
        String rendered1 = TransactionDetailsScreen.renderContent(
                tv, account, user, Collections.emptyMap(), 1, null, width);
        for (String line : rendered1.split("\n")) {
            if (line.contains("[2] Export Receipt (PDF)")) {
                assertEquals(82, TUIBox.visibleLength(line));
            }
        }
    }

    @Test
    @DisplayName("Verify TransactionDetailView delegator compatibility")
    void testTransactionDetailViewDelegator() {
        TransactionView tv = createSampleTransferView();
        AccountDTO account = createSampleAccount();
        User user = createSampleUser();

        String rendered = TransactionDetailView.render(
                tv, account, user, Collections.emptyMap(), 0, null, 82);
        assertNotNull(rendered);
        assertTrue(rendered.contains("TRANSACTION SUMMARY"));
        assertTrue(rendered.contains("AUDIT & LEDGER MEMO"));
    }
}
