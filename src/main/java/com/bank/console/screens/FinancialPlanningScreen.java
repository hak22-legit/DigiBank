package com.bank.console.screens;

import com.bank.console.ControllerFactory;
import com.bank.console.ScreenNavigator;
import com.bank.console.TUISession;
import com.bank.console.components.ScreenRenderer;
import com.bank.console.components.TUIBox;
import com.bank.console.components.TUILayout;
import com.bank.console.theme.ConsoleTheme;
import com.bank.controller.AccountController;
import com.bank.controller.BudgetController;
import com.bank.controller.CategoryController;
import com.bank.controller.SavingGoalController;
import com.bank.model.BudgetView;
import com.bank.model.dto.AccountDTO;
import com.bank.model.dto.UserDTO;
import com.bank.model.entity.Budget;
import com.bank.model.entity.Category;
import com.bank.model.entity.SavingGoal;
import com.bank.model.entity.User;
import com.bank.security.SessionManager;
import com.bank.ui.Ansi;
import org.jline.terminal.Attributes;
import org.jline.terminal.Terminal;
import org.jline.utils.NonBlockingReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * SCREEN 9: FINANCIAL PLANNING & WEALTH TARGETS (82 Columns)
 * Tab 1: Monthly Budgets (Paged)
 * Tab 2: Savings Goals (Paged, Interactive Row Selection, Contextual Hotkeys [C]/[E]/[D]/[W])
 * Features robust multi-byte sequence parsing (arrows, Tab), case-insensitive hotkeys,
 * clamped 16-slot progress bars (guarding against > 100%), and strict 82-column layout.
 */
public class FinancialPlanningScreen extends BudgetScreen {
    private static final Logger fpsLogger = LoggerFactory.getLogger(FinancialPlanningScreen.class);

    public FinancialPlanningScreen() {
        this(1);
    }

    public FinancialPlanningScreen(int initialTab) {
        super(initialTab);
    }

    public FinancialPlanningScreen(BudgetController budgetController, CategoryController categoryController) {
        this(budgetController, categoryController, 1);
    }

    public FinancialPlanningScreen(BudgetController budgetController, CategoryController categoryController, int initialTab) {
        super(budgetController, categoryController, initialTab);
    }

    public FinancialPlanningScreen(BudgetController budgetController,
                                   CategoryController categoryController,
                                   SavingGoalController savingGoalController,
                                   AccountController accountController) {
        this(budgetController, categoryController, savingGoalController, accountController, 1);
    }

    public FinancialPlanningScreen(BudgetController budgetController,
                                   CategoryController categoryController,
                                   SavingGoalController savingGoalController,
                                   AccountController accountController,
                                   int initialTab) {
        super(budgetController, categoryController, savingGoalController, accountController, initialTab);
    }

    @Override
    public void render(ScreenNavigator navigator, TUISession session) {
        UserDTO userDto = session.getCurrentUser();
        User userEntity = SessionManager.getCurrentUser();
        if (userDto == null || userEntity == null) {
            navigator.pop();
            return;
        }

        int width = TUILayout.APP_WIDTH;
        DecimalFormat df = new DecimalFormat("#,##0.00");
        DateTimeFormatter dfDate = DateTimeFormatter.ofPattern("yyyy-MM-dd");

        Terminal terminal = session.getTerminal();
        Attributes origAttributes = terminal.enterRawMode();
        NonBlockingReader reader = terminal.reader();

        int currentTab = this.initialTab; // 0: Budgets, 1: Savings Goals
        int budgetPage = 1;
        int goalPage = 1;
        int selectedGoalIndex = 0;
        int selectedBudgetIndex = 0;
        boolean firstRender = true;

        // In-memory cache to eliminate cloud database queries on navigation keystrokes
        Map<Long, String> catMap = new HashMap<>();
        List<Category> allCategories = new ArrayList<>();
        List<BudgetView> budgets = List.of();
        List<SavingGoal> goals = List.of();
        List<AccountDTO> accounts = List.of();
        BigDecimal totalMonthlyCap = BigDecimal.ZERO;
        int totalBudgetPages = 1;
        int totalGoalPages = 1;
        boolean reloadNeeded = true;

        try {
            while (true) {
                if (reloadNeeded) {
                    catMap.clear();
                    try {
                        List<Category> categories = categoryController.getVisibleCategories(userEntity);
                        if (categories != null) {
                            allCategories = categories;
                            for (Category c : categories) catMap.put(c.getCategoryId(), c.getName());
                        }
                    } catch (Exception ignored) {}

                    try {
                        List<BudgetView> fetched = budgetController.getBudgetsWithUsage(userEntity);
                        budgets = (fetched != null) ? fetched : List.of();
                    } catch (Exception ignored) {
                        budgets = List.of();
                    }

                    try {
                        List<SavingGoal> fetchedGoals = savingGoalController.getGoalsForUser(userEntity);
                        goals = (fetchedGoals != null) ? fetchedGoals : List.of();
                    } catch (Exception ignored) {
                        goals = List.of();
                    }

                    try {
                        List<AccountDTO> fetchedAccounts = accountController.getAccountsForUser(userEntity);
                        accounts = (fetchedAccounts != null) ? fetchedAccounts : List.of();
                    } catch (Exception ignored) {
                        accounts = List.of();
                    }

                    totalMonthlyCap = BigDecimal.ZERO;
                    if (budgets.isEmpty()) {
                        totalMonthlyCap = new BigDecimal("470.00");
                    } else {
                        for (BudgetView bv : budgets) {
                            if (bv.getBudget() != null && bv.getBudget().getAmountLimit() != null) {
                                totalMonthlyCap = totalMonthlyCap.add(bv.getBudget().getAmountLimit());
                            }
                        }
                    }

                    totalBudgetPages = Math.max(1, (int) Math.ceil((double) budgets.size() / PAGE_SIZE));
                    budgetPage = Math.min(budgetPage, totalBudgetPages);

                    int goalCount = goals.isEmpty() ? 5 : goals.size();
                    totalGoalPages = Math.max(1, (int) Math.ceil((double) goalCount / PAGE_SIZE));
                    goalPage = Math.min(goalPage, totalGoalPages);

                    reloadNeeded = false;
                }

                StringBuilder sb = new StringBuilder();

                // Top Box
                sb.append(TUIBox.top(width)).append("\n");
                sb.append(TUIBox.line(ConsoleTheme.primary("DIGIBANK CORE > FINANCIAL PLANNING & WEALTH TARGETS"), width)).append("\n");
                sb.append(TUIBox.divider(width)).append("\n");

                // Tab strip (strictly 78 inner characters)
                String tab1Label = String.format("[1] MONTHLY BUDGETS (Page %d/%d)", budgetPage, totalBudgetPages);
                String tab2Label = String.format("[2] SAVINGS GOALS (Page %d/%d)", goalPage, totalGoalPages);
                String tab1 = currentTab == 0 ? "▸ " + ConsoleTheme.highlight(tab1Label) : "    " + tab1Label;
                String tab2 = currentTab == 1 ? "▸ " + ConsoleTheme.highlight(tab2Label) : "    " + tab2Label;
                sb.append(TUIBox.line(String.format("  %-42s  %-30s", tab1, tab2), width)).append("\n");
                sb.append(TUIBox.divider(width)).append("\n");

                SavingGoal currentSelectedGoal = null;

                if (currentTab == 0) {
                    // TAB 1: MONTHLY BUDGETS
                    String header = " CATEGORY       LIMIT        SPENT      REMAINING    USAGE PROGRESS           ";
                    sb.append(TUIBox.line(header, width)).append("\n");
                    sb.append(TUIBox.line(" " + "─".repeat(76) + " ", width)).append("\n");

                    if (budgets.isEmpty()) {
                        selectedBudgetIndex = Math.max(0, Math.min(selectedBudgetIndex, 4));
                        renderDefaultBudgetRows(sb, width, selectedBudgetIndex);
                    } else {
                        int startIdx = (budgetPage - 1) * PAGE_SIZE;
                        int endIdx = Math.min(startIdx + PAGE_SIZE, budgets.size());
                        int pageCount = Math.max(1, endIdx - startIdx);
                        selectedBudgetIndex = Math.max(0, Math.min(selectedBudgetIndex, pageCount - 1));

                        for (int i = startIdx; i < endIdx; i++) {
                            BudgetView bv = budgets.get(i);
                            Budget b = bv.getBudget();
                            String catName = catMap.getOrDefault(b.getCategoryId(), "General");
                            if (catName.length() > 14) catName = catName.substring(0, 14);

                            BigDecimal limit = b.getAmountLimit() != null ? b.getAmountLimit() : BigDecimal.ONE;
                            BigDecimal spent = bv.getActualSpending() != null ? bv.getActualSpending() : BigDecimal.ZERO;
                            BigDecimal rem = bv.getRemainingAmount() != null ? bv.getRemainingAmount() : BigDecimal.ZERO;

                            String limitStr = "$ " + String.format("%8s", df.format(limit));
                            String spentStr = "$ " + String.format("%7s", df.format(spent));
                            String remStr = "$ " + String.format("%7s", df.format(rem));

                            int percentage = (limit.compareTo(BigDecimal.ZERO) > 0)
                                    ? (int) Math.round((spent.doubleValue() / limit.doubleValue()) * 100)
                                    : 0;
                            percentage = Math.max(0, percentage);
                            int filledSlots = Math.min(16, (percentage * 16) / 100);
                            filledSlots = Math.max(0, filledSlots);
                            int emptySlots = Math.max(0, 16 - filledSlots);
                            String progressBar = "█".repeat(filledSlots) + "░".repeat(emptySlots);

                            boolean isSelected = (i - startIdx == selectedBudgetIndex);
                            String prefix = isSelected ? "▸ " : "  ";

                            String row = String.format("%s%-14s %11s  %10s  %10s   [%-16s] %3d%%",
                                    prefix, catName, limitStr, spentStr, remStr, progressBar, Math.min(999, percentage));
                            if (isSelected) {
                                sb.append(TUIBox.line(ConsoleTheme.inlineHighlight(row), width)).append("\n");
                            } else {
                                String coloredBar = (percentage >= 100) ? Ansi.red(progressBar)
                                        : (percentage >= 75) ? Ansi.yellow(progressBar)
                                        : Ansi.green(progressBar);
                                String coloredRow = row.replace("[" + progressBar + "]", "[" + coloredBar + "]");
                                sb.append(TUIBox.line(coloredRow, width)).append("\n");
                            }
                        }

                        for (int i = endIdx - startIdx; i < PAGE_SIZE; i++) {
                            sb.append(TUIBox.emptyLine(width)).append("\n");
                        }
                    }

                    sb.append(TUIBox.divider(width)).append("\n");
                    String capStr = "$" + df.format(totalMonthlyCap);
                    String metaRow = String.format("Page: [ %d / %d ]   │ Filter: [ACTIVE BUDGETS]      │ Total Monthly Cap: %s",
                            budgetPage, totalBudgetPages, capStr);
                    sb.append(TUIBox.line(metaRow, width)).append("\n");

                } else {
                    // TAB 2: SAVINGS GOALS
                    String header = "    GOAL NAME            TARGET       SAVED  DEADLINE    SAVINGS PROGRESS      ";
                    sb.append(TUIBox.line(header, width)).append("\n");
                    sb.append(TUIBox.line(" " + "─".repeat(76) + " ", width)).append("\n");

                    List<SavingGoal> displayGoals;
                    if (goals.isEmpty()) {
                        displayGoals = createDefaultGoals(userEntity);
                    } else {
                        displayGoals = goals;
                    }

                    int startIdx = (goalPage - 1) * PAGE_SIZE;
                    int endIdx = Math.min(startIdx + PAGE_SIZE, displayGoals.size());
                    int pageCount = Math.max(1, endIdx - startIdx);
                    selectedGoalIndex = Math.max(0, Math.min(selectedGoalIndex, pageCount - 1));

                    if (!displayGoals.isEmpty() && (startIdx + selectedGoalIndex) < displayGoals.size()) {
                        currentSelectedGoal = displayGoals.get(startIdx + selectedGoalIndex);
                    }

                    for (int i = startIdx; i < endIdx; i++) {
                        SavingGoal g = displayGoals.get(i);
                        int pageRowIdx = i - startIdx;
                        boolean isSelected = (pageRowIdx == selectedGoalIndex);

                        String name = g.getName() != null ? g.getName() : "Goal";
                        if (name.length() > 15) name = name.substring(0, 15);

                        BigDecimal target = g.getTargetAmount() != null ? g.getTargetAmount() : BigDecimal.ONE;
                        BigDecimal saved = g.getCurrentAmount() != null ? g.getCurrentAmount() : BigDecimal.ZERO;
                        String targetStr = "$" + String.format("%10s", df.format(target));
                        String savedStr = "$" + String.format("%10s", df.format(saved));
                        String deadlineStr = g.getDeadline() != null ? g.getDeadline().format(dfDate) : "2026-12-31";

                        int percentage = (target.compareTo(BigDecimal.ZERO) > 0)
                                ? (int) Math.round((saved.doubleValue() / target.doubleValue()) * 100)
                                : 0;
                        percentage = Math.max(0, percentage);
                        int filledSlots = Math.min(16, (percentage * 16) / 100);
                        filledSlots = Math.max(0, filledSlots);
                        int emptySlots = Math.max(0, 16 - filledSlots);
                        String progressBar = "█".repeat(filledSlots) + "░".repeat(emptySlots);

                        String prefix = isSelected ? "  ▸ " : "    ";
                        String rowText = String.format("%s%-15s %11s %11s  %-10s [%-16s]%3d%%",
                                prefix, name, targetStr, savedStr, deadlineStr, progressBar, Math.min(999, percentage));

                        if (isSelected) {
                            sb.append(TUIBox.line(ConsoleTheme.inlineHighlight(rowText), width)).append("\n");
                        } else {
                            String coloredBar = (percentage >= 100) ? Ansi.green(progressBar)
                                    : (percentage >= 50) ? Ansi.cyan(progressBar)
                                    : Ansi.yellow(progressBar);
                            String coloredRow = rowText.replace("[" + progressBar + "]", "[" + coloredBar + "]");
                            sb.append(TUIBox.line(coloredRow, width)).append("\n");
                        }
                    }

                    for (int i = endIdx - startIdx; i < PAGE_SIZE; i++) {
                        sb.append(TUIBox.emptyLine(width)).append("\n");
                    }

                    sb.append(TUIBox.divider(width)).append("\n");
                    int totalCount = displayGoals.size();
                    String metaRow = String.format("Page: [ %d / %d ]   │ Filter: [IN PROGRESS]        │ Total Goals: %-4d",
                            goalPage, totalGoalPages, totalCount);
                    sb.append(TUIBox.line(metaRow, width)).append("\n");
                }

                sb.append(TUIBox.bottom(width)).append("\n");

                if (currentTab == 0) {
                    sb.append(ConsoleTheme.keyGuide("[↑/↓] Select  •  [Enter] Edit Limit  •  [N] New  •  [X] Delete  •  [1/2/Tab] Tab")).append("\n");
                } else {
                    sb.append(ConsoleTheme.keyGuide("[↑/↓] Select  •  [Enter] Deposit  •  [E] Edit  •  [C] Create  •  [1/2/Tab] Tab")).append("\n");
                }

                if (firstRender) {
                    System.out.print("\033[H\033[2J");
                    System.out.flush();
                }
                ScreenRenderer.render(sb.toString(), firstRender);
                firstRender = false;

                int ch = reader.read();
                if (ch == -1) {
                    break;
                }

                // Handle ANSI Escape Sequences (Arrow Keys, Esc) with strict 25ms lookahead
                if (ch == 27) { // 0x1B / ESC
                    int next1 = reader.read(25);
                    if (next1 == '[' || next1 == 'O') {
                        int next2 = reader.read(25);
                        if (next2 == 'A') { // \033[A: Up Arrow
                            if (currentTab == 1) {
                                selectedGoalIndex = Math.max(0, selectedGoalIndex - 1);
                                transientStatus = null;
                            } else {
                                selectedBudgetIndex = Math.max(0, selectedBudgetIndex - 1);
                                transientStatus = null;
                            }
                        } else if (next2 == 'B') { // \033[B: Down Arrow
                            if (currentTab == 1) {
                                List<SavingGoal> currentList = (goals.isEmpty()) ? createDefaultGoals(userEntity) : goals;
                                int currentStart = (goalPage - 1) * PAGE_SIZE;
                                int currentPageCount = Math.max(1, Math.min(PAGE_SIZE, currentList.size() - currentStart));
                                selectedGoalIndex = Math.min(currentPageCount - 1, selectedGoalIndex + 1);
                                transientStatus = null;
                            } else {
                                int count = budgets.isEmpty() ? 5 : Math.max(1, budgets.size() - (budgetPage - 1) * PAGE_SIZE);
                                int currentPageCount = Math.max(1, Math.min(PAGE_SIZE, count));
                                selectedBudgetIndex = Math.min(currentPageCount - 1, selectedBudgetIndex + 1);
                                transientStatus = null;
                            }
                        } else if (next2 == 'D') { // \033[D: Left Arrow
                            if (currentTab == 0) {
                                budgetPage = Math.max(1, budgetPage - 1);
                                selectedBudgetIndex = 0;
                            } else {
                                goalPage = Math.max(1, goalPage - 1);
                                selectedGoalIndex = 0;
                                transientStatus = null;
                            }
                        } else if (next2 == 'C') { // \033[C: Right Arrow
                            if (currentTab == 0) {
                                budgetPage = Math.min(totalBudgetPages, budgetPage + 1);
                                selectedBudgetIndex = 0;
                            } else {
                                goalPage = Math.min(totalGoalPages, goalPage + 1);
                                selectedGoalIndex = 0;
                                transientStatus = null;
                            }
                        } else if (next2 == 'Z') { // \033[Z: Shift-Tab
                            currentTab = (currentTab == 0) ? 1 : 0;
                            budgetPage = 1;
                            goalPage = 1;
                            selectedGoalIndex = 0;
                            selectedBudgetIndex = 0;
                            transientStatus = null;
                        }
                    } else if (next1 == -2 || next1 == -1 || next1 == 'b' || next1 == 'B') {
                        // Bare ESC key or back -> Dashboard
                        terminal.setAttributes(origAttributes);
                        navigator.pop();
                        return;
                    }
                } else if (ch == '\t') { // Tab key toggles between [1] Monthly Budgets and [2] Savings Goals
                    currentTab = (currentTab == 0) ? 1 : 0;
                    budgetPage = 1;
                    goalPage = 1;
                    selectedGoalIndex = 0;
                    selectedBudgetIndex = 0;
                    transientStatus = null;
                } else if (ch == '1') {
                    currentTab = 0;
                    budgetPage = 1;
                    selectedBudgetIndex = 0;
                    transientStatus = null;
                } else if (ch == '2') {
                    currentTab = 1;
                    goalPage = 1;
                    selectedGoalIndex = 0;
                    transientStatus = null;
                } else {
                    char upper = (ch == '\r' || ch == '\n')
                            ? (currentTab == 0 ? 'S' : 'D')
                            : Character.toUpperCase((char) ch);

                    // Vim-style navigation fallback support (k=Up, j=Down, h=Left, l=Right)
                    if (upper == 'K') {
                        if (currentTab == 1) {
                            selectedGoalIndex = Math.max(0, selectedGoalIndex - 1);
                            transientStatus = null;
                        } else {
                            selectedBudgetIndex = Math.max(0, selectedBudgetIndex - 1);
                            transientStatus = null;
                        }
                    } else if (upper == 'J') {
                        if (currentTab == 1) {
                            List<SavingGoal> currentList = (goals.isEmpty()) ? createDefaultGoals(userEntity) : goals;
                            int currentStart = (goalPage - 1) * PAGE_SIZE;
                            int currentPageCount = Math.max(1, Math.min(PAGE_SIZE, currentList.size() - currentStart));
                            selectedGoalIndex = Math.min(currentPageCount - 1, selectedGoalIndex + 1);
                            transientStatus = null;
                        } else {
                            int count = budgets.isEmpty() ? 5 : Math.max(1, budgets.size() - (budgetPage - 1) * PAGE_SIZE);
                            int currentPageCount = Math.max(1, Math.min(PAGE_SIZE, count));
                            selectedBudgetIndex = Math.min(currentPageCount - 1, selectedBudgetIndex + 1);
                            transientStatus = null;
                        }
                    } else if (upper == 'H') {
                        if (currentTab == 0) {
                            budgetPage = Math.max(1, budgetPage - 1);
                            selectedBudgetIndex = 0;
                        } else {
                            goalPage = Math.max(1, goalPage - 1);
                            selectedGoalIndex = 0;
                            transientStatus = null;
                        }
                    } else if (upper == 'L') {
                        if (currentTab == 0) {
                            budgetPage = Math.min(totalBudgetPages, budgetPage + 1);
                            selectedBudgetIndex = 0;
                        } else {
                            goalPage = Math.min(totalGoalPages, goalPage + 1);
                            selectedGoalIndex = 0;
                            transientStatus = null;
                        }
                    } else if (currentTab == 0) {
                        // TAB 1: Route 'S', 'N', 'D'
                        if (upper == 'S') {
                            if (allCategories == null || allCategories.isEmpty()) {
                                try {
                                    allCategories = categoryController.getVisibleCategories(userEntity);
                                } catch (Exception ignored) {}
                            }
                            if (allCategories == null || allCategories.isEmpty()) {
                                transientStatus = "No categories available to configure.";
                                isErrorStatus = true;
                                firstRender = true;
                                continue;
                            }

                            Category initialCat = allCategories.get(0);
                            String selectedCatName = null;
                            if (budgets.isEmpty()) {
                                String[] defaultCatNames = {"Food & Dining", "Utilities", "Transportation", "Entertainment", "Shopping"};
                                if (selectedBudgetIndex >= 0 && selectedBudgetIndex < defaultCatNames.length) {
                                    selectedCatName = defaultCatNames[selectedBudgetIndex];
                                }
                            } else {
                                int targetIdx = (budgetPage - 1) * PAGE_SIZE + selectedBudgetIndex;
                                if (targetIdx < budgets.size()) {
                                    Budget b = budgets.get(targetIdx).getBudget();
                                    selectedCatName = catMap.get(b.getCategoryId());
                                }
                            }
                            if (selectedCatName != null) {
                                for (Category c : allCategories) {
                                    if (c.getName() != null && c.getName().equalsIgnoreCase(selectedCatName)) {
                                        initialCat = c;
                                        break;
                                    }
                                }
                            }

                            Category chosen = CategorySelectModal.selectCategory(
                                    terminal, origAttributes, reader, allCategories, initialCat, width);
                            if (chosen != null) {
                                SetBudgetModal.open(navigator, session, budgetController, categoryController, chosen);
                                reloadNeeded = true;
                            }
                            firstRender = true;
                            continue;
                        } else if (upper == 'N') {
                            Category created = CreateCategoryModal.show(
                                    terminal, origAttributes, reader, categoryController, userEntity, width);
                            if (created != null) {
                                transientStatus = "Category '" + created.getName() + "' created successfully.";
                                isErrorStatus = false;
                                reloadNeeded = true;
                            }
                            firstRender = true;
                        } else if (upper == 'X' || upper == 'D') {
                            if (!budgets.isEmpty()) {
                                try {
                                    int targetIdx = (budgetPage - 1) * PAGE_SIZE + selectedBudgetIndex;
                                    if (targetIdx >= budgets.size()) targetIdx = 0;
                                    Budget toDelete = budgets.get(targetIdx).getBudget();
                                    ControllerFactory.getBudgetRepository().deleteById(toDelete.getBudgetId());
                                    this.transientStatus = "Budget successfully removed.";
                                    this.isErrorStatus = false;
                                    reloadNeeded = true;
                                } catch (Exception e) {
                                    this.transientStatus = "Failed to delete budget: " + e.getMessage();
                                    this.isErrorStatus = true;
                                }
                            }
                        }
                    } else {
                        // TAB 2: Route 'C', 'E', 'D', 'W'
                        if (upper == 'C') {
                            SavingGoal created = CreateGoalModal.createGoal(
                                    terminal, origAttributes, reader, accounts,
                                    savingGoalController, accountController, userEntity, width);
                            if (created != null) {
                                transientStatus = "Goal '" + created.getName() + "' created successfully.";
                                isErrorStatus = false;
                                selectedGoalIndex = 0;
                                reloadNeeded = true;
                            }
                            firstRender = true;
                        } else if (upper == 'E') {
                            if (currentSelectedGoal != null) {
                                SavingGoal targetToEdit = currentSelectedGoal;
                                if (targetToEdit.getGoalId() == null) {
                                    try {
                                        SavingGoal created = savingGoalController.createGoal(
                                                userEntity, targetToEdit.getName(), targetToEdit.getTargetAmount(), targetToEdit.getDeadline());
                                        if (targetToEdit.getCurrentAmount() != null && targetToEdit.getCurrentAmount().compareTo(BigDecimal.ZERO) > 0) {
                                            savingGoalController.contribute(created.getGoalId(), targetToEdit.getCurrentAmount(), userEntity);
                                        }
                                        targetToEdit = created;
                                    } catch (Exception ignored) {}
                                }
                                SavingGoal updated = EditGoalModal.editGoal(
                                        terminal, origAttributes, reader, targetToEdit, savingGoalController, userEntity, width);
                                if (updated != null) {
                                    transientStatus = "Goal '" + updated.getName() + "' updated successfully.";
                                    isErrorStatus = false;
                                    reloadNeeded = true;
                                }
                            } else {
                                transientStatus = "No goal selected to edit.";
                                isErrorStatus = true;
                            }
                            firstRender = true;
                        } else if (upper == 'D') {
                            if (currentSelectedGoal != null) {
                                SavingGoal targetToFund = currentSelectedGoal;
                                if (targetToFund.getGoalId() == null) {
                                    try {
                                        SavingGoal created = savingGoalController.createGoal(
                                                userEntity, targetToFund.getName(), targetToFund.getTargetAmount(), targetToFund.getDeadline());
                                        if (targetToFund.getCurrentAmount() != null && targetToFund.getCurrentAmount().compareTo(BigDecimal.ZERO) > 0) {
                                            savingGoalController.contribute(created.getGoalId(), targetToFund.getCurrentAmount(), userEntity);
                                        }
                                        targetToFund = created;
                                    } catch (Exception ignored) {}
                                }
                                boolean ok = DepositGoalModal.depositToGoal(
                                        terminal, origAttributes, reader, targetToFund, accounts,
                                        savingGoalController, accountController, userEntity, width);
                                if (ok) {
                                    transientStatus = "Deposited funds to '" + targetToFund.getName() + "' successfully.";
                                    isErrorStatus = false;
                                    reloadNeeded = true;
                                }
                            } else {
                                transientStatus = "No goal selected to deposit into.";
                                isErrorStatus = true;
                            }
                            firstRender = true;
                        } else if (upper == 'W') {
                            if (currentSelectedGoal != null) {
                                executeWithdrawContextual(currentSelectedGoal, accounts, userEntity, df);
                                reloadNeeded = true;
                            } else {
                                transientStatus = "No goal selected to withdraw from.";
                                isErrorStatus = true;
                            }
                        }
                    }
                }
            }
        } catch (IOException e) {
            fpsLogger.error("Error reading key on FinancialPlanningScreen", e);
        } finally {
            terminal.setAttributes(origAttributes);
        }
    }
}
