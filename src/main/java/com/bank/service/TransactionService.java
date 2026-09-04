package com.bank.service;

import com.bank.model.enums.HistoryFilter;
import com.bank.model.enums.TransactionDirection;
import com.bank.exception.AccountNotFoundException;
import com.bank.exception.UnauthorizedException;
import com.bank.model.entity.Account;
import com.bank.model.entity.Transaction;
import com.bank.model.TransactionView;
import com.bank.model.entity.User;
import com.bank.model.repository.AccountRepository;
import com.bank.model.repository.TransactionRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

public class TransactionService {

    private final TransactionRepository transactionRepository;
    private final AccountRepository accountRepository;

    public TransactionService(TransactionRepository transactionRepository,
                              AccountRepository accountRepository) {
        this.transactionRepository = transactionRepository;
        this.accountRepository = accountRepository;
    }

    /**
     * Returns the transaction history for an account, with each transaction
     * tagged as INCOME or OUTCOME from that account's point of view.
     * A TRANSFER is INCOME if this account is the receiver, OUTCOME if it's the sender.
     */
    public List<TransactionView> getTransactionHistory(Long accountId, HistoryFilter filter, User requestingUser) {
        assertOwnership(accountId, requestingUser);

        List<Transaction> transactions = transactionRepository.findHistoryForAccount(accountId);

        return transactions.stream()
                .map(txn -> new TransactionView(txn, resolveDirection(txn, accountId)))
                .filter(view -> matchesFilter(view, filter))
                .collect(Collectors.toList());
    }

    /**
     * Same as above, but restricted to a date range.
     */
    public List<TransactionView> getTransactionHistory(Long accountId, HistoryFilter filter,
                                                       LocalDateTime from, LocalDateTime to,
                                                       User requestingUser) {
        return getTransactionHistory(accountId, filter, requestingUser).stream()
                .filter(view -> {
                    LocalDateTime date = view.getTransaction().getTransactionDate();
                    return !date.isBefore(from) && !date.isAfter(to);
                })
                .collect(Collectors.toList());
    }

    private TransactionDirection resolveDirection(Transaction txn, Long accountId) {
        return switch (txn.getTransactionType()) {
            case DEPOSIT, LOAN_DISBURSEMENT -> TransactionDirection.INCOME;
            case WITHDRAWAL, PAYMENT, LOAN_REPAYMENT -> TransactionDirection.OUTCOME;
            case TRANSFER -> accountId.equals(txn.getRelatedAccountId())
                    ? TransactionDirection.INCOME
                    : TransactionDirection.OUTCOME;
        };
    }

    private boolean matchesFilter(TransactionView view, HistoryFilter filter) {
        return switch (filter) {
            case ALL -> true;
            case INCOME -> view.getDirection() == TransactionDirection.INCOME;
            case OUTCOME -> view.getDirection() == TransactionDirection.OUTCOME;
        };
    }

    private void assertOwnership(Long accountId, User requestingUser) {
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new AccountNotFoundException("Account not found: " + accountId));

        if (!account.getUserId().equals(requestingUser.getUserId())) {
            throw new UnauthorizedException("You do not have access to this account's history");
        }
    }
}