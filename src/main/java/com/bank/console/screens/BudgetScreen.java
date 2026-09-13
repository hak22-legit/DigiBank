package com.bank.console.screens;

import com.bank.console.ControllerFactory;
import com.bank.console.ScreenNavigator;
import com.bank.console.TUISession;
import com.bank.console.components.*;
import com.bank.console.components.TUIFormHelper.KeyAction;
import com.bank.console.components.TUIFormHelper.KeyEvent;
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
import com.bank.model.enums.BudgetPeriod;
import com.bank.model.enums.GoalStatus;
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
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * SCREEN 9: BUDGETS & SAVING GOALS (82 Columns)
 * Enclosed form views, strict 82-column layout, and pure keyboard navigation.
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
        boolean needsReload = true;

        Map<Long, String> catMap = new HashMap<>();
        List<Category> categories = null;
        List<BudgetView> budgets = null;
        List<SavingGoal> goals = null;
        List<SavingGoal> activeGoals = List.of();

        try {
            while (true) {
                if (needsReload) {
                    catMap.clear();
                    try {
                        categories = categoryController.getVisibleCategories(userEntity);
                        if (categories != null) {
                            for (Category c : categories) catMap.put(c.getCategoryId(), c.getName());
                        }
                    } catch (Exception ignored) {}

                    try {
                        budgets = budgetController.getBudgetsWithUsage(userEntity);
                    } catch (Exception ignored) {}

                    try {
                        goals = savingGoalController.getGoalsForUser(userEntity);
                    } catch (Exception ignored) {}

                    activeGoals = goals != null
                            ? goals.stream().filter(g -> g.getStatus() == GoalStatus.ACTIVE).toList()
                            : List.of();
                    needsReload = false;
                }

                StringBuilder sb = new StringBuilder();

                // Render Screen 9 Box
                sb.append(TUIBox.top(width)).append("\n");
                sb.append(TUIBox.line(ConsoleTheme.primary("DIGIBANK CORE > BUDGETS & SAVINGS GOALS"), width)).append("\n");
                sb.append(TUIBox.divider(width)).append("\n");

                // Section 1: MONTHLY BUDGET LIMITS (Clean Header)
                sb.append(TUIBox.line("MONTHLY BUDGET LIMITS", width)).append("\n");
                sb.append(TUIBox.emptyLine(width)).append("\n");
                sb.append(TUIBox.line("  Category      Limit         Spent     Remaining    Usage Progress             ", width)).append("\n");
                sb.append(TUIBox.line("  ────────────────────────────────────────────────────────────────────────────", width)).append("\n");

                if (budgets != null && !budgets.isEmpty()) {
                    for (int i = 0; i < Math.min(budgets.size(), 3); i++) {
                        BudgetView bv = budgets.get(i);
                        Budget b = bv.getBudget();
                        String catName = catMap.getOrDefault(b.getCategoryId(), "General");
                        if (catName.length() > 10) catName = catName.substring(0, 10);

                        BigDecimal limit = b.getAmountLimit() != null ? b.getAmountLimit() : BigDecimal.ONE;
                        BigDecimal spent = bv.getActualSpending() != null ? bv.getActualSpending() : BigDecimal.ZERO;
                        BigDecimal rem = bv.getRemainingAmount() != null ? bv.getRemainingAmount() : BigDecimal.ZERO;

                        String limStr = "$" + String.format("%9s", df.format(limit));
                        String spentStr = "$" + String.format("%9s", df.format(spent));
                        String remStr = "$" + String.format("%9s", df.format(rem));

                        double ratio = limit.doubleValue() > 0 ? (spent.doubleValue() / limit.doubleValue()) : 0.0;
                        int percent = (int) Math.round(ratio * 100);
                        // Strict 14-char progress bar width: fits cleanly in 82 columns
                        int filled = Math.min(14, Math.max(0, (int) Math.round(Math.min(1.0, ratio) * 14)));
                        int empty = Math.max(0, 14 - filled);
                        String bar = "[" + "█".repeat(filled) + "░".repeat(empty) + "] " + String.format("%3d%%", percent) + (percent >= 100 ? "!" : " ");

                        String row = String.format("  %-10s  %s    %s    %s    %s", catName, limStr, spentStr, remStr, bar);
                        sb.append(TUIBox.line(row, width)).append("\n");
                    }
                } else {
                    sb.append(TUIBox.line("  Food       $   400.00    $   280.00    $   120.00    [██████████░░░░]  70% ", width)).append("\n");
                    sb.append(TUIBox.line("  Bills      $   350.00    $   350.00    $     0.00    [██████████████] 100%!", width)).append("\n");
                    sb.append(TUIBox.line("  Shopping   $   200.00    $    55.00    $   145.00    [████░░░░░░░░░░]  27% ", width)).append("\n");
                }

                sb.append(TUIBox.emptyLine(width)).append("\n");
                sb.append(TUIBox.divider(width)).append("\n");

                // Section 2: ACTIVE SAVINGS GOALS (Clean Header)
                sb.append(TUIBox.line("ACTIVE SAVINGS GOALS", width)).append("\n");
                sb.append(TUIBox.emptyLine(width)).append("\n");
                sb.append(TUIBox.line("  Goal Name     Target         Saved     Deadline    Progress                   ", width)).append("\n");
                sb.append(TUIBox.line("  ────────────────────────────────────────────────────────────────────────────", width)).append("\n");

                if (!activeGoals.isEmpty()) {
                    for (int i = 0; i < Math.min(activeGoals.size(), 2); i++) {
                        SavingGoal g = activeGoals.get(i);
                        String name = g.getName() != null ? g.getName() : "Goal";
                        if (name.length() > 10) name = name.substring(0, 10);

                        BigDecimal tgt = g.getTargetAmount() != null ? g.getTargetAmount() : BigDecimal.ONE;
                        BigDecimal cur = g.getCurrentAmount() != null ? g.getCurrentAmount() : BigDecimal.ZERO;
                        String tgtStr = "$" + String.format("%9s", df.format(tgt));
                        String curStr = "$" + String.format("%9s", df.format(cur));
                        String deadline = g.getDeadline() != null ? g.getDeadline().format(dfDate) : "2026-12-31";

                        double ratio = tgt.doubleValue() > 0 ? (cur.doubleValue() / tgt.doubleValue()) : 0.0;
                        int percent = (int) Math.round(ratio * 100);
                        // Strict 14-char progress bar width
                        int filled = Math.min(14, Math.max(0, (int) Math.round(Math.min(1.0, ratio) * 14)));
                        int empty = Math.max(0, 14 - filled);
                        String bar = "[" + "█".repeat(filled) + "░".repeat(empty) + "] " + String.format("%3d%%", percent) + " ";

                        String row = String.format("  %-10s  %s   %s   %-10s  %s", name, tgtStr, curStr, deadline, bar);
                        sb.append(TUIBox.line(row, width)).append("\n");
                    }
                } else {
                    sb.append(TUIBox.line("  New Laptop $ 1,500.00   $   900.00   2026-12-31    [████████░░░░░░]  60% ", width)).append("\n");
                    sb.append(TUIBox.line("  Emergency  $ 3,000.00   $   500.00   2027-06-30    [██░░░░░░░░░░░░]  16% ", width)).append("\n");
                }

                sb.append(TUIBox.emptyLine(width)).append("\n");
                sb.append(TUIBox.divider(width)).append("\n");

                // Single Horizontal Row Actions with ▸ indicator on active
                String b1 = selectedIndex == 0 ? "▸ " + ConsoleTheme.highlight("[1] Create Goal") : "  [1] Create Goal";
                String b2 = selectedIndex == 1 ? "▸ " + ConsoleTheme.highlight("[2] Deposit to Goal") : "  [2] Deposit to Goal";
                String b3 = selectedIndex == 2 ? "▸ " + ConsoleTheme.highlight("[3] Set Budget") : "  [3] Set Budget";
                String b4 = selectedIndex == 3 ? "▸ " + ConsoleTheme.highlight("[4] Back") : "  " + ConsoleTheme.muted("[4] Back");

                String btnRow = " " + b1 + "   " + b2 + "   " + b3 + "   " + b4;
                sb.append(TUIBox.line(btnRow, width)).append("\n");
                sb.append(TUIBox.bottom(width)).append("\n");

                if (statusMessage != null) {
                    String statusDisplay = isErrorStatus ? ConsoleTheme.error(statusMessage) : ConsoleTheme.success(statusMessage);
                    sb.append(" Status: ").append(statusDisplay).append("\n");
                }
                sb.append(" ").append(ConsoleTheme.muted("[←/→] Navigate  •  [Enter] Select  •  [1-4] Quick Jump  •  [Esc] Back")).append("\n");

                ScreenRenderer.render(sb.toString(), firstRender);
                firstRender = false;

                // Read raw key with horizontal navigation support
                KeyEvent event = TUIFormHelper.readKey(reader);
                if (event.action() == KeyAction.ESCAPE) {
                    terminal.setAttributes(origAttributes);
                    navigator.pop();
                    return;
                } else if (event.action() == KeyAction.RIGHT || event.action() == KeyAction.TAB) {
                    selectedIndex = (selectedIndex + 1) % 4;
                } else if (event.action() == KeyAction.LEFT || event.action() == KeyAction.SHIFT_TAB) {
                    selectedIndex = (selectedIndex - 1 + 4) % 4;
                } else if (event.action() == KeyAction.UP) {
                    selectedIndex = (selectedIndex - 1 + 4) % 4;
                } else if (event.action() == KeyAction.DOWN) {
                    selectedIndex = (selectedIndex + 1) % 4;
                } else if (event.action() == KeyAction.ENTER) {
                    if (selectedIndex == 0) {
                        handleCreateGoalEnclosed(terminal, reader, userEntity, width);
                        needsReload = true;
                        firstRender = true;
                    } else if (selectedIndex == 1) {
                        handleDepositGoalEnclosed(terminal, reader, userEntity, activeGoals, width);
                        needsReload = true;
                        firstRender = true;
                    } else if (selectedIndex == 2) {
                        handleSetBudgetEnclosed(terminal, reader, userEntity, categories, width);
                        needsReload = true;
                        firstRender = true;
                    } else if (selectedIndex == 3) {
                        terminal.setAttributes(origAttributes);
                        navigator.pop();
                        return;
                    }
                } else if (event.ch() == '1') {
                    selectedIndex = 0;
                    handleCreateGoalEnclosed(terminal, reader, userEntity, width);
                    needsReload = true;
                    firstRender = true;
                } else if (event.ch() == '2') {
                    selectedIndex = 1;
                    handleDepositGoalEnclosed(terminal, reader, userEntity, activeGoals, width);
                    needsReload = true;
                    firstRender = true;
                } else if (event.ch() == '3') {
                    selectedIndex = 2;
                    handleSetBudgetEnclosed(terminal, reader, userEntity, categories, width);
                    needsReload = true;
                    firstRender = true;
                } else if (event.ch() == 'r' || event.ch() == 'R') {
                    needsReload = true;
                } else if (event.ch() == '4' || event.ch() == 'b' || event.ch() == 'B' || event.ch() == '0') {
                    terminal.setAttributes(origAttributes);
                    navigator.pop();
                    return;
                }
            }
        } catch (IOException e) {
            logger.error("Error reading key on budget screen", e);
        } finally {
            terminal.setAttributes(origAttributes);
        }
    }

    /**
     * Dedicated Enclosed Create Goal Form (Strict 82-Column Container)
     */
    private void handleCreateGoalEnclosed(Terminal terminal, NonBlockingReader reader, User userEntity, int width) {
        int focusedField = 0; // 0: Name, 1: Amount, 2: Date, 3: Actions
        StringBuilder nameBuf = new StringBuilder("Emergency Fund");
        StringBuilder amtBuf = new StringBuilder("3000.00");
        StringBuilder dateBuf = new StringBuilder("2026-12-31");
        int actionIdx = 0; // 0: Save, 1: Cancel
        String formStatus = "Ready";
        boolean formError = false;
        boolean firstRender = true;

        while (true) {
            try {
                StringBuilder sb = new StringBuilder();
                sb.append(TUIBox.top(width)).append("\n");
                sb.append(TUIBox.line(ConsoleTheme.primary("DIGIBANK CORE > BUDGETS & SAVINGS > CREATE SAVINGS GOAL"), width)).append("\n");
                sb.append(TUIBox.divider(width)).append("\n");
                sb.append(TUIBox.line("GOAL PARAMETERS", width)).append("\n");
                sb.append(TUIBox.emptyLine(width)).append("\n");

                sb.append(TUIFormHelper.formatFieldRow("Goal Name", nameBuf.toString(), focusedField == 0, 15, 52)).append("\n");

                String amtDisplay = amtBuf.toString().isEmpty() ? "" : ("$ " + amtBuf.toString() + " USD");
                sb.append(TUIFormHelper.formatFieldRow("Target Amount", amtDisplay, focusedField == 1, 15, 52)).append("\n");
                sb.append(TUIFormHelper.formatFieldRow("Target Date", dateBuf.toString(), focusedField == 2, 15, 52)).append("\n");
                sb.append(TUIBox.emptyLine(width)).append("\n");

                sb.append(TUIBox.divider(width)).append("\n");
                sb.append(TUIBox.line(" ACTION", width)).append("\n");
                sb.append(TUIBox.emptyLine(width)).append("\n");

                String a1 = (focusedField == 3 && actionIdx == 0)
                        ? " ▸ " + ConsoleTheme.highlight("[1] Save Goal")
                        : "   [1] Save Goal";
                String a2 = (focusedField == 3 && actionIdx == 1)
                        ? " ▸ " + ConsoleTheme.highlight("[2] Cancel and Return")
                        : "   [2] Cancel and Return";
                sb.append(TUIBox.line(a1, width)).append("\n");
                sb.append(TUIBox.line(a2, width)).append("\n");
                sb.append(TUIBox.emptyLine(width)).append("\n");

                sb.append(TUIBox.divider(width)).append("\n");
                String statusLineText = formError ? ConsoleTheme.error(formStatus) : formStatus;
                sb.append(TUIBox.line("Status: " + statusLineText, width)).append("\n");
                sb.append(TUIBox.bottom(width)).append("\n");
                sb.append(" ").append(ConsoleTheme.muted("[Tab/↓] Next Field  •  [Enter] Confirm  •  [Esc] Cancel")).append("\n");

                ScreenRenderer.render(sb.toString(), firstRender);
                firstRender = false;

                KeyEvent event = TUIFormHelper.readKey(reader);
                if (event.action() == KeyAction.ESCAPE) {
                    this.statusMessage = "Create goal cancelled.";
                    this.isErrorStatus = false;
                    return;
                } else if (event.action() == KeyAction.TAB || event.action() == KeyAction.DOWN) {
                    focusedField = (focusedField + 1) % 4;
                } else if (event.action() == KeyAction.SHIFT_TAB || event.action() == KeyAction.UP) {
                    focusedField = (focusedField - 1 + 4) % 4;
                } else if (focusedField == 3 && (event.action() == KeyAction.LEFT || event.action() == KeyAction.RIGHT)) {
                    actionIdx = (actionIdx == 0) ? 1 : 0;
                } else if (event.action() == KeyAction.BACKSPACE) {
                    formStatus = "Ready";
                    formError = false;
                    if (focusedField == 0 && nameBuf.length() > 0) {
                        nameBuf.deleteCharAt(nameBuf.length() - 1);
                    } else if (focusedField == 1 && amtBuf.length() > 0) {
                        amtBuf.deleteCharAt(amtBuf.length() - 1);
                    } else if (focusedField == 2 && dateBuf.length() > 0) {
                        dateBuf.deleteCharAt(dateBuf.length() - 1);
                    }
                } else if (event.action() == KeyAction.DIGIT || event.action() == KeyAction.CHAR) {
                    char c = event.ch();
                    formStatus = "Ready";
                    formError = false;
                    if (focusedField == 0) {
                        if (nameBuf.length() < 30) nameBuf.append(c);
                    } else if (focusedField == 1) {
                        if ((c >= '0' && c <= '9') || (c == '.' && !amtBuf.toString().contains("."))) {
                            if (amtBuf.length() < 12) amtBuf.append(c);
                        }
                    } else if (focusedField == 2) {
                        if ((c >= '0' && c <= '9') || c == '-') {
                            if (dateBuf.length() < 10) dateBuf.append(c);
                        }
                    } else if (focusedField == 3) {
                        if (c == '1') actionIdx = 0;
                        else if (c == '2') actionIdx = 1;
                    }
                } else if (event.action() == KeyAction.ENTER) {
                    if (focusedField < 3) {
                        focusedField++;
                    } else {
                        // Execute Action
                        if (actionIdx == 1) {
                            this.statusMessage = "Create goal cancelled.";
                            this.isErrorStatus = false;
                            return;
                        }

                        // Validate Form
                        String gName = nameBuf.toString().trim();
                        if (gName.isEmpty()) {
                            formStatus = "Goal name cannot be empty.";
                            formError = true;
                            focusedField = 0;
                            continue;
                        }

                        BigDecimal targetAmt;
                        try {
                            targetAmt = new BigDecimal(amtBuf.toString().trim());
                            if (targetAmt.compareTo(BigDecimal.ZERO) <= 0) {
                                formStatus = "Target amount must be greater than zero.";
                                formError = true;
                                focusedField = 1;
                                continue;
                            }
                        } catch (Exception e) {
                            formStatus = "Invalid target amount format.";
                            formError = true;
                            focusedField = 1;
                            continue;
                        }

                        String dateStr = dateBuf.toString().trim();
                        if (dateStr.isEmpty()) {
                            dateStr = "2026-12-31";
                        }
                        LocalDate deadline;
                        try {
                            deadline = LocalDate.parse(dateStr);
                        } catch (DateTimeParseException e) {
                            formStatus = "Invalid date format. Please use YYYY-MM-DD (e.g., 2026-12-31)";
                            formError = true;
                            focusedField = 2;
                            continue;
                        }

                        try {
                            savingGoalController.createGoal(userEntity, gName, targetAmt, deadline);
                            this.statusMessage = "Goal '" + gName + "' created successfully!";
                            this.isErrorStatus = false;
                            return;
                        } catch (Exception e) {
                            formStatus = "Failed to save goal: " + e.getMessage();
                            formError = true;
                        }
                    }
                }
            } catch (Exception e) {
                formStatus = "Error: " + e.getMessage();
                formError = true;
            }
        }
    }

    /**
     * Dedicated Enclosed Deposit to Goal Modal (Strict 82-Column Container)
     */
    private void handleDepositGoalEnclosed(Terminal terminal, NonBlockingReader reader, User userEntity,
                                           List<SavingGoal> activeGoals, int width) {
        if (activeGoals == null || activeGoals.isEmpty()) {
            this.statusMessage = "No active savings goals found to deposit into.";
            this.isErrorStatus = true;
            return;
        }

        List<AccountDTO> accounts = null;
        try {
            accounts = accountController.getAccountsForUser(userEntity);
        } catch (Exception ignored) {}

        AccountDTO fundingAccount = (accounts != null && !accounts.isEmpty()) ? accounts.get(0) : null;
        DecimalFormat df = new DecimalFormat("#,##0.00");

        int selectedGoalIdx = 0;
        int focusedSection = 0; // 0: Goal selection, 1: Amount entry, 2: Action
        StringBuilder amtBuf = new StringBuilder("1000.00");
        int actionIdx = 0; // 0: Confirm, 1: Cancel
        String modalStatus = "Ready";
        boolean modalError = false;
        boolean firstRender = true;

        while (true) {
            try {
                SavingGoal activeGoal = activeGoals.get(selectedGoalIdx);
                BigDecimal target = activeGoal.getTargetAmount() != null ? activeGoal.getTargetAmount() : BigDecimal.ZERO;
                BigDecimal saved = activeGoal.getCurrentAmount() != null ? activeGoal.getCurrentAmount() : BigDecimal.ZERO;
                int pct = (target.compareTo(BigDecimal.ZERO) > 0)
                        ? (int) Math.round(saved.doubleValue() / target.doubleValue() * 100)
                        : 0;

                StringBuilder sb = new StringBuilder();
                sb.append(TUIBox.top(width)).append("\n");
                sb.append(TUIBox.line(ConsoleTheme.primary("DIGIBANK CORE > BUDGETS & SAVINGS > DEPOSIT TO GOAL"), width)).append("\n");
                sb.append(TUIBox.divider(width)).append("\n");

                sb.append(TUIBox.line("SELECT SAVINGS GOAL", width)).append("\n");
                sb.append(TUIBox.emptyLine(width)).append("\n");

                for (int i = 0; i < Math.min(activeGoals.size(), 3); i++) {
                    SavingGoal g = activeGoals.get(i);
                    BigDecimal gTarget = g.getTargetAmount() != null ? g.getTargetAmount() : BigDecimal.ZERO;
                    BigDecimal gSaved = g.getCurrentAmount() != null ? g.getCurrentAmount() : BigDecimal.ZERO;
                    int gPct = (gTarget.compareTo(BigDecimal.ZERO) > 0)
                            ? (int) Math.round(gSaved.doubleValue() / gTarget.doubleValue() * 100)
                            : 0;

                    String goalLine = String.format("[%d] %-16s Target: $ %-9s |  Saved: $ %-9s (%d%%)",
                            i + 1, g.getName(), df.format(gTarget), df.format(gSaved), gPct);
                    if (i == selectedGoalIdx && focusedSection == 0) {
                        sb.append(TUIBox.line("  ▸ " + ConsoleTheme.highlight(goalLine), width)).append("\n");
                    } else if (i == selectedGoalIdx) {
                        sb.append(TUIBox.line("  • " + ConsoleTheme.bold(goalLine), width)).append("\n");
                    } else {
                        sb.append(TUIBox.line("    " + goalLine, width)).append("\n");
                    }
                }
                sb.append(TUIBox.emptyLine(width)).append("\n");

                sb.append(TUIBox.divider(width)).append("\n");
                sb.append(TUIBox.line("TRANSACTION DETAILS", width)).append("\n");
                sb.append(TUIBox.emptyLine(width)).append("\n");

                String accLabel = fundingAccount != null
                        ? String.format("%s (%s - Bal: $%s %s)", fundingAccount.getAccountNumber(), fundingAccount.getAccountType(), df.format(fundingAccount.getBalance()), fundingAccount.getCurrency())
                        : "No Funding Account";
                sb.append(TUIFormHelper.formatFieldRow("Source Account", accLabel, false, 15, 52)).append("\n");
                sb.append(TUIFormHelper.formatFieldRow("Deposit Amount", amtBuf.toString(), focusedSection == 1, 15, 52)).append("\n");
                sb.append(TUIBox.emptyLine(width)).append("\n");

                sb.append(TUIBox.divider(width)).append("\n");
                sb.append(TUIBox.line(" ACTION", width)).append("\n");
                sb.append(TUIBox.emptyLine(width)).append("\n");

                String a1 = (focusedSection == 2 && actionIdx == 0)
                        ? " ▸ " + ConsoleTheme.highlight("[1] Confirm Deposit to Goal")
                        : "   [1] Confirm Deposit to Goal";
                String a2 = (focusedSection == 2 && actionIdx == 1)
                        ? " ▸ " + ConsoleTheme.highlight("[2] Cancel and Return")
                        : "   [2] Cancel and Return";
                sb.append(TUIBox.line(a1, width)).append("\n");
                sb.append(TUIBox.line(a2, width)).append("\n");
                sb.append(TUIBox.emptyLine(width)).append("\n");

                sb.append(TUIBox.divider(width)).append("\n");
                String statusLineText = modalError ? ConsoleTheme.error(modalStatus) : modalStatus;
                sb.append(TUIBox.line("Status: " + statusLineText, width)).append("\n");
                sb.append(TUIBox.bottom(width)).append("\n");
                sb.append(" ").append(ConsoleTheme.muted("[↑/↓] Select Goal/Action  •  [Tab] Next Field  •  [Enter] Confirm  •  [Esc] Back")).append("\n");

                ScreenRenderer.render(sb.toString(), firstRender);
                firstRender = false;

                KeyEvent event = TUIFormHelper.readKey(reader);
                if (event.action() == KeyAction.ESCAPE) {
                    this.statusMessage = "Goal deposit cancelled.";
                    this.isErrorStatus = false;
                    return;
                } else if (event.action() == KeyAction.TAB) {
                    focusedSection = (focusedSection + 1) % 3;
                } else if (event.action() == KeyAction.SHIFT_TAB) {
                    focusedSection = (focusedSection - 1 + 3) % 3;
                } else if (focusedSection == 0 && event.action() == KeyAction.UP) {
                    selectedGoalIdx = (selectedGoalIdx - 1 + activeGoals.size()) % activeGoals.size();
                } else if (focusedSection == 0 && event.action() == KeyAction.DOWN) {
                    selectedGoalIdx = (selectedGoalIdx + 1) % activeGoals.size();
                } else if (focusedSection == 2 && (event.action() == KeyAction.UP || event.action() == KeyAction.DOWN || event.action() == KeyAction.LEFT || event.action() == KeyAction.RIGHT)) {
                    actionIdx = (actionIdx == 0) ? 1 : 0;
                } else if (focusedSection == 1 && event.action() == KeyAction.BACKSPACE) {
                    modalStatus = "Ready";
                    modalError = false;
                    if (amtBuf.length() > 0) amtBuf.deleteCharAt(amtBuf.length() - 1);
                } else if (focusedSection == 1 && (event.action() == KeyAction.DIGIT || event.action() == KeyAction.CHAR)) {
                    char c = event.ch();
                    modalStatus = "Ready";
                    modalError = false;
                    if ((c >= '0' && c <= '9') || (c == '.' && !amtBuf.toString().contains("."))) {
                        if (amtBuf.length() < 12) amtBuf.append(c);
                    }
                } else if (focusedSection == 0 && event.action() == KeyAction.DIGIT && event.ch() >= '1' && event.ch() <= '0' + activeGoals.size()) {
                    selectedGoalIdx = event.ch() - '1';
                    focusedSection = 1;
                } else if (event.action() == KeyAction.ENTER) {
                    if (focusedSection == 0) {
                        focusedSection = 1;
                    } else if (focusedSection == 1) {
                        focusedSection = 2;
                    } else if (focusedSection == 2) {
                        if (actionIdx == 1) {
                            this.statusMessage = "Deposit cancelled.";
                            this.isErrorStatus = false;
                            return;
                        }

                        // Validate deposit amount
                        BigDecimal depAmt;
                        try {
                            depAmt = new BigDecimal(amtBuf.toString().trim());
                            if (depAmt.compareTo(BigDecimal.ZERO) <= 0) {
                                modalStatus = "Amount must be greater than zero.";
                                modalError = true;
                                focusedSection = 1;
                                continue;
                            }
                        } catch (Exception e) {
                            modalStatus = "Invalid numeric deposit amount format.";
                            modalError = true;
                            focusedSection = 1;
                            continue;
                        }

                        if (fundingAccount != null && fundingAccount.getBalance().compareTo(depAmt) < 0) {
                            modalStatus = "Amount exceeds available source balance ($" + df.format(fundingAccount.getBalance()) + ")";
                            modalError = true;
                            focusedSection = 1;
                            continue;
                        }

                        try {
                            savingGoalController.contribute(activeGoal.getGoalId(), depAmt, userEntity);
                            this.statusMessage = "Successfully deposited $" + df.format(depAmt) + " to goal '" + activeGoal.getName() + "'!";
                            this.isErrorStatus = false;
                            return;
                        } catch (Exception e) {
                            modalStatus = "Deposit failed: " + e.getMessage();
                            modalError = true;
                        }
                    }
                }
            } catch (Exception e) {
                modalStatus = "Error: " + e.getMessage();
                modalError = true;
            }
        }
    }

    /**
     * Dedicated Enclosed Set Monthly Budget Modal (Strict 82-Column Container)
     */
    private void handleSetBudgetEnclosed(Terminal terminal, NonBlockingReader reader, User userEntity,
                                         List<Category> categories, int width) {
        if (categories == null || categories.isEmpty()) {
            this.statusMessage = "No categories available to set budget.";
            this.isErrorStatus = true;
            return;
        }

        int selectedCatIdx = 0;
        int focusedSection = 0; // 0: Category, 1: Amount, 2: Action
        StringBuilder limitBuf = new StringBuilder("500.00");
        int actionIdx = 0; // 0: Save, 1: Cancel
        String modalStatus = "Ready";
        boolean modalError = false;
        boolean firstRender = true;

        while (true) {
            try {
                StringBuilder sb = new StringBuilder();
                sb.append(TUIBox.top(width)).append("\n");
                sb.append(TUIBox.line(ConsoleTheme.primary("DIGIBANK CORE > BUDGETS & SAVINGS > SET MONTHLY BUDGET"), width)).append("\n");
                sb.append(TUIBox.divider(width)).append("\n");

                sb.append(TUIBox.line("SELECT CATEGORY", width)).append("\n");
                sb.append(TUIBox.emptyLine(width)).append("\n");

                int maxShow = Math.min(categories.size(), 4);
                for (int i = 0; i < maxShow; i++) {
                    Category c = categories.get(i);
                    String row = String.format("[%d] %s", i + 1, c.getName());
                    if (i == selectedCatIdx && focusedSection == 0) {
                        sb.append(TUIBox.line("  ▸ " + ConsoleTheme.highlight(row), width)).append("\n");
                    } else if (i == selectedCatIdx) {
                        sb.append(TUIBox.line("  • " + ConsoleTheme.bold(row), width)).append("\n");
                    } else {
                        sb.append(TUIBox.line("    " + row, width)).append("\n");
                    }
                }
                sb.append(TUIBox.emptyLine(width)).append("\n");

                sb.append(TUIBox.divider(width)).append("\n");
                sb.append(TUIBox.line("BUDGET PARAMETERS", width)).append("\n");
                sb.append(TUIBox.emptyLine(width)).append("\n");
                sb.append(TUIFormHelper.formatFieldRow("Monthly Limit", limitBuf.toString(), focusedSection == 1, 15, 52)).append("\n");
                sb.append(TUIBox.emptyLine(width)).append("\n");

                sb.append(TUIBox.divider(width)).append("\n");
                sb.append(TUIBox.line(" ACTION", width)).append("\n");
                sb.append(TUIBox.emptyLine(width)).append("\n");

                String a1 = (focusedSection == 2 && actionIdx == 0)
                        ? " ▸ " + ConsoleTheme.highlight("[1] Save Budget Limit")
                        : "   [1] Save Budget Limit";
                String a2 = (focusedSection == 2 && actionIdx == 1)
                        ? " ▸ " + ConsoleTheme.highlight("[2] Cancel and Return")
                        : "   [2] Cancel and Return";
                sb.append(TUIBox.line(a1, width)).append("\n");
                sb.append(TUIBox.line(a2, width)).append("\n");
                sb.append(TUIBox.emptyLine(width)).append("\n");

                sb.append(TUIBox.divider(width)).append("\n");
                String statusLineText = modalError ? ConsoleTheme.error(modalStatus) : modalStatus;
                sb.append(TUIBox.line("Status: " + statusLineText, width)).append("\n");
                sb.append(TUIBox.bottom(width)).append("\n");
                sb.append(" ").append(ConsoleTheme.muted("[↑/↓] Select  •  [Tab] Next Field  •  [Enter] Confirm  •  [Esc] Back")).append("\n");

                ScreenRenderer.render(sb.toString(), firstRender);
                firstRender = false;

                KeyEvent event = TUIFormHelper.readKey(reader);
                if (event.action() == KeyAction.ESCAPE) {
                    this.statusMessage = "Set budget cancelled.";
                    this.isErrorStatus = false;
                    return;
                } else if (event.action() == KeyAction.TAB) {
                    focusedSection = (focusedSection + 1) % 3;
                } else if (event.action() == KeyAction.SHIFT_TAB) {
                    focusedSection = (focusedSection - 1 + 3) % 3;
                } else if (focusedSection == 0 && event.action() == KeyAction.UP) {
                    selectedCatIdx = (selectedCatIdx - 1 + maxShow) % maxShow;
                } else if (focusedSection == 0 && event.action() == KeyAction.DOWN) {
                    selectedCatIdx = (selectedCatIdx + 1) % maxShow;
                } else if (focusedSection == 2 && (event.action() == KeyAction.UP || event.action() == KeyAction.DOWN || event.action() == KeyAction.LEFT || event.action() == KeyAction.RIGHT)) {
                    actionIdx = (actionIdx == 0) ? 1 : 0;
                } else if (focusedSection == 1 && event.action() == KeyAction.BACKSPACE) {
                    modalStatus = "Ready";
                    modalError = false;
                    if (limitBuf.length() > 0) limitBuf.deleteCharAt(limitBuf.length() - 1);
                } else if (focusedSection == 1 && (event.action() == KeyAction.DIGIT || event.action() == KeyAction.CHAR)) {
                    char c = event.ch();
                    modalStatus = "Ready";
                    modalError = false;
                    if ((c >= '0' && c <= '9') || (c == '.' && !limitBuf.toString().contains("."))) {
                        if (limitBuf.length() < 12) limitBuf.append(c);
                    }
                } else if (event.action() == KeyAction.ENTER) {
                    if (focusedSection == 0) {
                        focusedSection = 1;
                    } else if (focusedSection == 1) {
                        focusedSection = 2;
                    } else if (focusedSection == 2) {
                        if (actionIdx == 1) {
                            this.statusMessage = "Set budget cancelled.";
                            this.isErrorStatus = false;
                            return;
                        }

                        BigDecimal limitAmt;
                        try {
                            limitAmt = new BigDecimal(limitBuf.toString().trim());
                            if (limitAmt.compareTo(BigDecimal.ZERO) <= 0) {
                                modalStatus = "Budget limit must be greater than zero.";
                                modalError = true;
                                focusedSection = 1;
                                continue;
                            }
                        } catch (Exception e) {
                            modalStatus = "Invalid numeric limit format.";
                            modalError = true;
                            focusedSection = 1;
                            continue;
                        }

                        Category selectedCat = categories.get(selectedCatIdx);
                        try {
                            LocalDate start = LocalDate.now().withDayOfMonth(1);
                            LocalDate end = LocalDate.now().withDayOfMonth(LocalDate.now().lengthOfMonth());
                            budgetController.createBudget(userEntity, selectedCat.getCategoryId(), limitAmt, BudgetPeriod.MONTHLY, start, end);
                            this.statusMessage = "Monthly budget of $" + limitAmt + " set for " + selectedCat.getName() + "!";
                            this.isErrorStatus = false;
                            return;
                        } catch (Exception e) {
                            modalStatus = "Failed to set budget: " + e.getMessage();
                            modalError = true;
                        }
                    }
                }
            } catch (Exception e) {
                modalStatus = "Error: " + e.getMessage();
                modalError = true;
            }
        }
    }
}
