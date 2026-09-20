package com.bank.model.repository;

import com.bank.model.entity.Account;

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

    /**
     * Inserts a new account within an existing transaction/connection.
     */
    Account saveWithConnection(Connection conn, Account account) throws SQLException;

    List<com.bank.model.dto.GlobalLedgerItem> findGlobalLedger(int offset, int limit, String search, com.bank.model.enums.Currency currency);
    long countAccounts(String search, com.bank.model.enums.Currency currency);
    java.util.Map<com.bank.model.enums.Currency, java.math.BigDecimal> getVaultTotalAssets();
}