package com.bank.util;

import com.bank.model.dto.StatementReportData;
import com.bank.model.dto.StatementTransactionItem;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AuditCryptoUtilTest {

    @Test
    @DisplayName("sha256 computes standard 64-character lowercase hex digest")
    void testStandardSha256Digest() {
        // Known SHA-256 for empty string
        String emptyHash = AuditCryptoUtil.sha256("");
        assertEquals("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855", emptyHash);

        // Known SHA-256 for "hello world"
        String helloHash = AuditCryptoUtil.sha256("hello world");
        assertEquals("b94d27b9934d3e08a52e52d7da7dabfac484efe37a5380ee9088f7ace2efcde9", helloHash);

        // Null handling
        assertEquals(emptyHash, AuditCryptoUtil.sha256((String) null));
    }

    @Test
    @DisplayName("calculateLedgerHash is deterministic for identical statement data")
    void testLedgerHashDeterminism() {
        StatementReportData data1 = buildSampleReportData(new BigDecimal("5200.00"), new BigDecimal("8050.00"));
        StatementReportData data2 = buildSampleReportData(new BigDecimal("5200.00"), new BigDecimal("8050.00"));

        String hash1 = AuditCryptoUtil.calculateLedgerHash(data1);
        String hash2 = AuditCryptoUtil.calculateLedgerHash(data2);

        assertNotNull(hash1);
        assertEquals(64, hash1.length(), "SHA-256 hex string must be 64 characters long");
        assertEquals(hash1, hash2, "Identical statement data must yield identical ledger hash");
    }

    @Test
    @DisplayName("calculateLedgerHash detects tampering in balances or transactions")
    void testLedgerHashTamperSensitivity() {
        StatementReportData original = buildSampleReportData(new BigDecimal("5200.00"), new BigDecimal("8050.00"));
        String originalHash = AuditCryptoUtil.calculateLedgerHash(original);

        // Tamper opening balance
        StatementReportData tamperedBalance = buildSampleReportData(new BigDecimal("5200.01"), new BigDecimal("8050.00"));
        String tamperedBalanceHash = AuditCryptoUtil.calculateLedgerHash(tamperedBalance);
        assertNotEquals(originalHash, tamperedBalanceHash, "Hash must change when balance is tampered");

        // Tamper transaction item
        StatementReportData tamperedTx = buildSampleReportData(new BigDecimal("5200.00"), new BigDecimal("8050.00"));
        tamperedTx.getTransactions().get(0).setCreditAmount(new BigDecimal("4000.01"));
        String tamperedTxHash = AuditCryptoUtil.calculateLedgerHash(tamperedTx);
        assertNotEquals(originalHash, tamperedTxHash, "Hash must change when transaction amount is modified");
    }

    private StatementReportData buildSampleReportData(BigDecimal opening, BigDecimal closing) {
        StatementTransactionItem item1 = StatementTransactionItem.builder()
                .postingDate(LocalDate.of(2026, 8, 3))
                .valueDate(LocalDate.of(2026, 8, 3))
                .title("ACH Direct Salary Settlement")
                .memo("From: Tech Payroll • Trace #ACH-99412")
                .code("ACH_CR")
                .creditAmount(new BigDecimal("4000.00"))
                .runningBalance(new BigDecimal("9200.00"))
                .build();

        StatementTransactionItem item2 = StatementTransactionItem.builder()
                .postingDate(LocalDate.of(2026, 8, 7))
                .valueDate(LocalDate.of(2026, 8, 7))
                .title("ATM Cash Dispense #04")
                .memo("Loc: Norodom Blvd Branch • Card: ************4810")
                .code("CSH_WDL")
                .debitAmount(new BigDecimal("300.00"))
                .runningBalance(new BigDecimal("8900.00"))
                .build();

        return StatementReportData.builder()
                .customerName("MEN SENGHAK")
                .customerId("#USR-03")
                .assignedBranch("Head Office")
                .accountNumber("DGB-429309564")
                .accountStructure("Checking Account")
                .currency("USD")
                .currencySymbol("$ ")
                .standingStatus("ACTIVE / Cleared")
                .statementDate(LocalDate.of(2026, 9, 1))
                .startDate(LocalDate.of(2026, 8, 1))
                .endDate(LocalDate.of(2026, 8, 31))
                .openingBalance(opening)
                .closingBalance(closing)
                .totalCredits(new BigDecimal("4000.00"))
                .creditCount(1)
                .totalDebits(new BigDecimal("300.00"))
                .debitCount(1)
                .transactions(List.of(item1, item2))
                .build();
    }
}
