package com.bank.console.screens;

import com.bank.controller.AccountController;
import com.bank.controller.LoanController;

/**
 * Screen alias for LoanScreen meeting enterprise specifications.
 */
public class LoanManagementScreen extends LoanScreen {

    public LoanManagementScreen() {
        super();
    }

    public LoanManagementScreen(LoanController loanController) {
        super(loanController);
    }

    public LoanManagementScreen(LoanController loanController, AccountController accountController) {
        super(loanController, accountController);
    }
}
