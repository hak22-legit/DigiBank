package com.bank.model.repository;

import com.bank.model.entity.Transaction;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TransactionRepository {
    Optional<Transaction> findById(Long transactionId);
    Optional<Transaction> findByIdempotencyKey(UUID idempotencyKey);
    List<Transaction> findByAccountId(Long accountId);
    List<Transaction> findByAccountIdAndDateRange(Long accountId, LocalDateTime from, LocalDateTime to);
    List<Transaction> findAll();
    List<Transaction> findHistoryForAccount(Long accountId);
    Transaction save(Transaction transaction);

    /**
     * Insert within an existing transaction/connection (Phase 8-10 ACID operations).
     * If transaction.getIdempotencyKey() is null, a random UUID is auto-generated
     * (used for DEPOSIT/WITHDRAWAL where duplicate-submission risk doesn't apply).
     * For TRANSFER, the caller must supply a real caller-provided idempotency key.
     */
    Transaction saveWithConnection(Connection conn, Transaction transaction) throws SQLException;
}