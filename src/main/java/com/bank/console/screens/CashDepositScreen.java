package com.bank.console.screens;

import com.bank.controller.AccountController;
import com.bank.controller.CategoryController;

/**
 * Alias / wrapper for DepositScreen providing CashDepositScreen identity.
 */
public class CashDepositScreen extends DepositScreen {

    public CashDepositScreen() {
        super();
    }

    public CashDepositScreen(AccountController accountController) {
        super(accountController);
    }

    public CashDepositScreen(AccountController accountController, CategoryController categoryController) {
        super(accountController, categoryController);
    }
}
