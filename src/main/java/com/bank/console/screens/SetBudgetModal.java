package com.bank.console.screens;

import com.bank.console.ControllerFactory;
import com.bank.console.ScreenNavigator;
import com.bank.console.TUISession;
import com.bank.controller.BudgetController;
import com.bank.controller.CategoryController;

import com.bank.model.entity.Category;

/**
 * Enterprise modal entrypoint for Set Monthly Budget dialog.
 */
public class SetBudgetModal {

    public static void open(ScreenNavigator navigator, TUISession session) {
        new SetMonthlyBudgetScreen().render(navigator, session);
    }

    public static void open(ScreenNavigator navigator, TUISession session, Category selectedCategory) {
        new SetMonthlyBudgetScreen(ControllerFactory.getBudgetController(), ControllerFactory.getCategoryController(), selectedCategory, 1)
                .render(navigator, session);
    }

    public static void open(ScreenNavigator navigator, TUISession session,
                            BudgetController budgetController, CategoryController categoryController) {
        new SetMonthlyBudgetScreen(budgetController, categoryController).render(navigator, session);
    }

    public static void open(ScreenNavigator navigator, TUISession session,
                            BudgetController budgetController, CategoryController categoryController,
                            Category selectedCategory) {
        new SetMonthlyBudgetScreen(budgetController, categoryController, selectedCategory, 1)
                .render(navigator, session);
    }
}
