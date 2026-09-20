package com.bank.console.screens;

import com.bank.controller.AccountController;
import com.bank.controller.BudgetController;
import com.bank.controller.CategoryController;
import com.bank.controller.SavingGoalController;

/**
 * Enterprise alias for BudgetScreen supporting tabbed financial planning.
 */
public class BudgetGoalsScreen extends BudgetScreen {

    public BudgetGoalsScreen() {
        super(1);
    }

    public BudgetGoalsScreen(BudgetController budgetController, CategoryController categoryController) {
        super(budgetController, categoryController, 1);
    }

    public BudgetGoalsScreen(BudgetController budgetController, CategoryController categoryController,
                             SavingGoalController savingGoalController, AccountController accountController) {
        super(budgetController, categoryController, savingGoalController, accountController, 1);
    }
}
