package com.bank.console.screens;

import com.bank.console.components.ScreenRenderer;
import com.bank.console.components.TUIBox;
import com.bank.console.components.TUIFormHelper;
import com.bank.console.components.TUIFormHelper.KeyAction;
import com.bank.console.components.TUIFormHelper.KeyEvent;
import com.bank.console.theme.ConsoleTheme;
import com.bank.model.dto.UnbudgetedCategory;
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
import java.util.List;

/**
 * 82-column enclosed modal table for picking an existing unbudgeted category:
 * - Numbered table with Category Name, Classification, and MTD Actual Spend.
 * - [↑/↓] / [W/S] arrow navigation and [Enter] selection.
 * - [1-N] quick digit selection.
 * - [Esc] to cancel and return to previous form.
 */
public class SelectCategoryModal {
    private static final Logger logger = LoggerFactory.getLogger(SelectCategoryModal.class);

    public static final String TITLE = "DIGIBANK CORE > FINANCIAL PLANNING > SELECT EXISTING CATEGORY";

    public record CategorySelection(Long categoryId, String categoryName) {}

    public static CategorySelection show(Terminal terminal, Attributes origAttr, NonBlockingReader reader,
                                         List<UnbudgetedCategory> categories, LocalDate targetPeriod, int width) {
        int selectedIndex = 0;
        boolean firstRender = true;
        String monthDisplay = targetPeriod.format(DateTimeFormatter.ofPattern("MMMM yyyy")).toUpperCase();

        try {
            while (true) {
                String content = renderContent(categories, selectedIndex, monthDisplay, width);
                ScreenRenderer.render(content, firstRender);
                firstRender = false;

                KeyEvent event = TUIFormHelper.readKey(reader);
                if (event.action() == KeyAction.ESCAPE) {
                    return null;
                } else if (event.action() == KeyAction.UP || (event.action() == KeyAction.CHAR && (event.ch() == 'w' || event.ch() == 'W'))) {
                    if (categories != null && !categories.isEmpty()) {
                        selectedIndex = Math.max(0, selectedIndex - 1);
                    }
                } else if (event.action() == KeyAction.DOWN || (event.action() == KeyAction.CHAR && (event.ch() == 's' || event.ch() == 'S'))) {
                    if (categories != null && !categories.isEmpty()) {
                        selectedIndex = Math.min(categories.size() - 1, selectedIndex + 1);
                    }
                } else if (event.action() == KeyAction.ENTER) {
                    if (categories != null && !categories.isEmpty() && selectedIndex < categories.size()) {
                        UnbudgetedCategory chosen = categories.get(selectedIndex);
                        return new CategorySelection(chosen.getCategoryId(), chosen.getName());
                    }
                    return null;
                } else if (event.action() == KeyAction.DIGIT || event.action() == KeyAction.CHAR) {
                    char c = event.ch();
                    if (c >= '1' && c <= '9') {
                        int rowIdx = (c - '1');
                        if (categories != null && rowIdx < categories.size()) {
                            UnbudgetedCategory chosen = categories.get(rowIdx);
                            return new CategorySelection(chosen.getCategoryId(), chosen.getName());
                        }
                    }
                }
            }
        } catch (IOException e) {
            logger.error("Error reading key in SelectCategoryModal", e);
            return null;
        }
    }

    public static String renderContent(List<UnbudgetedCategory> categories, int selectedIndex, String monthDisplay, int width) {
        StringBuilder sb = new StringBuilder();
        DecimalFormat df = new DecimalFormat("#,##0.00");

        sb.append(TUIBox.top(width)).append("\n");
        sb.append(TUIBox.line(ConsoleTheme.primary(TITLE), width)).append("\n");
        sb.append(TUIBox.divider(width)).append("\n");

        sb.append(TUIBox.line("UNBUDGETED CATEGORIES (" + monthDisplay + ")", width)).append("\n");
        sb.append(TUIBox.emptyLine(width)).append("\n");

        // Table Header: exactly 78 characters inside box (8 + 22 + 18 + 30 = 78)
        String colHdr = "    #   CATEGORY NAME         CLASSIFICATION    MTD ACTUAL SPEND             ";
        sb.append(TUIBox.line(colHdr, width)).append("\n");

        // Separator: 2 spaces + 74 '─' + 2 spaces = 78 chars
        sb.append(TUIBox.line("  " + "─".repeat(74) + "  ", width)).append("\n");

        if (categories == null || categories.isEmpty()) {
            sb.append(TUIBox.line("  No unbudgeted categories found for this period.", width)).append("\n");
            sb.append(TUIBox.emptyLine(width)).append("\n");
            sb.append(TUIBox.emptyLine(width)).append("\n");
        } else {
            for (int i = 0; i < categories.size(); i++) {
                UnbudgetedCategory cat = categories.get(i);
                boolean isSelected = (i == selectedIndex);

                String prefix = isSelected ? "  ▸" : "   ";
                String numSlot = String.format("[%d]  ", i + 1);
                String numCol = prefix + numSlot; // 8 chars

                String catName = cat.getName() != null ? cat.getName() : "";
                if (catName.length() > 20) catName = catName.substring(0, 20);
                String catCol = String.format("%-22s", catName); // 22 chars

                String classification = cat.getClassification() != null ? cat.getClassification() : "EXPENSE";
                if (classification.length() > 16) classification = classification.substring(0, 16);
                String classCol = String.format("%-18s", classification); // 18 chars

                BigDecimal spent = cat.getTotalSpent() != null ? cat.getTotalSpent() : BigDecimal.ZERO;
                String spendFormatted = String.format("$ %10s", df.format(spent));
                String spendCol = String.format("%-30s", spendFormatted); // 30 chars

                String rowText;
                if (isSelected) {
                    rowText = ConsoleTheme.inlineHighlight(numCol + catCol) + classCol + spendCol;
                } else {
                    rowText = numCol + catCol + classCol + spendCol;
                }

                sb.append(TUIBox.line(rowText, width)).append("\n");
            }

            // Fill blank lines if fewer than 4 items for aesthetic container balance
            for (int i = categories.size(); i < 4; i++) {
                sb.append(TUIBox.emptyLine(width)).append("\n");
            }
        }

        sb.append(TUIBox.emptyLine(width)).append("\n");
        sb.append(TUIBox.divider(width)).append("\n");
        sb.append(TUIBox.line("ACTION", width)).append("\n");

        int count = (categories != null) ? categories.size() : 0;
        String chooseLabel = count > 0 ? String.format("[Enter/1-%d] Choose Category", Math.min(count, 9)) : "[Enter] Choose Category";
        String actionLine = String.format("  %-48s %-26s", chooseLabel, "[Esc] Back to Form");
        sb.append(TUIBox.line(actionLine, width)).append("\n");

        sb.append(TUIBox.bottom(width)).append("\n");

        // Footer
        String quickPickLabel = count > 0 ? String.format("[1-%d] Quick Pick", Math.min(count, 9)) : "[1-N] Quick Pick";
        String footer = " [↑/↓] Navigate  •  [Enter] Select  •  " + quickPickLabel + "  •  [Esc] Cancel";
        sb.append(ConsoleTheme.keyGuide(footer)).append("\n");

        return sb.toString();
    }
}
