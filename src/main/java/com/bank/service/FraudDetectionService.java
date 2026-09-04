package com.bank.service;

import com.bank.model.enums.FraudStatus;
import com.bank.model.enums.RiskLevel;
import com.bank.model.entity.FraudAlert;
import com.bank.model.entity.Transaction;
import com.bank.model.enums.TransactionType;
import com.bank.model.repository.FraudAlertRepository;
import com.bank.model.repository.TransactionRepository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public class FraudDetectionService {

    private static final BigDecimal HIGH_VALUE_THRESHOLD = new BigDecimal("10000");

    private static final int VELOCITY_TIER1_COUNT = 5;
    private static final int VELOCITY_TIER1_MINUTES = 30;

    private static final int VELOCITY_TIER2_COUNT = 10;
    private static final int VELOCITY_TIER2_MINUTES = 60;

    private final FraudAlertRepository fraudAlertRepository;
    private final TransactionRepository transactionRepository;

    public FraudDetectionService(FraudAlertRepository fraudAlertRepository,
                                 TransactionRepository transactionRepository) {
        this.fraudAlertRepository = fraudAlertRepository;
        this.transactionRepository = transactionRepository;
    }

    /**
     * Runs all fraud rules against a completed transfer. This is called
     * AFTER the transfer has already committed successfully - fraud
     * detection never blocks or reverses a transaction (Option A: alert only).
     * Any alert created here is picked up later by a COMPLIANCE_OFFICER
     * (Phase 18/19) for manual investigation.
     */
    public void evaluateTransfer(Transaction transferTransaction, Long userId) {
        checkHighValue(transferTransaction, userId);
        checkVelocity(transferTransaction, userId);
    }

    /**
     * Rule 1: High-value transfer.
     * Mirrors the real-world Bank Secrecy Act CTR threshold ($10,000) used
     * by actual US financial institutions for currency transaction reporting.
     */
    private void checkHighValue(Transaction txn, Long userId) {
        if (txn.getAmount().compareTo(HIGH_VALUE_THRESHOLD) >= 0) {
            createAlert(userId, txn, RiskLevel.HIGH,
                    "High-value transfer: " + txn.getAmount() + " " + txn.getCurrency()
                            + " (threshold: " + HIGH_VALUE_THRESHOLD + ")");
        }
    }

    /**
     * Rule 2: Transaction velocity, two tiers.
     * Only applies to TRANSFER transactions (not deposit/withdrawal), since
     * rapid outgoing transfers are the classic account-takeover pattern,
     * while frequent deposits/withdrawals are normal customer behavior
     * (e.g. someone doing errands and hitting several ATMs in a day).
     */
    private void checkVelocity(Transaction txn, Long userId) {
        LocalDateTime tier2WindowStart = LocalDateTime.now().minusMinutes(VELOCITY_TIER2_MINUTES);
        List<Transaction> recentTransfers = transactionRepository
                .findByAccountIdAndDateRange(txn.getAccountId(), tier2WindowStart, LocalDateTime.now())
                .stream()
                .filter(t -> t.getTransactionType() == TransactionType.TRANSFER)
                .toList();

        long tier2Count = recentTransfers.size();

        LocalDateTime tier1WindowStart = LocalDateTime.now().minusMinutes(VELOCITY_TIER1_MINUTES);
        long tier1Count = recentTransfers.stream()
                .filter(t -> !t.getTransactionDate().isBefore(tier1WindowStart))
                .count();

        if (tier2Count >= VELOCITY_TIER2_COUNT) {
            createAlert(userId, txn, RiskLevel.HIGH,
                    tier2Count + " transfers within " + VELOCITY_TIER2_MINUTES + " minutes (threshold: "
                            + VELOCITY_TIER2_COUNT + ")");
        } else if (tier1Count >= VELOCITY_TIER1_COUNT) {
            createAlert(userId, txn, RiskLevel.MEDIUM,
                    tier1Count + " transfers within " + VELOCITY_TIER1_MINUTES + " minutes (threshold: "
                            + VELOCITY_TIER1_COUNT + ")");
        }
    }

    private void createAlert(Long userId, Transaction txn, RiskLevel riskLevel, String description) {
        FraudAlert alert = FraudAlert.builder()
                .userId(userId)
                .accountId(txn.getAccountId())
                .transactionId(txn.getTransactionId())
                .riskLevel(riskLevel)
                .status(FraudStatus.OPEN)
                .description(description)
                .build();

        fraudAlertRepository.save(alert);
    }
}