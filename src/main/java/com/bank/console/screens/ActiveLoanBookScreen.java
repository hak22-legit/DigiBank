package com.bank.console.screens;

import com.bank.console.ControllerFactory;
import com.bank.console.ScreenNavigator;
import com.bank.console.TUISession;
import com.bank.controller.AdminController;
import com.bank.controller.LoanController;
import com.bank.model.entity.Loan;

import java.util.List;

/**
 * LOAN OFFICER > PORTFOLIO PERFORMANCE & ACTIVE LOAN BOOK (82 Columns)
 * Alias and compatibility bridge delegating to PortfolioPerformanceScreen.
 */
public class ActiveLoanBookScreen implements Screen {

    private final PortfolioPerformanceScreen delegate;

    public ActiveLoanBookScreen() {
        this(ControllerFactory.getAdminController(), ControllerFactory.getLoanController());
    }

    public ActiveLoanBookScreen(AdminController adminController, LoanController loanController) {
        this.delegate = new PortfolioPerformanceScreen(adminController, loanController);
    }

    @Override
    public void render(ScreenNavigator navigator, TUISession session) {
        delegate.render(navigator, session);
    }

    public static String renderContent(List<Loan> activeLoans, String statusMessage, boolean isError, int width) {
        return PortfolioPerformanceScreen.renderContent(activeLoans, statusMessage, isError, width);
    }

    public static String renderContent(List<Loan> activeLoans, int selectedIndex, boolean sortDesc,
                                       String statusMessage, boolean isError, int width) {
        return PortfolioPerformanceScreen.renderContent(activeLoans, selectedIndex, sortDesc, statusMessage, isError, width);
    }
}
