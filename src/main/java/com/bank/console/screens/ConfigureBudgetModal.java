package com.bank.console.screens;

import com.bank.console.components.ScreenRenderer;
import com.bank.console.components.TUIBox;
import com.bank.console.components.TUIFormHelper;
import com.bank.console.components.TUIFormHelper.KeyAction;
import com.bank.console.components.TUIFormHelper.KeyEvent;
import com.bank.console.components.TUILayout;
import com.bank.console.theme.ConsoleTheme;
import com.bank.controller.BudgetController;
import com.bank.controller.CategoryController;
import com.bank.model.BudgetView;
import com.bank.model.entity.Budget;
import com.bank.model.entity.Category;
import com.bank.model.entity.User;
import org.jline.terminal.Attributes;
import org.jline.terminal.Terminal;
import org.jline.utils.NonBlockingReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Interactive 82-column unified modal for configuring budget limit:
 * - Combines category selection and monthly cap assignment in a single step.
 * - [Space] cycles through existing categories in the database without an active budget limit for the target month/year.
 * - Allows typing any custom category name directly.
 * - Collects Monthly Cap ($) before persisting via atomic PostgreSQL UPSERT queries.
 */
public class ConfigureBudgetModal {
    private static final Logger logger = LoggerFactory.getLogger(ConfigureBudgetModal.class);

    public static final String TITLE = "DIGIBANK CORE > FINANCIAL PLANNING > CONFIGURE BUDGET";
    public static final String COMPARTMENT_HEADER = "ASSIGN BUDGETARY LIMIT";
    public static final String FOOTER = " [Space] Pick Existing  •  [Tab/Enter] Next Field  •  [1/2] Action  •  [Esc] Back";

    public static String show(Terminal terminal, Attributes origAttr, NonBlockingReader reader,
                             BudgetController budgetController, CategoryController categoryController,
                             User userEntity, LocalDate targetPeriod, int width) {

        int targetMonth = targetPeriod.getMonthValue();
        int targetYear = targetPeriod.getYear();
        String periodDisplay = targetPeriod.format(DateTimeFormatter.ofPattern("MMMM yyyy")).toUpperCase();

        // State variables
        StringBuilder categoryBuf = new StringBuilder();
        StringBuilder capBuf = new StringBuilder("200.00");
        Long selectedCategoryId = null;

        int focusedField = 0; // 0: Category Name, 1: Monthly Cap, 2: Actions
        int actionIdx = 0;    // 0: Save & Apply, 1: Cancel & Return
        String statusMessage = null;
        boolean isError = false;
        boolean firstRender = true;

        try {
            while (true) {
                String modalContent = renderModalContent(
                        categoryBuf.toString(),
                        capBuf.toString(),
                        periodDisplay,
                        focusedField,
                        actionIdx,
                        statusMessage,
                        isError,
                        width
                );

                ScreenRenderer.render(modalContent, firstRender);
                firstRender = false;

                KeyEvent event = TUIFormHelper.readKey(reader);
                if (event.action() == KeyAction.ESCAPE) {
                    return null;
                } else if (event.action() == KeyAction.TAB || event.action() == KeyAction.DOWN) {
                    focusedField = (focusedField + 1) % 3;
                } else if (event.action() == KeyAction.SHIFT_TAB || event.action() == KeyAction.UP) {
                    focusedField = (focusedField - 1 + 3) % 3;
                } else if (focusedField == 2 && (event.action() == KeyAction.LEFT || event.action() == KeyAction.RIGHT)) {
                    actionIdx = (actionIdx == 0) ? 1 : 0;
                } else if (event.action() == KeyAction.BACKSPACE) {
                    if (focusedField == 0) {
                        if (categoryBuf.length() > 0) {
                            categoryBuf.deleteCharAt(categoryBuf.length() - 1);
                            selectedCategoryId = null;
                        }
                    } else if (focusedField == 1 && capBuf.length() > 0) {
                        capBuf.deleteCharAt(capBuf.length() - 1);
                    }
                } else if (event.action() == KeyAction.ENTER) {
                    if (focusedField == 0) {
                        if (categoryBuf.toString().trim().isEmpty()) {
                            // Space + Enter or Enter on blank field: open SelectCategoryModal
                            List<com.bank.model.dto.UnbudgetedCategory> unbudgeted = budgetController.getUnbudgetedCategories(userEntity, targetMonth, targetYear);
                            SelectCategoryModal.CategorySelection chosen = SelectCategoryModal.show(terminal, origAttr, reader, unbudgeted, targetPeriod, width);
                            if (chosen != null) {
                                categoryBuf.setLength(0);
                                categoryBuf.append(chosen.categoryName());
                                selectedCategoryId = chosen.categoryId();
                                focusedField = 1; // jump straight to Monthly Cap ($) input
                            }
                            firstRender = true;
                        } else {
                            focusedField = 1;
                        }
                    } else if (focusedField == 1) {
                        focusedField = 2;
                    } else if (focusedField == 2) {
                        if (actionIdx == 0) {
                            SaveResult result = attemptSave(userEntity, selectedCategoryId, categoryBuf.toString(), capBuf.toString(),
                                    targetMonth, targetYear, budgetController);
                            if (result.isSuccess()) {
                                return result.getCategoryName();
                            }
                            isError = true;
                            statusMessage = result.getErrorMessage();
                        } else {
                            return null;
                        }
                    }
                } else if (event.action() == KeyAction.CHAR && event.ch() == ' ') {
                    if (focusedField == 0) {
                        // [Space] triggers the SELECT EXISTING CATEGORY modal table
                        List<com.bank.model.dto.UnbudgetedCategory> unbudgeted = budgetController.getUnbudgetedCategories(userEntity, targetMonth, targetYear);
                        SelectCategoryModal.CategorySelection chosen = SelectCategoryModal.show(terminal, origAttr, reader, unbudgeted, targetPeriod, width);
                        if (chosen != null) {
                            categoryBuf.setLength(0);
                            categoryBuf.append(chosen.categoryName());
                            selectedCategoryId = chosen.categoryId();
                            focusedField = 1; // jump straight to Monthly Cap ($) input
                        }
                        firstRender = true;
                    }
                } else if (event.action() == KeyAction.DIGIT || event.action() == KeyAction.CHAR) {
                    char c = event.ch();
                    if (focusedField == 2) {
                        if (c == '1') {
                            SaveResult result = attemptSave(userEntity, selectedCategoryId, categoryBuf.toString(), capBuf.toString(),
                                    targetMonth, targetYear, budgetController);
                            if (result.isSuccess()) {
                                return result.getCategoryName();
                            }
                            isError = true;
                            statusMessage = result.getErrorMessage();
                        } else if (c == '2') {
                            return null;
                        }
                    } else if (focusedField == 0) {
                        if (Character.isLetterOrDigit(c) || c == ' ' || c == '&' || c == '-' || c == '_') {
                            if (categoryBuf.length() < 35) {
                                categoryBuf.append(c);
                                selectedCategoryId = null; // Typing custom category name
                            }
                        }
                    } else if (focusedField == 1) {
                        if ((c >= '0' && c <= '9') || (c == '.' && !capBuf.toString().contains("."))) {
                            if (capBuf.length() < 12) {
                                capBuf.append(c);
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            logger.error("Error in ConfigureBudgetModal", e);
            return null;
        }
    }

    public static class SaveResult {
        private final boolean success;
        private final String categoryName;
        private final String errorMessage;

        public SaveResult(boolean success, String categoryName, String errorMessage) {
            this.success = success;
            this.categoryName = categoryName;
            this.errorMessage = errorMessage;
        }

        public boolean isSuccess() { return success; }
        public String getCategoryName() { return categoryName; }
        public String getErrorMessage() { return errorMessage; }
    }

    public static SaveResult attemptSave(User user, Long categoryId, String categoryName, String capStr,
                                         int month, int year, BudgetController budgetController) {
        String trimmedName = (categoryName != null) ? categoryName.trim() : "";
        if (categoryId == null && (trimmedName.length() < 3 || trimmedName.length() > 20)) {
            return new SaveResult(false, null, "Category name must be 3-20 characters");
        }

        String trimmedCap = (capStr != null) ? capStr.replace(",", "").replace("$", "").trim() : "";
        if (trimmedCap.isEmpty()) {
            return new SaveResult(false, null, "Monthly Cap must be at least $1.00");
        }

        BigDecimal monthlyCap;
        try {
            monthlyCap = new BigDecimal(trimmedCap).setScale(2, java.math.RoundingMode.HALF_UP);
        } catch (Exception e) {
            return new SaveResult(false, null, "Monthly Cap must be a valid number (e.g., 10 or 200.00)");
        }

        if (monthlyCap.compareTo(new BigDecimal("1.00")) < 0) {
            return new SaveResult(false, null, "Monthly Cap must be at least $1.00");
        }

        try {
            budgetController.configureBudget(user, categoryId, trimmedName, monthlyCap, month, year);
            return new SaveResult(true, trimmedName, null);
        } catch (Exception e) {
            logger.error("Failed to persist budget limit", e);
            return new SaveResult(false, null, "Failed to save budget: " + e.getMessage());
        }
    }

    public static SaveResult attemptSave(User user, String categoryName, String capStr,
                                         int month, int year, BudgetController budgetController) {
        return attemptSave(user, null, categoryName, capStr, month, year, budgetController);
    }

    public static String renderModalContent(String categoryName, String monthlyCap, String targetPeriod,
                                           int focusedField, int actionIdx, String statusMessage,
                                           boolean isError, int width) {
        StringBuilder sb = new StringBuilder();
        sb.append(TUIBox.top(width)).append("\n");
        sb.append(TUIBox.line(ConsoleTheme.primary(TITLE), width)).append("\n");
        sb.append(TUIBox.divider(width)).append("\n");

        sb.append(TUIBox.line(COMPARTMENT_HEADER, width)).append("\n");
        sb.append(TUIBox.emptyLine(width)).append("\n");

        // Field 0: Category Name
        String catVal = (categoryName.length() > 38) ? categoryName.substring(0, 38) : categoryName;
        String catInputBox = String.format("[ %-38s ]", catVal);
        if (focusedField == 0) {
            catInputBox = ConsoleTheme.highlight(catInputBox);
        }
        String rowCat = String.format("  Category Name   : %-58s", catInputBox);
        sb.append(TUIBox.line(rowCat, width)).append("\n");

        // Helper text beneath Category Name
        String helper = "                    (Type custom name or press [Space] to pick existing)";
        sb.append(TUIBox.line(ConsoleTheme.muted(helper), width)).append("\n");

        // Field 1: Monthly Cap ($)
        String capVal = (monthlyCap.length() > 38) ? monthlyCap.substring(0, 38) : monthlyCap;
        String capInputBox = String.format("[ %-38s ]", capVal);
        if (focusedField == 1) {
            capInputBox = ConsoleTheme.highlight(capInputBox);
        }
        String rowCap = String.format("  Monthly Cap ($) : %-58s", capInputBox);
        sb.append(TUIBox.line(rowCap, width)).append("\n");

        // Target Period
        String rowPeriod = String.format("  Target Period   : %-58s", targetPeriod);
        sb.append(TUIBox.line(rowPeriod, width)).append("\n");
        sb.append(TUIBox.emptyLine(width)).append("\n");

        sb.append(TUIBox.divider(width)).append("\n");
        sb.append(TUIBox.line("ACTION", width)).append("\n");

        // Action Buttons
        String btnSave = "[1] Save & Apply Budget";
        String btnCancel = "[2] Cancel & Return";
        String act1 = (focusedField == 2 && actionIdx == 0) ? "▸ " + ConsoleTheme.highlight(btnSave) : "  " + btnSave;
        String act2 = (focusedField == 2 && actionIdx == 1) ? "▸ " + ConsoleTheme.highlight(btnCancel) : "  " + btnCancel;
        String actionLine = String.format("  %-42s %-32s", act1, act2);
        sb.append(TUIBox.line(actionLine, width)).append("\n");

        sb.append(TUIBox.bottom(width)).append("\n");

        // Status or Error
        if (statusMessage != null) {
            String statusFormatted = isError ? ConsoleTheme.error(statusMessage) : ConsoleTheme.success(statusMessage);
            sb.append(" Status: ").append(statusFormatted).append("\n");
        }

        // Footer Hotkey Guide
        sb.append(ConsoleTheme.keyGuide(FOOTER)).append("\n");

        return sb.toString();
    }
}
