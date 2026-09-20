package com.bank.console.screens;

import com.bank.console.components.ScreenRenderer;
import com.bank.console.components.TUIBox;
import com.bank.console.theme.ConsoleTheme;
import com.bank.model.entity.Category;
import org.jline.terminal.Attributes;
import org.jline.terminal.Terminal;
import org.jline.utils.NonBlockingReader;

import java.util.List;

/**
 * Interactive 82-column modal selector for Expense Categories.
 */
public class CategorySelectorModal {

    public static final String TITLE = "DIGIBANK CORE > CATEGORIES > SELECT EXPENSE CATEGORY";
    public static final String COMPARTMENT_HEADER = "AVAILABLE EXPENSE CATEGORIES";
    public static final String TIP_TEXT = "Tip: Assigning a specific category ensures accurate monthly budget tracking.";
    public static final String FOOTER_FORMAT = " [↑/↓] Navigate  •  [Enter] Confirm  •  [%s] Hotkey  •  [Esc] Cancel";

    public static Category selectCategory(Terminal terminal, Attributes origAttr, NonBlockingReader reader,
                                          List<Category> categories, Category currentSelected, int width) {
        if (categories == null || categories.isEmpty()) {
            return null;
        }

        int selectedIdx = 0;
        if (currentSelected != null) {
            for (int i = 0; i < categories.size(); i++) {
                if (categories.get(i).getCategoryId().equals(currentSelected.getCategoryId())) {
                    selectedIdx = i;
                    break;
                }
            }
        }

        boolean first = true;
        try {
            while (true) {
                String modal = renderModalContent(categories, selectedIdx, width);
                ScreenRenderer.render(modal, first);
                first = false;

                int ch = reader.read();
                if (ch == 27) { // ESC or arrow
                    int next = reader.read(60);
                    if (next == -2 || next == -1) {
                        return currentSelected != null ? currentSelected : categories.get(0);
                    }
                    if (next == '[' || next == 'O') {
                        int code = reader.read();
                        if (code == 'A') { // Up
                            selectedIdx = (selectedIdx - 1 + categories.size()) % categories.size();
                        } else if (code == 'B') { // Down
                            selectedIdx = (selectedIdx + 1) % categories.size();
                        }
                    }
                } else if (ch == '\t') {
                    selectedIdx = (selectedIdx + 1) % categories.size();
                } else if (ch == '\r' || ch == '\n') {
                    return categories.get(selectedIdx);
                } else if (ch >= '1' && ch <= '9') {
                    int chosen = ch - '1';
                    if (chosen < categories.size()) {
                        return categories.get(chosen);
                    }
                }
            }
        } catch (Exception e) {
            return currentSelected != null ? currentSelected : categories.get(0);
        }
    }

    public static Category selectCategory(Terminal terminal, Attributes origAttr, NonBlockingReader reader,
                                          List<Category> categories, Category currentSelected) {
        return selectCategory(terminal, origAttr, reader, categories, currentSelected, 82);
    }

    public static String renderModalContent(List<Category> categories, int selectedIdx, int width) {
        StringBuilder sb = new StringBuilder();
        sb.append(TUIBox.top(width)).append("\n");
        sb.append(TUIBox.line(ConsoleTheme.primary(TITLE), width)).append("\n");
        sb.append(TUIBox.divider(width)).append("\n");
        sb.append(TUIBox.line(COMPARTMENT_HEADER, width)).append("\n");
        sb.append(TUIBox.emptyLine(width)).append("\n");

        for (int i = 0; i < categories.size(); i++) {
            Category c = categories.get(i);
            String isSystem = c.isSystem() ? "(System)" : "(Custom)";
            String row = String.format("[%d] %-24s %-10s %s",
                    i + 1, c.getName(), isSystem, c.getDescription() != null ? c.getDescription() : "");
            if (row.length() > width - 8) {
                row = row.substring(0, width - 8);
            }

            if (i == selectedIdx) {
                sb.append(TUIBox.line("  ▸ " + ConsoleTheme.highlight(row), width)).append("\n");
            } else {
                sb.append(TUIBox.line("    " + row, width)).append("\n");
            }
        }

        sb.append(TUIBox.emptyLine(width)).append("\n");
        sb.append(TUIBox.divider(width)).append("\n");
        sb.append(TUIBox.line(TIP_TEXT, width)).append("\n");
        sb.append(TUIBox.bottom(width)).append("\n");

        int maxHotkey = Math.min(categories.size(), 9);
        String hotkeyRange = maxHotkey > 1 ? "1-" + maxHotkey : "1";
        String footer = String.format(FOOTER_FORMAT, hotkeyRange);
        sb.append(ConsoleTheme.keyGuide(footer)).append("\n");

        return sb.toString();
    }
}
