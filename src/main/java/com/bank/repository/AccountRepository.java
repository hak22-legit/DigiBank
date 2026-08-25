package com.bank.repository;

import com.bank.model.Account;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

public interface AccountRepository {
    Optional<Account> findById(Long accountId);
    Optional<Account> findByAccountNumber(String accountNumber);
    List<Account> findByUserId(Long userId);
    List<Account> findAll();
    Account save(Account account);
    boolean deleteById(Long accountId);

    /**
     * Locks the account row for update within an existing transaction.
     * MUST be called with autoCommit = false on the given connection.
     * Used by Deposit/Withdrawal/Transfer services (Phase 8-10).
     */
    Optional<Account> findByIdForUpdate(Connection conn, Long accountId) throws SQLException;

    /**
     * Updates balance + status within an existing transaction/connection.
     * Used by Deposit/Withdrawal/Transfer services.
     */
    void updateWithConnection(Connection conn, Account account) throws SQLException;
}