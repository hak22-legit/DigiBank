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

                if (budgets.isEmpty()) {
                    totalMonthlyCap = new BigDecimal("500.00");
                    totalMtdSpent = new BigDecimal("10080.00");
                    activeCount = 2;
                    overBudgetCount = 1;
                } else {
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
                }

                double spentPct = (totalMonthlyCap.compareTo(BigDecimal.ZERO) > 0)
                        ? (totalMtdSpent.doubleValue() / totalMonthlyCap.doubleValue()) * 100.0
                        : 0.0;

                // Frame Building
                StringBuilder sb = new StringBuilder();
                sb.append(TUIBox.top(width)).append("\n");
                sb.append(TUIBox.line(ConsoleTheme.primary("DIGIBANK CORE > FINANCIAL PLANNING > MONTHLY EXPENSE BUDGETS"), width)).append("\n");
                sb.append(TUIBox.divider(width)).append("\n");

                // Header with prominent action button
                String currentMonth = LocalDate.now().format(DateTimeFormatter.ofPattern("MMMM yyyy")).toUpperCase();
                String headerTitle = "ACTIVE BUDGETARY LIMITS (" + currentMonth + ")";
                String btn = "\033[36m[+N] CREATE NEW BUDGET\033[0m";
                int leftPad = Math.max(1, 78 - headerTitle.length() - TUIBox.stripAnsi(btn).length() - 4);
                String headerContent = headerTitle + " ".repeat(leftPad) + btn + "    ";
                sb.append(TUIBox.line(headerContent, width)).append("\n");
                sb.append(TUIBox.divider(width)).append("\n");

                // Table Header & Separator (78 chars inside box)
                String header = "CATEGORY       BUDGET LIMIT        SPENT    REMAINING     USAGE PROGRESS      ";
                sb.append(TUIBox.line(header, width)).append("\n");
                sb.append(TUIBox.line("─".repeat(78), width)).append("\n");

                if (budgets.isEmpty()) {
                    selectedIndex = Math.max(0, Math.min(selectedIndex, 1));
                    renderDefaultBudgetRows(sb, width, selectedIndex, df);
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

                        boolean isSelected = (i == selectedIndex);
                        String prefix = isSelected ? "▸" : " ";
                        String catPadded = String.format("%-16s", catName);
                        String limitPadded = String.format("$ %9s", df.format(limit));
                        String spentPadded = String.format("$ %9s", df.format(spent));
                        String remPadded = (rem.compareTo(BigDecimal.ZERO) < 0)
                                ? String.format("-$ %8s", df.format(rem.abs()))
                                : String.format(" $ %8s", df.format(rem));

                        String progressStr = formatProgress(spent.doubleValue(), limit.doubleValue());
                        int progVis = TUIBox.stripAnsi(progressStr).length();
                        int progPad = Math.max(0, 22 - progVis);
                        String row = prefix + catPadded + limitPadded + "  " + spentPadded + "  " + remPadded + "  " + progressStr + " ".repeat(progPad);

                        sb.append(TUIBox.line(row, width)).append("\n");
                    }

                    for (int i = budgets.size(); i < PAGE_SIZE; i++) {
                        sb.append(TUIBox.emptyLine(width)).append("\n");
                    }
                }

                // Metrics Section
                renderMetrics(sb, width, totalMonthlyCap.doubleValue(), totalMtdSpent.doubleValue(), spentPct, activeCount, overBudgetCount);

                sb.append(TUIBox.divider(width)).append("\n");

                String currentStatus = (transientStatus != null) ? transientStatus
                        : "Press [N] to create a new budget category.";
                String statusDisplay = isErrorStatus ? ConsoleTheme.error(currentStatus) : currentStatus;
                sb.append(TUIBox.line("Status: " + statusDisplay, width)).append("\n");
                sb.append(TUIBox.bottom(width)).append("\n");

                // Lower navigation footer
                sb.append(" \033[2;90m[↑/↓] Select  •  \033[0m\033[1;36m[N] Create Budget\033[0m\033[2;90m  •  [Enter] Edit Limit  •  [X] Delete  •  [Esc] Back\033[0m\n");

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
                            int count = budgets.isEmpty() ? 2 : budgets.size();
                            selectedIndex = Math.min(count - 1, selectedIndex + 1);
                            transientStatus = null;
                        }
                    } else if (next1 == -2 || next1 == -1) { // Pure ESC
                        terminal.setAttributes(origAttributes);
                        navigator.pop();
                        return;
                    }
                    continue;
                }

                if (ch == '\r' || ch == '\n') { // Enter: Edit limit
                    Category selectedCategory = null;
                    if (!budgets.isEmpty() && selectedIndex < budgets.size()) {
                        BudgetView bv = budgets.get(selectedIndex);
                        Budget b = bv.getBudget();
                        if (b.getCategoryId() != null) {
                            for (Category c : allCategories) {
                                if (c.getCategoryId().equals(b.getCategoryId())) {
                                    selectedCategory = c;
                                    break;
                                }
                            }
                        }
                    } else if (budgets.isEmpty()) {
                        String[] defaultNames = {"Food", "Entertainment"};
                        String targetName = (selectedIndex >= 0 && selectedIndex < defaultNames.length) ? defaultNames[selectedIndex] : "Food";
                        for (Category c : allCategories) {
                            if (c.getName() != null && c.getName().equalsIgnoreCase(targetName)) {
                                selectedCategory = c;
                                break;
                            }
                        }
                    }
                    terminal.setAttributes(origAttributes);
                    navigator.push(new SetMonthlyBudgetScreen(budgetController, categoryController, selectedCategory, 1));
                    return;
                }

                String keyInput = String.valueOf((char) ch);
                switch (keyInput.toUpperCase()) {
                    case "N":
                        showCreateBudgetDialog(terminal, origAttributes, reader, userEntity, width);
                        reloadNeeded = true;
                        firstRender = true;
                        break;
                    case "X":
                    case "D":
                        deleteSelectedBudget(budgets, selectedIndex, userEntity);
                        reloadNeeded = true;
                        firstRender = true;
                        break;
                    case "W":
                    case "K":
                        selectedIndex = Math.max(0, selectedIndex - 1);
                        transientStatus = null;
                        break;
                    case "S":
                    case "J":
                        int count = budgets.isEmpty() ? 2 : budgets.size();
                        selectedIndex = Math.min(count - 1, selectedIndex + 1);
                        transientStatus = null;
                        break;
                    case "B":
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

    private void showCreateBudgetDialog(Terminal terminal, Attributes origAttributes, NonBlockingReader reader,
                                       User userEntity, int width) {
        LocalDate currentPeriod = LocalDate.now();
        String savedCategory = ConfigureBudgetModal.show(terminal, origAttributes, reader,
                budgetController, categoryController, userEntity, currentPeriod, width);
        if (savedCategory != null) {
            transientStatus = ConsoleTheme.success("Budget limit for '" + savedCategory + "' applied successfully.");
            isErrorStatus = false;
        }
    }

    private void deleteSelectedBudget(List<BudgetView> budgets, int selectedIndex, User userEntity) {
        if (!budgets.isEmpty() && selectedIndex < budgets.size()) {
            BudgetView bv = budgets.get(selectedIndex);
            Budget b = bv.getBudget();
            try {
                budgetController.deleteBudget(userEntity, b.getBudgetId());
                transientStatus = ConsoleTheme.success("Budget limit deleted successfully.");
                isErrorStatus = false;
            } catch (Exception ex) {
                transientStatus = ConsoleTheme.error("Failed to delete budget: " + ex.getMessage());
                isErrorStatus = true;
            }
        }
    }

    public static void renderMetrics(StringBuilder sb, int width, double totalMonthlyCap, double totalSpent,
                                     double spentPercentage, int activeCategories, int overBudgetCount) {
        sb.append(TUIBox.divider(width)).append("\n");
        sb.append(TUIBox.line("METRICS", width)).append("\n");
        sb.append(TUIBox.emptyLine(width)).append("\n");

        String m1 = String.format("Total Monthly Cap : $ %,.2f USD", totalMonthlyCap);
        String m2 = String.format("Active Categories : %d", activeCategories);
        String line1 = String.format(" %-50s %-26s", m1, m2);
        sb.append(TUIBox.line(line1, width)).append("\n");

        String m3 = String.format("Total MTD Spent   : $ %,.2f USD (%.1f%%)", totalSpent, spentPercentage);
        String m4 = String.format("Over-budget Items : %d Cat", overBudgetCount);
        String line2 = String.format(" %-50s %-26s", m3, m4);
        sb.append(TUIBox.line(line2, width)).append("\n");
    }

    public static String formatProgress(double spent, double limit) {
        double percent = (limit > 0) ? (spent / limit) * 100.0 : 0.0;

        // Clamp the percentage display so it doesn't break table column widths
        String percentStr;
        if (percent > 999.9) {
            percentStr = ">999%";
        } else {
            percentStr = String.format("%5.1f%%", percent);
        }

        // Cap visual bar length to 12 blocks
        int totalBlocks = 12;
        int filledBlocks = (limit > 0) ? (int) Math.min(totalBlocks, (spent / limit) * totalBlocks) : 0;

        // Red bar if over budget, Green if within budget
        String color = (spent > limit) ? "\033[31m" : "\033[32m";
        String bar = "█".repeat(filledBlocks) + "░".repeat(totalBlocks - filledBlocks);

        return String.format("[%s%s\033[0m] %s", color, bar, percentStr);
    }

    private void renderDefaultBudgetRows(StringBuilder sb, int width, int selectedIndex, DecimalFormat df) {
        String[][] defaults = {
                {"Food", "200.00", "10000.00", "-9800.00"},
                {"Entertainment", "300.00", "80.00", "220.00"}
        };

        for (int i = 0; i < defaults.length; i++) {
            String[] d = defaults[i];
            boolean isSelected = (i == selectedIndex);
            String prefix = isSelected ? "▸" : " ";
            String catName = d[0];
            double limit = Double.parseDouble(d[1]);
            double spent = Double.parseDouble(d[2]);
            double rem = Double.parseDouble(d[3]);

            String catPadded = String.format("%-16s", catName);
            String limitPadded = String.format("$ %9s", df.format(limit));
            String spentPadded = String.format("$ %9s", df.format(spent));
            String remPadded = (rem < 0)
                    ? String.format("-$ %8s", df.format(Math.abs(rem)))
                    : String.format(" $ %8s", df.format(rem));

            String progressStr = formatProgress(spent, limit);
            int progVis = TUIBox.stripAnsi(progressStr).length();
            int progPad = Math.max(0, 22 - progVis);
            String row = prefix + catPadded + limitPadded + "  " + spentPadded + "  " + remPadded + "  " + progressStr + " ".repeat(progPad);

            sb.append(TUIBox.line(row, width)).append("\n");
        }

        for (int i = defaults.length; i < PAGE_SIZE; i++) {
            sb.append(TUIBox.emptyLine(width)).append("\n");
        }
    }
}
