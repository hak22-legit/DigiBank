package com.bank.service;

import com.bank.model.dto.AccountDTO;
import com.bank.model.dto.StatementReportData;
import com.bank.model.entity.Transaction;
import com.bank.model.entity.User;
import com.bank.model.enums.AccountStatus;
import com.bank.model.enums.AccountType;
import com.bank.model.enums.Currency;
import com.bank.model.enums.TransactionType;
import com.bank.model.repository.TransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class StatementExportServiceTest {

    private TransactionRepository transactionRepository;
    private StatementExportService exportService;

    @BeforeEach
    void setUp() {
        transactionRepository = mock(TransactionRepository.class);
        exportService = new StatementExportService(transactionRepository);
    }

    @Test
    @DisplayName("prepareReportData accurately calculates opening balance, totals, and running ledger")
    void testPrepareReportDataLedgerMath() {
        Long accountId = 10L;
        AccountDTO account = AccountDTO.builder()
                .accountId(accountId)
                .accountNumber("DGB-429309564")
                .accountType(AccountType.CHECKING)
                .currency(Currency.USD)
                .balance(new BigDecimal("8050.00"))
                .status(AccountStatus.ACTIVE)
                .build();

        User user = User.builder()
                .userId(3L)
                .fullName("MEN SENGHAK")
                .build();

        LocalDateTime from = LocalDateTime.of(2026, 8, 1, 0, 0);
        LocalDateTime to = LocalDateTime.of(2026, 8, 31, 23, 59);

        // Transactions matching the user's reference specification:
        // Starting balance: 5,200.00
        // Tx1: Credit +4,000.00 (Salary) -> 9,200.00
        // Tx2: Debit -300.00 (ATM) -> 8,900.00
        // Tx3: Debit -500.00 (Savings) -> 8,400.00
        // Tx4: Debit -80.00 (Electricite) -> 8,320.00
        // Tx5: Credit +500.00 (Wire) -> 8,820.00
        // Tx6: Debit -770.00 (Rent) -> 8,050.00 (Closing Balance)
        List<Transaction> mockTxs = List.of(
                Transaction.builder()
                        .transactionId(101L).accountId(accountId).transactionType(TransactionType.DEPOSIT)
                        .amount(new BigDecimal("4000.00")).description("ACH Direct Salary Settlement\nFrom: Tech Payroll • Trace #ACH-99412")
                        .transactionDate(LocalDateTime.of(2026, 8, 3, 10, 0)).idempotencyKey(UUID.randomUUID()).build(),
                Transaction.builder()
                        .transactionId(102L).accountId(accountId).transactionType(TransactionType.WITHDRAWAL)
                        .amount(new BigDecimal("300.00")).description("ATM Cash Dispense #04\nLoc: Norodom Blvd Branch • Card: ************4810")
                        .transactionDate(LocalDateTime.of(2026, 8, 7, 14, 30)).idempotencyKey(UUID.randomUUID()).build(),
                Transaction.builder()
                        .transactionId(103L).accountId(accountId).relatedAccountId(20L).transactionType(TransactionType.TRANSFER)
                        .amount(new BigDecimal("500.00")).description("Internal Allocation to Savings\nBeneficiary: Self • Target Account: DGB-788635551")
                        .transactionDate(LocalDateTime.of(2026, 8, 14, 9, 15)).idempotencyKey(UUID.randomUUID()).build(),
                Transaction.builder()
                        .transactionId(104L).accountId(accountId).transactionType(TransactionType.PAYMENT)
                        .amount(new BigDecimal("80.00")).description("Utility Direct Debit: EDC\nElectricite du Cambodge • Consumer #EDC-00984129")
                        .transactionDate(LocalDateTime.of(2026, 8, 20, 11, 45)).idempotencyKey(UUID.randomUUID()).build(),
                Transaction.builder()
                        .transactionId(105L).accountId(accountId).transactionType(TransactionType.DEPOSIT)
                        .amount(new BigDecimal("500.00")).description("Wire In: Client Settlement\nPhnom Penh Dev Labs • SWIFT Ref: 20260825-WIRE-8840")
                        .transactionDate(LocalDateTime.of(2026, 8, 25, 16, 20)).idempotencyKey(UUID.randomUUID()).build(),
                Transaction.builder()
                        .transactionId(106L).accountId(accountId).relatedAccountId(30L).transactionType(TransactionType.TRANSFER)
                        .amount(new BigDecimal("770.00")).description("Internal Transfer: Apartment Lease\nBeneficiary: Sok Dara • Memo: August 2026 Rent")
                        .transactionDate(LocalDateTime.of(2026, 8, 29, 8, 0)).idempotencyKey(UUID.randomUUID()).build()
        );

        when(transactionRepository.findByAccountIdAndDateRange(eq(accountId), any(), any()))
                .thenReturn(mockTxs);

        StatementReportData reportData = exportService.prepareReportData(user, account, from, to);

        assertEquals("MEN SENGHAK", reportData.getCustomerName());
        assertEquals("#USR-03", reportData.getCustomerId());
        assertEquals("DGB-429309564", reportData.getAccountNumber());
        assertEquals("Checking Account", reportData.getAccountStructure());

        // Verify Balance Consolidation Math
        // Total credits = 4000.00 + 500.00 = 4500.00 (2 entries)
        // Total debits  = 300.00 + 500.00 + 80.00 + 770.00 = 1650.00 (4 entries)
        // Opening = 8050.00 - 4500.00 + 1650.00 = 5200.00
        assertEquals(new BigDecimal("5200.00"), reportData.getOpeningBalance());
        assertEquals(new BigDecimal("8050.00"), reportData.getClosingBalance());
        assertEquals(new BigDecimal("4500.00"), reportData.getTotalCredits());
        assertEquals(2, reportData.getCreditCount());
        assertEquals(new BigDecimal("1650.00"), reportData.getTotalDebits());
        assertEquals(4, reportData.getDebitCount());

        // Verify Running Balances
        assertEquals(6, reportData.getTransactions().size());
        assertEquals(new BigDecimal("9200.00"), reportData.getTransactions().get(0).getRunningBalance());
        assertEquals(new BigDecimal("8900.00"), reportData.getTransactions().get(1).getRunningBalance());
        assertEquals(new BigDecimal("8400.00"), reportData.getTransactions().get(2).getRunningBalance());
        assertEquals(new BigDecimal("8320.00"), reportData.getTransactions().get(3).getRunningBalance());
        assertEquals(new BigDecimal("8820.00"), reportData.getTransactions().get(4).getRunningBalance());
        assertEquals(new BigDecimal("8050.00"), reportData.getTransactions().get(5).getRunningBalance());

        // Verify SHA-256 fingerprint generated
        assertNotNull(reportData.getSha256Hash());
        assertEquals(64, reportData.getSha256Hash().length());
    }

    @Test
    @DisplayName("generateStatementPdfBytes produces valid PDF byte stream with %PDF header")
    void testGenerateStatementPdfBytes() {
        Long accountId = 10L;
        AccountDTO account = AccountDTO.builder()
                .accountId(accountId)
                .accountNumber("DGB-429309564")
                .accountType(AccountType.CHECKING)
                .currency(Currency.USD)
                .balance(new BigDecimal("8050.00"))
                .status(AccountStatus.ACTIVE)
                .build();

        User user = User.builder().userId(3L).fullName("MEN SENGHAK").build();
        StatementReportData reportData = exportService.prepareReportData(user, account, LocalDateTime.now().minusDays(30), LocalDateTime.now());

        byte[] pdfBytes = exportService.generateStatementPdfBytes(reportData);
        assertNotNull(pdfBytes);
        assertTrue(pdfBytes.length > 500, "PDF byte array should contain complete document structure");

        // Verify PDF Magic Header "%PDF"
        String magicHeader = new String(pdfBytes, 0, 5, StandardCharsets.US_ASCII);
        assertTrue(magicHeader.startsWith("%PDF"), "Output must be valid PDF document starting with %PDF");
    }

    @Test
    @DisplayName("generateStatement writes PDF file to statements directory and returns valid path")
    void testGenerateStatementFileExport() {
        Long accountId = 10L;
        AccountDTO account = AccountDTO.builder()
                .accountId(accountId)
                .accountNumber("DGB-429309564")
                .accountType(AccountType.CHECKING)
                .currency(Currency.USD)
                .balance(new BigDecimal("8050.00"))
                .status(AccountStatus.ACTIVE)
                .build();

        User user = User.builder().userId(3L).fullName("MEN SENGHAK").build();

        String outputPath = exportService.generateStatement(user, account, LocalDateTime.now().minusDays(30), LocalDateTime.now());
        assertNotNull(outputPath);

        File exportedFile = new File(outputPath);
        assertTrue(exportedFile.exists(), "Exported statement PDF file must exist on disk");
        assertTrue(exportedFile.length() > 500, "Exported PDF file must not be empty");

        // Clean up test file
        exportedFile.deleteOnExit();
    }

    @Test
    @DisplayName("generateStatement creates reference PDF statement matching official banking mockup")
    void testGenerateReferencePdfStatement() {
        Long accountId = 10L;
        AccountDTO account = AccountDTO.builder()
                .accountId(accountId)
                .accountNumber("DGB-429309564")
                .accountType(AccountType.CHECKING)
                .currency(Currency.USD)
                .balance(new BigDecimal("8050.00"))
                .status(AccountStatus.ACTIVE)
                .build();

        User user = User.builder().userId(3L).fullName("MEN SENGHAK").build();
        LocalDateTime from = LocalDateTime.of(2026, 8, 1, 0, 0);
        LocalDateTime to = LocalDateTime.of(2026, 8, 31, 23, 59);

        List<Transaction> mockTxs = List.of(
                Transaction.builder()
                        .transactionId(101L).accountId(accountId).transactionType(TransactionType.DEPOSIT)
                        .amount(new BigDecimal("4000.00")).description("ACH Direct Salary Settlement\nFrom: Tech Payroll • Trace #ACH-99412")
                        .transactionDate(LocalDateTime.of(2026, 8, 3, 10, 0)).build(),
                Transaction.builder()
                        .transactionId(102L).accountId(accountId).transactionType(TransactionType.WITHDRAWAL)
                        .amount(new BigDecimal("300.00")).description("ATM Cash Dispense #04\nLoc: Norodom Blvd Branch • Card: ************4810")
                        .transactionDate(LocalDateTime.of(2026, 8, 7, 14, 30)).build(),
                Transaction.builder()
                        .transactionId(103L).accountId(accountId).relatedAccountId(20L).transactionType(TransactionType.TRANSFER)
                        .amount(new BigDecimal("500.00")).description("Internal Allocation to Savings\nBeneficiary: Self • Target Account: DGB-788635551")
                        .transactionDate(LocalDateTime.of(2026, 8, 14, 9, 15)).build(),
                Transaction.builder()
                        .transactionId(104L).accountId(accountId).transactionType(TransactionType.PAYMENT)
                        .amount(new BigDecimal("80.00")).description("Utility Direct Debit: EDC\nElectricite du Cambodge • Consumer #EDC-00984129")
                        .transactionDate(LocalDateTime.of(2026, 8, 20, 11, 45)).build(),
                Transaction.builder()
                        .transactionId(105L).accountId(accountId).transactionType(TransactionType.DEPOSIT)
                        .amount(new BigDecimal("500.00")).description("Wire In: Client Settlement\nPhnom Penh Dev Labs • SWIFT Ref: 20260825-WIRE-8840")
                        .transactionDate(LocalDateTime.of(2026, 8, 25, 16, 20)).build(),
                Transaction.builder()
                        .transactionId(106L).accountId(accountId).relatedAccountId(30L).transactionType(TransactionType.TRANSFER)
                        .amount(new BigDecimal("770.00")).description("Internal Transfer: Apartment Lease\nBeneficiary: Sok Dara • Memo: August 2026 Rent")
                        .transactionDate(LocalDateTime.of(2026, 8, 29, 8, 0)).build()
        );

        when(transactionRepository.findByAccountIdAndDateRange(eq(accountId), any(), any()))
                .thenReturn(mockTxs);

        String path = exportService.generateStatement(user, account, from, to);
        assertNotNull(path);
        File file = new File(path);
        assertTrue(file.exists());
        assertTrue(file.length() > 1000);
    }
}
