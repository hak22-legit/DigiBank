package com.bank.console.screens;

import com.bank.controller.AccountController;
import com.bank.controller.LoanController;

/**
 * Screen alias for LoanScreen meeting enterprise specifications:
 * LoanManagementAndRepaymentsScreen.
 */
public class LoanManagementAndRepaymentsScreen extends LoanScreen {

    public LoanManagementAndRepaymentsScreen() {
        super();
    }

    public LoanManagementAndRepaymentsScreen(LoanController loanController) {
        super(loanController);
    }

    public LoanManagementAndRepaymentsScreen(LoanController loanController, AccountController accountController) {
        super(loanController, accountController);
    }
}
