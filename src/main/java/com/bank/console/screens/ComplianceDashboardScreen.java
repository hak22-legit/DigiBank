package com.bank.console.screens;

import com.bank.console.ControllerFactory;
import com.bank.console.ScreenNavigator;
import com.bank.console.TUISession;
import com.bank.controller.AdminController;

/**
 * Compliance Officer Operations Dashboard Screen.
 */
public class ComplianceDashboardScreen implements Screen {

    private final AdminController adminController;

    public ComplianceDashboardScreen() {
        this(ControllerFactory.getAdminController());
    }

    public ComplianceDashboardScreen(AdminController adminController) {
        this.adminController = adminController;
    }

    @Override
    public void render(ScreenNavigator navigator, TUISession session) {
        StaffDashboardScreen staffDashboard = new StaffDashboardScreen(
                adminController,
                ControllerFactory.getLoanController(),
                ControllerFactory.getAuthController());
        staffDashboard.render(navigator, session);
    }
}
