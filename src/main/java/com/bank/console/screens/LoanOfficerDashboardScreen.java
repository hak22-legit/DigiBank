package com.bank.console.screens;

import com.bank.console.ControllerFactory;
import com.bank.controller.AdminController;
import com.bank.controller.LoanController;

/**
 * Console screen alias for LoanOfficerDashboardScreen.
 */
public class LoanOfficerDashboardScreen extends com.bank.ui.screens.LoanOfficerDashboardScreen {

    public LoanOfficerDashboardScreen() {
        super();
    }

    public LoanOfficerDashboardScreen(AdminController adminController, LoanController loanController) {
        super(adminController, loanController);
    }
}
