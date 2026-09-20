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
import com.bank.model.BudgetView;
import com.bank.model.dto.UserDTO;
import com.bank.model.entity.Budget;
import com.bank.model.entity.Category;
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
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * DEDICATED SCREEN: MONTHLY EXPENSE BUDGETS (82 Columns)
 * Expense limits, spending progress, over-budget warnings, and metrics overview.
 */
public class BudgetManagementScreen implements Screen {
    private static final Logger logger = LoggerFactory.getLogger(BudgetManagementScreen.class);
    private static final int PAGE_SIZE = 5;

    private final BudgetController budgetController;
    private final CategoryController categoryController;
    private final AccountController accountController;

    private String transientStatus = null;
    private boolean isErrorStatus = false;

    public BudgetManagementScreen() {
        this(ControllerFactory.getBudgetController(),
             ControllerFactory.getCategoryController(),
             ControllerFactory.getAccountController());
    }

    public BudgetManagementScreen(BudgetController budgetController, CategoryController categoryController) {
        this(budgetController, categoryController, ControllerFactory.getAccountController());
    }

    public BudgetManagementScreen(BudgetController budgetController,
                                  CategoryController categoryController,
                                  AccountController accountController) {
        this.budgetController = budgetController;
        this.categoryController = categoryController;
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

        Terminal terminal = session.getTerminal();
        Attributes origAttributes = terminal.enterRawMode();
        NonBlockingReader reader = terminal.reader();

        int selectedIndex = 0;
        boolean firstRender = true;
        boolean reloadNeeded = true;

        Map<Long, String> catMap = new HashMap<>();
        List<Category> allCategories = new ArrayList<>();
        List<BudgetView> budgets = List.of();

        try {
            while (true) {
                if (reloadNeeded) {
                    catMap.clear();
                    try {
                        List<Category> categories = categoryController.getVisibleCategories(userEntity);
                        if (categories != null) {
                            allCategories = categories;
                            for (Category c : categories) {
                                catMap.put(c.getCategoryId(), c.getName());
                            }
                        }
                    } catch (Exception e) {
                        logger.error("Error loading categories", e);
                    }

                    try {
                        budgets = budgetController.getBudgetsWithUsage(userEntity);
                        if (budgets == null) budgets = List.of();
                    } catch (Exception e) {
                        logger.error("Error loading budgets", e);
                        budgets = List.of();
                    }
                    reloadNeeded = false;
                }

                // Calculate Metrics
                BigDecimal totalMonthlyCap = BigDecimal.ZERO;
                BigDecimal totalMtdSpent = BigDecimal.ZERO;
                int overBudgetCount = 0;
                int activeCount = budgets.size();

                for (BudgetView bv : budgets) {
                    Budget b = bv.getBudget();
                    BigDecimal limit = b.getAmountLimit() != null ? b.getAmountLimit() : BigDecimal.ZERO;
                    BigDecimal spent = bv.getActualSpending() != null ? bv.getActualSpending() : BigDecimal.ZERO;
                    totalMonthlyCap = totalMonthlyCap.add(limit);
                    totalMtdSpent = totalMtdSpent.add(spent);
                    if (spent.compareTo(limit) > 0) {
                        overBudgetCount++;
                    }
                }

                double spentPct = (totalMonthlyCap.compareTo(BigDecimal.ZERO) > 0)
                        ? (totalMtdSpent.doubleValue() / totalMonthlyCap.doubleValue()) * 100.0
                        : 0.0;

                // Frame Building
                StringBuilder sb = new StringBuilder();
                sb.append(TUIBox.top(width)).append("\n");
                sb.append(TUIBox.line(ConsoleTheme.primary("DIGIBANK CORE > FINANCIAL PLANNING > MONTHLY EXPENSE BUDGETS"), width)).append("\n");
                sb.append(TUIBox.divider(width)).append("\n");

                String currentMonth = LocalDate.now().format(DateTimeFormatter.ofPattern("MMMM yyyy")).toUpperCase();
                sb.append(TUIBox.line("ACTIVE BUDGETARY LIMITS (" + currentMonth + ")", width)).append("\n");
                sb.append(TUIBox.emptyLine(width)).append("\n");

                // Table Header (78 chars inside box)
                String header = "  CATEGORY         BUDGET LIMIT      SPENT REMAINING      USAGE PROGRESS      ";
                sb.append(TUIBox.line(header, width)).append("\n");
                sb.append(TUIBox.line("  " + "─".repeat(76), width)).append("\n");

                if (budgets.isEmpty()) {
                    selectedIndex = 0;
                    renderDefaultBudgetRows(sb, width, selectedIndex);
                } else {
                    int maxIdx = Math.max(0, budgets.size() - 1);
                    selectedIndex = Math.max(0, Math.min(selectedIndex, maxIdx));

                    for (int i = 0; i < budgets.size(); i++) {
                        BudgetView bv = budgets.get(i);
                        Budget b = bv.getBudget();
                        String catName = catMap.getOrDefault(b.getCategoryId(), "General");
                        if (catName.length() > 15) catName = catName.substring(0, 15);

                        BigDecimal limit = b.getAmountLimit() != null ? b.getAmountLimit() : BigDecimal.ONE;
                        BigDecimal spent = bv.getActualSpending() != null ? bv.getActualSpending() : BigDecimal.ZERO;
                        BigDecimal rem = bv.getRemainingAmount() != null ? bv.getRemainingAmount() : BigDecimal.ZERO;

                        String limitStr = "$ " + String.format("%10s", df.format(limit));
                        String spentStr = "$ " + String.format("%8s", df.format(spent));
                        String remStr;
                        if (rem.compareTo(BigDecimal.ZERO) < 0) {
                            remStr = "-$ " + String.format("%7s", df.format(rem.abs()));
                        } else {
                            remStr = "$ " + String.format("%8s", df.format(rem));
                        }

                        double pctDouble = (limit.compareTo(BigDecimal.ZERO) > 0)
                                ? (spent.doubleValue() / limit.doubleValue()) * 100.0
                                : 0.0;
                        int percentage = (int) Math.round(pctDouble);
                        percentage = Math.max(0, percentage);

                        // 14-slot progress bar
                        int filledSlots = Math.min(14, (percentage * 14) / 100);
                        filledSlots = Math.max(0, filledSlots);
                        int emptySlots = Math.max(0, 14 - filledSlots);
                        String progressBar = "█".repeat(filledSlots) + "░".repeat(emptySlots);

                        boolean isSelected = (i == selectedIndex);
                        String prefix = isSelected ? "▸" : " ";
                        boolean isOverBudget = spent.compareTo(limit) > 0;
                        String alertFlag = isOverBudget ? " !" : "  ";

                        String progressStr = String.format("[%s] %5.1f%%%s", progressBar, pctDouble, alertFlag);

                        String row = String.format("%s %-15s %12s  %10s %10s %s",
                                prefix, catName, limitStr, spentStr, remStr, progressStr);

                        if (row.length() > 78) {
                            row = row.substring(0, 78);
                        } else {
                            row = String.format("%-78s", row);
                        }

                        if (isSelected) {
                            sb.append(TUIBox.line(ConsoleTheme.inlineHighlight(row), width)).append("\n");
                        } else {
                            String coloredBar = (percentage >= 100) ? Ansi.red(progressBar)
                                    : (percentage >= 75) ? Ansi.yellow(progressBar)
                                    : Ansi.green(progressBar);
                            String coloredRow = row.replace("[" + progressBar + "]", "[" + coloredBar + "]");
                            if (isOverBudget) {
                                coloredRow = coloredRow.replace(" !", Ansi.red(" !"));
                            }
                            sb.append(TUIBox.line(coloredRow, width)).append("\n");
                        }
                    }

                    for (int i = budgets.size(); i < PAGE_SIZE; i++) {
                        sb.append(TUIBox.emptyLine(width)).append("\n");
                    }
                }

                sb.append(TUIBox.divider(width)).append("\n");
                sb.append(TUIBox.line("METRICS", width)).append("\n");
                sb.append(TUIBox.emptyLine(width)).append("\n");

                String m1 = String.format("  Total Monthly Cap : $ %s USD", df.format(totalMonthlyCap));
                String m2 = String.format("Active Categories : %d", activeCount);
                int sp1 = Math.max(2, 78 - m1.length() - m2.length());
                sb.append(TUIBox.line(m1 + " ".repeat(sp1) + m2, width)).append("\n");

                String m3 = String.format("  Total MTD Spent   : $ %s USD (%.1f%%)", df.format(totalMtdSpent), spentPct);
                String m4 = String.format("Over-budget Items : %d %s", overBudgetCount, (overBudgetCount == 1 ? "Category" : "Categories"));
                int sp2 = Math.max(2, 78 - m3.length() - m4.length());
                sb.append(TUIBox.line(m3 + " ".repeat(sp2) + m4, width)).append("\n");

                sb.append(TUIBox.emptyLine(width)).append("\n");
                sb.append(TUIBox.divider(width)).append("\n");

                String currentStatus = (transientStatus != null) ? transientStatus
                        : "Select category to modify limit. Press [N] to create a new budget cap.";
                String statusDisplay = isErrorStatus ? ConsoleTheme.error(currentStatus) : currentStatus;
                sb.append(TUIBox.line("Status: " + statusDisplay, width)).append("\n");
                sb.append(TUIBox.bottom(width)).append("\n");

                sb.append(Ansi.keyGuide("[↑/↓] Select  •  [Enter] Edit Limit  •  [N] New Category  •  [X] Delete  •  [Esc] Back")).append("\n");

                if (firstRender) {
                    System.out.print("\033[H\033[2J");
                    System.out.flush();
                }
                ScreenRenderer.render(sb.toString(), firstRender);
                firstRender = false;

                int ch = reader.read();
                if (ch == -1) break;

                if (ch == 27) { // ESC / Arrow sequence
                    int next1 = reader.read(25);
                    if (next1 == '[' || next1 == 'O') {
                        int next2 = reader.read(25);
                        if (next2 == 'A') { // Up
                            selectedIndex = Math.max(0, selectedIndex - 1);
                            transientStatus = null;
                        } else if (next2 == 'B') { // Down
                            int count = Math.max(1, budgets.size());
                            selectedIndex = Math.min(count - 1, selectedIndex + 1);
                            transientStatus = null;
                        }
                    } else if (next1 == -2 || next1 == -1) { // Pure ESC (timeout or EOF)
                        terminal.setAttributes(origAttributes);
                        navigator.pop();
                        return;
                    }
                    continue;
                }

                if (ch == 'w' || ch == 'W' || ch == 'k' || ch == 'K') {
                    selectedIndex = Math.max(0, selectedIndex - 1);
                    transientStatus = null;
                } else if (ch == 's' || ch == 'S' || ch == 'j' || ch == 'J') {
                    int count = Math.max(1, budgets.size());
                    selectedIndex = Math.min(count - 1, selectedIndex + 1);
                    transientStatus = null;
                } else if (ch == '\r' || ch == '\n') { // Enter: Edit limit
                    if (!budgets.isEmpty() && selectedIndex < budgets.size()) {
                        BudgetView bv = budgets.get(selectedIndex);
                        Budget b = bv.getBudget();
                        Category selectedCategory = null;
                        if (b.getCategoryId() != null) {
                            for (Category c : allCategories) {
                                if (c.getCategoryId().equals(b.getCategoryId())) {
                                    selectedCategory = c;
                                    break;
                                }
                            }
                        }
                        terminal.setAttributes(origAttributes);
                        navigator.push(new SetMonthlyBudgetScreen(budgetController, categoryController, selectedCategory, 0));
                        return;
                    }
                } else if (ch == 'n' || ch == 'N') { // New Category / Budget
                    Category created = CreateCategoryModal.show(terminal, origAttributes, reader, categoryController, userEntity);
                    if (created != null) {
                        transientStatus = ConsoleTheme.success("Category '" + created.getName() + "' created.");
                        isErrorStatus = false;
                        reloadNeeded = true;
                    }
                    firstRender = true;
                } else if (ch == 'x' || ch == 'X' || ch == 'd' || ch == 'D') { // Delete
                    if (!budgets.isEmpty() && selectedIndex < budgets.size()) {
                        BudgetView bv = budgets.get(selectedIndex);
                        Budget b = bv.getBudget();
                        try {
                            budgetController.deleteBudget(userEntity, b.getBudgetId());
                            transientStatus = ConsoleTheme.success("Budget limit deleted successfully.");
                            isErrorStatus = false;
                            reloadNeeded = true;
                        } catch (Exception ex) {
                            transientStatus = ConsoleTheme.error("Failed to delete budget: " + ex.getMessage());
                            isErrorStatus = true;
                        }
                    }
                } else if (ch == 'b' || ch == 'B') {
                    terminal.setAttributes(origAttributes);
                    navigator.pop();
                    return;
                }
            }
        } catch (IOException e) {
            logger.error("Error reading key on BudgetManagementScreen", e);
        } finally {
            terminal.setAttributes(origAttributes);
        }
    }

    private void renderDefaultBudgetRows(StringBuilder sb, int width, int selectedIndex) {
        String[][] defaults = {
                {"Food", "$     200.00", "$   0.00", "$  200.00", "░".repeat(14), "0.0%"},
                {"Entertainment", "$     300.00", "$  80.00", "$  220.00", "████" + "░".repeat(10), "26.7%"},
                {"Shopping", "$     150.00", "$ 180.00", "-$  30.00", "█".repeat(14), "120.0%"}
        };

        for (int i = 0; i < defaults.length; i++) {
            String[] r = defaults[i];
            boolean isSelected = (i == selectedIndex);
            String prefix = isSelected ? "▸" : " ";
            boolean isOver = r[5].startsWith("120");
            String alert = isOver ? " !" : "  ";
            String prog = String.format("[%s] %5s%s", r[4], r[5], alert);

            String row = String.format("%s %-15s %12s  %10s %10s %s",
                    prefix, r[0], r[1], r[2], r[3], prog);

            if (row.length() > 78) {
                row = row.substring(0, 78);
            } else {
                row = String.format("%-78s", row);
            }

            if (isSelected) {
                sb.append(TUIBox.line(ConsoleTheme.inlineHighlight(row), width)).append("\n");
            } else {
                String coloredBar = isOver ? Ansi.red(r[4]) : Ansi.green(r[4]);
                String coloredRow = row.replace("[" + r[4] + "]", "[" + coloredBar + "]");
                if (isOver) {
                    coloredRow = coloredRow.replace(" !", Ansi.red(" !"));
                }
                sb.append(TUIBox.line(coloredRow, width)).append("\n");
            }
        }

        for (int i = defaults.length; i < PAGE_SIZE; i++) {
            sb.append(TUIBox.emptyLine(width)).append("\n");
        }
    }
}
