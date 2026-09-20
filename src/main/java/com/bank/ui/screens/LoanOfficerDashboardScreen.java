package com.bank.ui.screens;

import com.bank.console.ControllerFactory;
import com.bank.console.ScreenNavigator;
import com.bank.console.TUISession;
import com.bank.console.screens.Screen;
import com.bank.console.screens.StaffDashboardScreen;
import com.bank.controller.AdminController;
import com.bank.controller.LoanController;
import com.bank.model.dto.AdminDTO;
import com.bank.service.LoanService.LoanPipelineStats;

/**
 * LOAN OFFICER DASHBOARD SCREEN (82 Columns)
 * Enterprise loan operations dashboard conforming strictly to 82 columns,
 * featuring strict 74 printable character selection highlight width and 50% footer opacity.
 */
public class LoanOfficerDashboardScreen implements Screen {

    private final StaffDashboardScreen delegate;

    public LoanOfficerDashboardScreen() {
        this(ControllerFactory.getAdminController(), ControllerFactory.getLoanController());
    }

    public LoanOfficerDashboardScreen(AdminController adminController, LoanController loanController) {
        this.delegate = new StaffDashboardScreen(adminController, loanController);
    }

    @Override
    public void render(ScreenNavigator navigator, TUISession session) {
        delegate.render(navigator, session);
    }

    /**
     * Prints a menu option padded strictly to 74 printable characters before applying
     * ANSI reverse video so the highlight spans from left to right border.
     */
    private void printMenuOption(int optionNum, String label, boolean isSelected) {
        String prefix = isSelected ? "▸ " : "  ";
        String plainText = String.format("%s[%d] %s", prefix, optionNum, label);

        // Pad strictly to 74 printable characters so the highlight spans from left to right border
        if (plainText.length() > 74) {
            plainText = plainText.substring(0, 74);
        } else {
            plainText = String.format("%-74s", plainText);
        }

        if (isSelected) {
            // Reverse video spans the exact 74 characters between pipes
            System.out.printf("│\033[7m%s\033[0m│%n", plainText);
        } else {
            System.out.printf("│%s│%n", plainText);
        }
    }

    public static String renderContent(AdminDTO adminDto, LoanPipelineStats stats, int selectedIndex, String statusMessage, int width) {
        return StaffDashboardScreen.renderLoanOfficerContent(adminDto, stats, selectedIndex, statusMessage, width);
    }
}
