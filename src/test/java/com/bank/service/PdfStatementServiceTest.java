package com.bank.service;

import com.bank.console.screens.TransactionDetailsScreen;
import com.bank.controller.ReportController;
import com.bank.model.TransactionView;
import com.bank.model.dto.AccountDTO;
import com.bank.model.entity.Account;
import com.bank.model.entity.Transaction;
import com.bank.model.entity.User;
import com.bank.model.enums.AccountType;
import com.bank.model.enums.Currency;
import com.bank.model.enums.TransactionDirection;
import com.bank.model.enums.TransactionStatus;
import com.bank.model.enums.TransactionType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

public class PdfStatementServiceTest {

    private PdfStatementService pdfStatementService;
    private ReportController reportController;
    private User testUser;
    private Account testAccount;
    private AccountDTO testAccountDTO;
    private Transaction testTransaction;

    @BeforeEach
    void setUp() {
        pdfStatementService = new PdfStatementService();
        reportController = new ReportController(null, null, pdfStatementService);

        testUser = User.builder()
                .userId(6L)
                .username("chheng")
                .fullName("Hokchheng")
                .email("chheng12@gmail.com")
                .build();

        testAccount = Account.builder()
                .accountId(1L)
                .userId(6L)
                .accountNumber("DGB-429309564")
                .accountType(AccountType.CHECKING)
                .currency(Currency.USD)
                .balance(new BigDecimal("5000.00"))
                .build();

        testAccountDTO = AccountDTO.builder()
                .accountId(1L)
                .accountNumber("DGB-429309564")
                .accountType(AccountType.CHECKING)
                .currency(Currency.USD)
                .balance(new BigDecimal("5000.00"))
                .build();

        testTransaction = Transaction.builder()
                .transactionId(92L)
                .accountId(1L)
                .relatedAccountId(2L)
                .amount(new BigDecimal("100.00"))
                .currency(Currency.USD)
                .transactionType(TransactionType.TRANSFER)
                .description("Fund Transfer: Exchanged 100.00 USD -> 404,891.45 KHR @ 4,048.91 KHR")
                .transactionDate(LocalDateTime.of(2026, 9, 28, 13, 25, 21))
                .status(TransactionStatus.COMPLETED)
                .build();
    }

    @Test
    @DisplayName("Verify Transaction entity generates standard reference ID")
    void testTransactionReferenceId() {
        String refId = testTransaction.getReferenceId();
        assertEquals("TXN-20260928-00092", refId);
    }

    @Test
    @DisplayName("Verify generateTransactionReceiptPdf for Account entity generates valid PDF file")
    void testGenerateTransactionReceiptPdfForAccount() {
        File receiptFile = pdfStatementService.generateTransactionReceiptPdf(testTransaction, testAccount, testUser);

        assertNotNull(receiptFile);
        assertTrue(receiptFile.exists(), "Receipt PDF file must be created on disk");
        assertTrue(receiptFile.length() > 1000, "Receipt PDF must contain non-empty bytes");
        assertEquals("receipt_TXN-20260928-00092.pdf", receiptFile.getName());
    }

    @Test
    @DisplayName("Verify generateTransactionReceiptPdf for AccountDTO generates valid PDF file")
    void testGenerateTransactionReceiptPdfForAccountDTO() {
        File receiptFile = pdfStatementService.generateTransactionReceiptPdf(testTransaction, testAccountDTO, testUser);

        assertNotNull(receiptFile);
        assertTrue(receiptFile.exists());
        assertTrue(receiptFile.length() > 1000);
        assertEquals("receipt_TXN-20260928-00092.pdf", receiptFile.getName());
    }

    @Test
    @DisplayName("Verify ReportController generateReceipt delegates to PdfStatementService")
    void testReportControllerGenerateReceipt() {
        String path = reportController.generateReceipt(testTransaction, testAccountDTO, testUser);

        assertNotNull(path);
        assertTrue(path.contains("receipt_TXN-20260928-00092.pdf"));
        File f = new File(path);
        assertTrue(f.exists());
    }

    @Test
    @DisplayName("Verify TransactionDetailsScreen.executeReceiptExport produces focused single receipt instead of full statement")
    void testTransactionDetailsScreenExecuteReceiptExport() {
        TransactionView tv = new TransactionView(testTransaction, TransactionDirection.OUTCOME);

        String feedback = TransactionDetailsScreen.executeReceiptExport(reportController, testUser, testAccountDTO, tv);

        assertNotNull(feedback);
        assertTrue(feedback.contains("[✓] Receipt exported:"), "Feedback must indicate successful receipt export");
        assertTrue(feedback.contains("receipt_TXN-20260928-00092.pdf"),
                "Feedback must refer to single transaction receipt, not full account statement");
        assertFalse(feedback.contains("statement_DGB"),
                "Feedback must NOT refer to a full statement");
    }
}
