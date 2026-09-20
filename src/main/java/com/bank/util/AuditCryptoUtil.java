package com.bank.util;

import com.bank.model.dto.StatementReportData;
import com.bank.model.dto.StatementTransactionItem;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Cryptographic utility for calculating SHA-256 digital audit fingerprints
 * to ensure non-tampering and ledger record integrity for DigiBank statements.
 */
public final class AuditCryptoUtil {

    private static final String DIGEST_ALGORITHM = "SHA-256";
    private static final String ENGINE_SIGNATURE = "DIGIBANK-ZERO-TRUST-LEDGER-V1.4.2";

    private AuditCryptoUtil() {}

    /**
     * Calculates the standard SHA-256 hash of a string input in UTF-8.
     *
     * @param input Raw string to digest
     * @return 64-character lowercase hexadecimal hash string
     */
    public static String sha256(String input) {
        if (input == null) {
            input = "";
        }
        return sha256(input.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Calculates the standard SHA-256 hash of byte array input.
     *
     * @param data Raw bytes to digest
     * @return 64-character lowercase hexadecimal hash string
     */
    public static String sha256(byte[] data) {
        if (data == null) {
            data = new byte[0];
        }
        try {
            MessageDigest digest = MessageDigest.getInstance(DIGEST_ALGORITHM);
            byte[] hashBytes = digest.digest(data);
            StringBuilder hexString = new StringBuilder();
            for (byte b : hashBytes) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 digest algorithm unavailable", e);
        }
    }

    /**
     * Computes the deterministic SHA-256 cryptographic audit fingerprint for an account statement.
     * Incorporates account metadata, statement period, starting/closing balances, and all ledger postings.
     *
     * @param data The populated StatementReportData
     * @return 64-character lowercase hexadecimal ledger hash
     */
    public static String calculateLedgerHash(StatementReportData data) {
        if (data == null) {
            return sha256(ENGINE_SIGNATURE);
        }

        StringBuilder canonical = new StringBuilder();
        canonical.append(ENGINE_SIGNATURE).append("::");
        canonical.append("ACC:").append(data.getAccountNumber() != null ? data.getAccountNumber() : "").append("::");
        canonical.append("CUST:").append(data.getCustomerId() != null ? data.getCustomerId() : "").append("::");
        canonical.append("PERIOD:").append(data.getStartDate()).append("->").append(data.getEndDate()).append("::");
        canonical.append("OPEN:").append(data.getOpeningBalance() != null ? data.getOpeningBalance().toPlainString() : "0.00").append("::");
        canonical.append("CLOSE:").append(data.getClosingBalance() != null ? data.getClosingBalance().toPlainString() : "0.00").append("::");
        canonical.append("CR_TOT:").append(data.getTotalCredits() != null ? data.getTotalCredits().toPlainString() : "0.00").append("::");
        canonical.append("CR_CNT:").append(data.getCreditCount()).append("::");
        canonical.append("DB_TOT:").append(data.getTotalDebits() != null ? data.getTotalDebits().toPlainString() : "0.00").append("::");
        canonical.append("DB_CNT:").append(data.getDebitCount()).append("::");

        if (data.getTransactions() != null) {
            for (StatementTransactionItem item : data.getTransactions()) {
                canonical.append("[TX:")
                        .append(item.getPostingDate()).append("|")
                        .append(item.getCode() != null ? item.getCode() : "").append("|")
                        .append(item.getDebitAmount() != null ? item.getDebitAmount().toPlainString() : "-").append("|")
                        .append(item.getCreditAmount() != null ? item.getCreditAmount().toPlainString() : "-").append("|")
                        .append(item.getRunningBalance() != null ? item.getRunningBalance().toPlainString() : "0.00")
                        .append("]");
            }
        }

        return sha256(canonical.toString());
    }
}
