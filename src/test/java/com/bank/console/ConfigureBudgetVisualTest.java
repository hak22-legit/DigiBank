package com.bank.console;

import com.bank.console.components.TUIBox;
import com.bank.console.components.TUILayout;
import com.bank.console.screens.ConfigureBudgetModal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class ConfigureBudgetVisualTest {

    @Test
    @DisplayName("Verify ConfigureBudgetModal lines strictly adhere to 82 columns across all fields and states")
    void testConfigureBudgetModal82Columns() {
        int width = TUILayout.APP_WIDTH;
        assertEquals(82, width);

        // State 1: Focused on Category Name (Field 0)
        String modal0 = ConfigureBudgetModal.renderModalContent(
                "Food", "200.00", "SEPTEMBER 2026",
                0, 0, null, false, width
        );

        String[] lines0 = modal0.split("\n");
        for (String line : lines0) {
            if (line.startsWith("│") || line.startsWith("\033[38;5;238m│") || line.contains("│")) {
                assertEquals(82, TUIBox.visibleLength(line), "Line must be exactly 82 columns: " + line);
            }
        }

        // State 2: Focused on Monthly Cap (Field 1) with Error status
        String modal1 = ConfigureBudgetModal.renderModalContent(
                "Entertainment & Games", "350.50", "SEPTEMBER 2026",
                1, 0, "Invalid monthly cap limit.", true, width
        );

        String[] lines1 = modal1.split("\n");
        for (String line : lines1) {
            if (line.startsWith("│") || line.startsWith("\033[38;5;238m│") || line.contains("│")) {
                assertEquals(82, TUIBox.visibleLength(line), "Line must be exactly 82 columns: " + line);
            }
        }

        // State 3: Focused on Action 1 (Cancel & Return) (Field 2)
        String modal2 = ConfigureBudgetModal.renderModalContent(
                "Travel", "1200.00", "OCTOBER 2026",
                2, 1, "Budget limit applied successfully.", false, width
        );

        String[] lines2 = modal2.split("\n");
        for (String line : lines2) {
            if (line.startsWith("│") || line.startsWith("\033[38;5;238m│") || line.contains("│")) {
                assertEquals(82, TUIBox.visibleLength(line), "Line must be exactly 82 columns: " + line);
            }
        }
    }

    @Test
    @DisplayName("Verify modal content contains required wireframe elements")
    void testModalContentWireframeElements() {
        String modal = ConfigureBudgetModal.renderModalContent(
                "Food", "200.00", "SEPTEMBER 2026",
                0, 0, null, false, 82
        );

        assertTrue(modal.contains("DIGIBANK CORE > FINANCIAL PLANNING > CONFIGURE BUDGET"));
        assertTrue(modal.contains("ASSIGN BUDGETARY LIMIT"));
        assertTrue(modal.contains("Category Name"));
        assertTrue(modal.contains("(Type custom name or press [Space] to pick existing)"));
        assertTrue(modal.contains("Monthly Cap ($)"));
        assertTrue(modal.contains("Target Period"));
        assertTrue(modal.contains("SEPTEMBER 2026"));
        assertTrue(modal.contains("[1] Save & Apply Budget"));
        assertTrue(modal.contains("[2] Cancel & Return"));
        assertTrue(modal.contains("[Space] Pick Existing"));
    }

    @Test
    @DisplayName("Verify SelectCategoryModal lines strictly adhere to 82 columns and match wireframe")
    void testSelectCategoryModal82Columns() {
        int width = TUILayout.APP_WIDTH;
        assertEquals(82, width);

        java.util.List<com.bank.model.dto.UnbudgetedCategory> categories = java.util.List.of(
                new com.bank.model.dto.UnbudgetedCategory(1L, "Food", "EXPENSE", new java.math.BigDecimal("10000.00")),
                new com.bank.model.dto.UnbudgetedCategory(2L, "Groceries", "EXPENSE", java.math.BigDecimal.ZERO),
                new com.bank.model.dto.UnbudgetedCategory(3L, "Transport", "EXPENSE", new java.math.BigDecimal("45.50")),
                new com.bank.model.dto.UnbudgetedCategory(4L, "Utilities", "EXPENSE", new java.math.BigDecimal("120.00"))
        );

        String rendered = com.bank.console.screens.SelectCategoryModal.renderContent(
                categories, 0, "SEPTEMBER 2026", width
        );

        String[] lines = rendered.split("\n");
        for (String line : lines) {
            if (line.startsWith("│") || line.startsWith("\033[38;5;238m│") || line.contains("│")) {
                assertEquals(82, TUIBox.visibleLength(line), "Line must be exactly 82 columns: " + line);
            }
        }

        assertTrue(rendered.contains("DIGIBANK CORE > FINANCIAL PLANNING > SELECT EXISTING CATEGORY"));
        assertTrue(rendered.contains("UNBUDGETED CATEGORIES (SEPTEMBER 2026)"));
        assertTrue(rendered.contains("Food"));
        assertTrue(rendered.contains("Groceries"));
        assertTrue(rendered.contains("Transport"));
        assertTrue(rendered.contains("Utilities"));
        assertTrue(rendered.contains("[Enter/1-4] Choose Category"));
        assertTrue(rendered.contains("[Esc] Back to Form"));
        assertTrue(rendered.contains("[1-4] Quick Pick"));
    }

    @Test
    @DisplayName("Verify validation logic: trim, min $1.00, integer parsing, and field-specific error messages")
    void testValidationLogic() {
        // 1. Trim check: "  Food  " is trimmed to "Food"
        ConfigureBudgetModal.SaveResult r1 = ConfigureBudgetModal.attemptSave(
                null, "  Food  ", "200.00", 9, 2026, null
        );
        assertNotEquals("Category name must be 3-20 characters", r1.getErrorMessage());

        // 2. Short category name (< 3 chars)
        ConfigureBudgetModal.SaveResult rShort = ConfigureBudgetModal.attemptSave(
                null, "AB", "200.00", 9, 2026, null
        );
        assertFalse(rShort.isSuccess());
        assertEquals("Category name must be 3-20 characters", rShort.getErrorMessage());

        // 3. Long category name (> 20 chars)
        ConfigureBudgetModal.SaveResult rLong = ConfigureBudgetModal.attemptSave(
                null, "ThisIsAVeryLongCategoryNameThatExceeds20", "200.00", 9, 2026, null
        );
        assertFalse(rLong.isSuccess());
        assertEquals("Category name must be 3-20 characters", rLong.getErrorMessage());

        // 4. Monthly Cap < 1.00 (e.g. 0.50)
        ConfigureBudgetModal.SaveResult rMin = ConfigureBudgetModal.attemptSave(
                null, "Groceries", "0.50", 9, 2026, null
        );
        assertFalse(rMin.isSuccess());
        assertEquals("Monthly Cap must be at least $1.00", rMin.getErrorMessage());

        // 5. Integer input for Monthly Cap (e.g. "10", "1")
        ConfigureBudgetModal.SaveResult rInt = ConfigureBudgetModal.attemptSave(
                null, "Groceries", "10", 9, 2026, null
        );
        assertNotEquals("Monthly Cap must be at least $1.00", rInt.getErrorMessage());
        assertNotEquals("Monthly Cap must be a valid number (e.g., 10 or 200.00)", rInt.getErrorMessage());

        // 6. Non-numeric Monthly Cap
        ConfigureBudgetModal.SaveResult rNonNum = ConfigureBudgetModal.attemptSave(
                null, "Groceries", "abc", 9, 2026, null
        );
        assertFalse(rNonNum.isSuccess());
        assertEquals("Monthly Cap must be a valid number (e.g., 10 or 200.00)", rNonNum.getErrorMessage());

        // 7. Selected category from picker (known categoryId) does not fail name length check even if name is short or empty
        ConfigureBudgetModal.SaveResult rPicked = ConfigureBudgetModal.attemptSave(
                null, 5L, "", "150.00", 9, 2026, null
        );
        assertNotEquals("Category name must be 3-20 characters", rPicked.getErrorMessage());
    }
}
