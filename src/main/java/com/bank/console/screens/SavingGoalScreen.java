package com.bank.console.screens;

import com.bank.console.ControllerFactory;
import com.bank.console.ScreenNavigator;
import com.bank.console.TUISession;
import com.bank.controller.SavingGoalController;

/**
 * Screen for tracking and contributing to savings goals.
 * Delegates to the unified Budgets & Saving Goals Screen (Screen 9).
 */
public class SavingGoalScreen implements Screen {
    private final SavingGoalController savingGoalController;

    public SavingGoalScreen() {
        this(ControllerFactory.getSavingGoalController());
    }

    public SavingGoalScreen(SavingGoalController savingGoalController) {
        this.savingGoalController = savingGoalController;
    }

    @Override
    public void render(ScreenNavigator navigator, TUISession session) {
        new BudgetScreen().render(navigator, session);
    }
}
