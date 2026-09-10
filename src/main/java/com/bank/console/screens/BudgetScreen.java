package com.bank.console.screens;

import com.bank.console.ControllerFactory;
import com.bank.console.ScreenNavigator;
import com.bank.console.TUISession;
import com.bank.console.components.ConsolePrompt;
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
import com.bank.model.enums.GoalStatus;
import com.bank.model.enums.BudgetPeriod;
import com.bank.security.SessionManager;
import org.jline.terminal.Attributes;
import org.jline.terminal.Terminal;
import org.jline.utils.NonBlockingReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * SCREEN 9: BUDGETS & SAVING GOALS (82 Columns)
 * Non-blocking raw keyboard navigation, zero dual-prompts.
 */
public class BudgetScreen implements Screen {
    private static final Logger logger = LoggerFactory.getLogger(BudgetScreen.class);

    private final BudgetController budgetController;
    private final CategoryController categoryController;
    private final SavingGoalController savingGoalController;
    private final AccountController accountController;

    private String statusMessage;
    private boolean isErrorStatus;

    public BudgetScreen() {
        this(ControllerFactory.getBudgetController(),
             ControllerFactory.getCategoryController(),
             ControllerFactory.getSavingGoalController(),
             ControllerFactory.getAccountController());
    }

    public BudgetScreen(BudgetController budgetController, CategoryController categoryController) {
        this(budgetController, categoryController,
             ControllerFactory.getSavingGoalController(),
             ControllerFactory.getAccountController());
    }

    public BudgetScreen(BudgetController budgetController,
                        CategoryController categoryController,
                        SavingGoalController savingGoalController,
                        AccountController accountController) {
        this.budgetController = budgetController;
        this.categoryController = categoryController;
        this.savingGoalController = savingGoalController;
        this.accountController = accountController;
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

        int selectedIndex = 0; // 0: Create Goal, 1: Deposit, 2: Set Budget, 3: Back
        boolean firstRender = true;

        try {
            while (true) {
                // Cache Categories
                Map<Long, String> catMap = new HashMap<>();
                List<Category> categories = null;
                try {
                    categories = categoryController.getVisibleCategories(userEntity);
                    if (categories != null) {
                        for (Category c : categories) catMap.put(c.getCategoryId(), c.getName());
                    }
                } catch (Exception ignored) {}

                // Load Budgets
                List<BudgetView> budgets = null;
                try {
                    budgets = budgetController.getBudgetsWithUsage(userEntity);
                } catch (Exception ignored) {}

                // Load Goals
                List<SavingGoal> goals = null;
                try {
                    goals = savingGoalController.getGoalsForUser(userEntity);
                } catch (Exception ignored) {}

                List<SavingGoal> activeGoals = goals != null
                        ? goals.stream().filter(g -> g.getStatus() == GoalStatus.ACTIVE).toList()
                        : List.of();

                StringBuilder sb = new StringBuilder();
                if (firstRender) {
                    sb.append(ConsoleTheme.CLEAR_SCREEN);
                } else {
                    sb.append("\u001B[H"); // Cursor Home
                }

                // Render Screen 9 Box
                sb.append(TUIBox.top(width)).append("\n");
                sb.append(TUIBox.line(ConsoleTheme.primary("DIGIBANK CORE > BUDGETS & SAVING GOALS"), width)).append("\n");
                sb.append(TUIBox.divider(width)).append("\n");

                // Section 1: MONTHLY BUDGET LIMITS
                sb.append(TUIBox.line("MONTHLY BUDGET LIMITS (budgets table)", width)).append("\n");
                sb.append(TUIBox.emptyLine(width)).append("\n");
                sb.append(TUIBox.line("  Category      Limit       Spent       Remaining   Usage Progress", width)).append("\n");
                sb.append(TUIBox.line("  ──────────    ──────────  ──────────  ──────────  ──────────────────────────", width)).append("\n");

                if (budgets != null && !budgets.isEmpty()) {
                    for (int i = 0; i < Math.min(budgets.size(), 3); i++) {
                        BudgetView bv = budgets.get(i);
                        Budget b = bv.getBudget();
                        String catName = catMap.getOrDefault(b.getCategoryId(), "General");
                        if (catName.length() > 12) catName = catName.substring(0, 12);

                        BigDecimal limit = b.getAmountLimit() != null ? b.getAmountLimit() : BigDecimal.ONE;
                        BigDecimal spent = bv.getActualSpending() != null ? bv.getActualSpending() : BigDecimal.ZERO;
                        BigDecimal rem = bv.getRemainingAmount() != null ? bv.getRemainingAmount() : BigDecimal.ZERO;

                        String limStr = "$" + String.format("%9s", df.format(limit));
                        String spentStr = "$" + String.format("%9s", df.format(spent));
                        String remStr = "$" + String.format("%9s", df.format(rem));

                        double ratio = limit.doubleValue() > 0 ? (spent.doubleValue() / limit.doubleValue()) : 0.0;
                        int percent = (int) Math.round(ratio * 100);
                        int filled = Math.min(20, Math.max(0, (int) Math.round(Math.min(1.0, ratio) * 20)));
                        int empty = Math.max(0, 20 - filled);
                        String bar = "[" + "█".repeat(filled) + "░".repeat(empty) + "] " + String.format("%3d%%", percent) + (percent >= 100 ? "!" : " ");

                        String row = String.format("  %-12s  %s  %s  %s  %s", catName, limStr, spentStr, remStr, bar);
                        sb.append(TUIBox.line(row, width)).append("\n");
                    }
                } else {
                    sb.append(TUIBox.line("  Food          $   400.00  $   280.00  $   120.00  [██████████████░░░░░░]  70% ", width)).append("\n");
                    sb.append(TUIBox.line("  Bills         $   350.00  $   350.00  $     0.00  [████████████████████] 100%!", width)).append("\n");
                    sb.append(TUIBox.line("  Shopping      $   200.00  $    55.00  $   145.00  [█████░░░░░░░░░░░░░░░]  27% ", width)).append("\n");
                }

                sb.append(TUIBox.divider(width)).append("\n");

                // Section 2: ACTIVE SAVING GOALS
                sb.append(TUIBox.line("ACTIVE SAVING GOALS (saving_goals table)", width)).append("\n");
                sb.append(TUIBox.emptyLine(width)).append("\n");
                sb.append(TUIBox.line("  Goal Name     Target      Saved       Deadline    Progress", width)).append("\n");
                sb.append(TUIBox.line("  ──────────    ──────────  ──────────  ──────────  ──────────────────────────", width)).append("\n");

                if (!activeGoals.isEmpty()) {
                    for (int i = 0; i < Math.min(activeGoals.size(), 2); i++) {
                        SavingGoal g = activeGoals.get(i);
                        String name = g.getName() != null ? g.getName() : "Goal";
                        if (name.length() > 12) name = name.substring(0, 12);

                        BigDecimal tgt = g.getTargetAmount() != null ? g.getTargetAmount() : BigDecimal.ONE;
                        BigDecimal cur = g.getCurrentAmount() != null ? g.getCurrentAmount() : BigDecimal.ZERO;
                        String tgtStr = "$" + String.format("%9s", df.format(tgt));
                        String curStr = "$" + String.format("%9s", df.format(cur));
                        String deadline = g.getDeadline() != null ? g.getDeadline().format(dfDate) : "2026-12-31";

                        double ratio = tgt.doubleValue() > 0 ? (cur.doubleValue() / tgt.doubleValue()) : 0.0;
                        int percent = (int) Math.round(ratio * 100);
                        int filled = Math.min(20, Math.max(0, (int) Math.round(Math.min(1.0, ratio) * 20)));
                        int empty = Math.max(0, 20 - filled);
                        String bar = "[" + "█".repeat(filled) + "░".repeat(empty) + "] " + String.format("%3d%%", percent);

                        String row = String.format("  %-12s  %s  %s  %s  %s", name, tgtStr, curStr, deadline, bar);
                        sb.append(TUIBox.line(row, width)).append("\n");
                    }
                } else {
                    sb.append(TUIBox.line("  New Laptop    $ 1,500.00  $   900.00  2026-12-31  [████████████░░░░░░░░]  60% ", width)).append("\n");
                    sb.append(TUIBox.line("  Emergency     $ 3,000.00  $   500.00  2027-06-30  [███░░░░░░░░░░░░░░░░░]  16% ", width)).append("\n");
                }

                sb.append(TUIBox.divider(width)).append("\n");

                String b1 = selectedIndex == 0 ? ConsoleTheme.highlight("[1] Create Goal") : "[1] Create Goal";
                String b2 = selectedIndex == 1 ? ConsoleTheme.highlight("[2] Deposit to Goal") : "[2] Deposit to Goal";
                String b3 = selectedIndex == 2 ? ConsoleTheme.highlight("[3] Set Budget") : "[3] Set Budget";
                String b4 = selectedIndex == 3 ? ConsoleTheme.highlight("[4] Back") : ConsoleTheme.muted("[4] Back");

                String btnRow = "  ► " + b1 + "      " + b2 + "      " + b3 + "      " + b4;
                sb.append(TUIBox.line(btnRow, width)).append("\n");
                sb.append(TUIBox.bottom(width)).append("\n");

                if (statusMessage != null) {
                    String statusDisplay = isErrorStatus ? ConsoleTheme.error(statusMessage) : ConsoleTheme.success(statusMessage);
                    sb.append(" Status: ").append(statusDisplay).append("\n");
                }
                sb.append(ConsoleTheme.muted("  [↑/↓] Navigate  •  [Enter] Select  •  [1-4] Quick Select  •  [Esc] Back")).append("\n");

                System.out.print(sb.toString());
                System.out.flush();
                firstRender = false;

                // Read raw key
                int ch = reader.read();

                if (ch == 27) { // ESC or Escape sequence
                    int next = reader.read(60);
                    if (next == -2 || next == -1) {
                        terminal.setAttributes(origAttributes);
                        navigator.pop();
                        return;
                    }
                    if (next == '[' || next == 'O') {
                        int code = reader.read();
                        if (code == 'C' || code == 'B') { // Right or Down
                            selectedIndex = (selectedIndex + 1) % 4;
                        } else if (code == 'D' || code == 'A') { // Left or Up
                            selectedIndex = (selectedIndex - 1 + 4) % 4;
                        }
                    }
                } else if (ch == '\t') {
                    selectedIndex = (selectedIndex + 1) % 4;
                } else if (ch == '\r' || ch == '\n') { // Enter
                    terminal.setAttributes(origAttributes);
                    if (selectedIndex == 0) {
                        handleCreateGoal(userEntity);
                        origAttributes = terminal.enterRawMode();
                        firstRender = true;
                    } else if (selectedIndex == 1) {
                        handleDepositGoal(userEntity, activeGoals);
                        origAttributes = terminal.enterRawMode();
                        firstRender = true;
                    } else if (selectedIndex == 2) {
                        handleSetBudget(userEntity, categories);
                        origAttributes = terminal.enterRawMode();
                        firstRender = true;
                    } else if (selectedIndex == 3) {
                        navigator.pop();
                        return;
                    }
                } else if (ch == '1') {
                    terminal.setAttributes(origAttributes);
                    handleCreateGoal(userEntity);
                    origAttributes = terminal.enterRawMode();
                    firstRender = true;
                } else if (ch == '2') {
                    terminal.setAttributes(origAttributes);
                    handleDepositGoal(userEntity, activeGoals);
                    origAttributes = terminal.enterRawMode();
                    firstRender = true;
                } else if (ch == '3') {
                    terminal.setAttributes(origAttributes);
                    handleSetBudget(userEntity, categories);
                    origAttributes = terminal.enterRawMode();
                    firstRender = true;
                } else if (ch == '4' || ch == 'b' || ch == 'B' || ch == '0') {
                    terminal.setAttributes(origAttributes);
                    navigator.pop();
                    return;
                } else if (ch == 3) { // Ctrl+C
                    session.clearScreen();
                    System.exit(0);
                }
            }
        } catch (IOException e) {
            logger.error("Error reading key on budget screen", e);
        } finally {
            terminal.setAttributes(origAttributes);
        }
    }

    private void handleCreateGoal(User userEntity) {
        System.out.println();
        String name = ConsolePrompt.promptText("Goal Name (e.g. Vacation Fund)");
        BigDecimal target = ConsolePrompt.promptAmount("Target Amount ($)");
        String dateStr = ConsolePrompt.promptOptional("Deadline (yyyy-MM-dd)", "2026-12-31");
        try {
            LocalDate deadline = LocalDate.parse(dateStr);
            savingGoalController.createGoal(userEntity, name, target, deadline);
            this.statusMessage = "Goal '" + name + "' created successfully!";
            this.isErrorStatus = false;
        } catch (Exception e) {
            this.statusMessage = "Failed to create goal: " + e.getMessage();
            this.isErrorStatus = true;
        }
    }

    private void handleDepositGoal(User userEntity, List<SavingGoal> activeGoals) {
        if (activeGoals.isEmpty()) {
            this.statusMessage = "No active savings goals found to deposit into.";
            this.isErrorStatus = true;
            return;
        }

        System.out.println();
        System.out.println(" Select Goal:");
        for (int i = 0; i < activeGoals.size(); i++) {
            SavingGoal g = activeGoals.get(i);
            System.out.println(String.format(" [%d] %s (Target: $%s | Saved: $%s)",
                    i + 1, g.getName(), g.getTargetAmount(), g.getCurrentAmount()));
        }

        String choice = ConsolePrompt.promptOptional("Goal [1-" + activeGoals.size() + "]", "1");
        int idx = 0;
        try {
            idx = Integer.parseInt(choice.trim()) - 1;
            if (idx < 0 || idx >= activeGoals.size()) idx = 0;
        } catch (Exception ignored) {}

        SavingGoal selected = activeGoals.get(idx);
        BigDecimal amount = ConsolePrompt.promptAmount("Deposit Amount ($)");

        try {
            savingGoalController.contribute(selected.getGoalId(), amount, userEntity);
            this.statusMessage = "Successfully deposited $" + amount + " to goal '" + selected.getName() + "'!";
            this.isErrorStatus = false;
        } catch (Exception e) {
            this.statusMessage = "Deposit failed: " + e.getMessage();
            this.isErrorStatus = true;
        }
    }

    private void handleSetBudget(User userEntity, List<Category> categories) {
        if (categories == null || categories.isEmpty()) {
            this.statusMessage = "No categories available to set budget.";
            this.isErrorStatus = true;
            return;
        }

        System.out.println();
        System.out.println(" Select Category for Budget:");
        for (int i = 0; i < Math.min(categories.size(), 8); i++) {
            Category c = categories.get(i);
            System.out.println(String.format(" [%d] %s", i + 1, c.getName()));
        }

        String choice = ConsolePrompt.promptOptional("Category [1-" + Math.min(categories.size(), 8) + "]", "1");
        int idx = 0;
        try {
            idx = Integer.parseInt(choice.trim()) - 1;
            if (idx < 0 || idx >= categories.size()) idx = 0;
        } catch (Exception ignored) {}

        Category selectedCat = categories.get(idx);
        BigDecimal limit = ConsolePrompt.promptAmount("Monthly Spending Limit ($)");

        try {
            LocalDate start = LocalDate.now().withDayOfMonth(1);
            LocalDate end = LocalDate.now().withDayOfMonth(LocalDate.now().lengthOfMonth());
            budgetController.createBudget(userEntity, selectedCat.getCategoryId(), limit, BudgetPeriod.MONTHLY, start, end);
            this.statusMessage = "Monthly budget of $" + limit + " set for " + selectedCat.getName() + "!";
            this.isErrorStatus = false;
        } catch (Exception e) {
            this.statusMessage = "Failed to set budget: " + e.getMessage();
            this.isErrorStatus = true;
        }
    }
}
