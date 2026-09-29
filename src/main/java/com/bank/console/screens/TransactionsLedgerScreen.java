package com.bank.console.screens;

import com.bank.controller.AccountController;
import com.bank.controller.ReportController;
import com.bank.controller.TransactionController;

/**
 * SCREEN 7: TRANSACTIONS LEDGER (82 Columns)
 * Alias and extension for TransactionHistoryScreen to support TransactionsLedgerScreen naming.
 */
public class TransactionsLedgerScreen extends TransactionHistoryScreen {

    public TransactionsLedgerScreen() {
        super();
    }

    public TransactionsLedgerScreen(TransactionController transactionController) {
        super(transactionController);
    }

    public TransactionsLedgerScreen(TransactionController transactionController, AccountController accountController) {
        super(transactionController, accountController);
    }

    public TransactionsLedgerScreen(TransactionController transactionController, AccountController accountController, ReportController reportController) {
        super(transactionController, accountController, reportController);
    }
}
