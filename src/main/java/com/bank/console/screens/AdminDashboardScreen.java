package com.bank.console.screens;

import com.bank.console.ControllerFactory;
import com.bank.console.ScreenNavigator;
import com.bank.console.TUISession;
import com.bank.controller.AdminController;
import com.bank.controller.LoanController;

/**
 * SCREEN 10: ADMIN DASHBOARD (STAFF PORTAL) (82 Columns)
 * Delegates to StaffDashboardScreen for role-based routing across LOAN_OFFICER,
 * COMPLIANCE_OFFICER, and SUPER_ADMIN.
 */
public class AdminDashboardScreen implements Screen {
    private final StaffDashboardScreen delegate;

    public AdminDashboardScreen() {
        this(ControllerFactory.getAdminController(), ControllerFactory.getLoanController());
    }

    public AdminDashboardScreen(AdminController adminController, LoanController loanController) {
        this.delegate = new StaffDashboardScreen(adminController, loanController);
    }

    @Override
    public void render(ScreenNavigator navigator, TUISession session) {
        delegate.render(navigator, session);
    }
}
