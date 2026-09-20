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
 * Supports quick select [1-8], arrow navigation, and screen clearing to prevent stacking bleed.
 */
public class CategorySelectModal {

    public static final String DEFAULT_TITLE = "DIGIBANK CORE > FINANCIAL PLANNING > SELECT EXPENSE CATEGORY";
    public static final String COMPARTMENT_HEADER = "AVAILABLE EXPENSE CATEGORIES";
    public static final String FOOTER = " [↑/↓] Navigate  •  [Enter] Confirm  •  [1-8] Quick Select  •  [Esc] Cancel";

    public static Category selectCategory(Terminal terminal, Attributes origAttr, NonBlockingReader reader,
                                          List<Category> categories, Category currentSelected, int width) {
        return selectCategory(terminal, origAttr, reader, categories, currentSelected, width, DEFAULT_TITLE);
    }

    public static Category selectCategory(Terminal terminal, Attributes origAttr, NonBlockingReader reader,
                                          List<Category> categories, Category currentSelected, int width, String title) {
        if (categories == null || categories.isEmpty()) {
            return null;
        }

        int selectedIdx = 0;
        if (currentSelected != null) {
            for (int i = 0; i < categories.size(); i++) {
                if (categories.get(i).getCategoryId() != null
                        && categories.get(i).getCategoryId().equals(currentSelected.getCategoryId())) {
                    selectedIdx = i;
                    break;
                }
            }
        }

        boolean first = true;
        try {
            while (true) {
                String modal = renderModalContent(categories, selectedIdx, width, title);
                ScreenRenderer.render(modal, first);
                first = false;

                int ch = reader.read();
                if (ch == -1) {
                    break;
                }

                if (ch == 27) { // ESC or arrow sequence
                    int next1 = reader.read(25);
                    if (next1 == -2 || next1 == -1 || next1 == 'b' || next1 == 'B') {
                        return null;
                    }
                    if (next1 == '[' || next1 == 'O') {
                        int code = reader.read(25);
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
                } else {
                    char upper = Character.toUpperCase((char) ch);
                    if (upper == 'K') {
                        selectedIdx = (selectedIdx - 1 + categories.size()) % categories.size();
                    } else if (upper == 'J') {
                        selectedIdx = (selectedIdx + 1) % categories.size();
                    }
                }
            }
        } catch (Exception e) {
            return null;
        }

        return null;
    }

    public static Category selectCategory(Terminal terminal, Attributes origAttr, NonBlockingReader reader,
                                          List<Category> categories, Category currentSelected) {
        return selectCategory(terminal, origAttr, reader, categories, currentSelected, 82, DEFAULT_TITLE);
    }

    public static String renderModalContent(List<Category> categories, int selectedIdx, int width) {
        return renderModalContent(categories, selectedIdx, width, DEFAULT_TITLE);
    }

    public static String renderModalContent(List<Category> categories, int selectedIdx, int width, String title) {
        StringBuilder sb = new StringBuilder();
        sb.append(TUIBox.top(width)).append("\n");
        sb.append(TUIBox.line(ConsoleTheme.primary(title), width)).append("\n");
        sb.append(TUIBox.divider(width)).append("\n");
        sb.append(TUIBox.line(COMPARTMENT_HEADER, width)).append("\n");
        sb.append(TUIBox.emptyLine(width)).append("\n");

        for (int i = 0; i < categories.size(); i++) {
            Category c = categories.get(i);
            String isSystem = c.isSystem() ? "(System)" : "(Custom)";
            String desc = c.getDescription() != null ? c.getDescription() : "";
            String row = String.format("[%d] %-15s %-10s %s", i + 1, c.getName(), isSystem, desc);
            if (row.length() > 74) {
                row = row.substring(0, 74);
            }

            if (i == selectedIdx) {
                sb.append(TUIBox.line("  ▸ " + ConsoleTheme.highlight(row), width)).append("\n");
            } else {
                sb.append(TUIBox.line("    " + row, width)).append("\n");
            }
        }

        sb.append(TUIBox.emptyLine(width)).append("\n");
        sb.append(TUIBox.divider(width)).append("\n");
        sb.append(TUIBox.line("Status: Use [↑/↓] to navigate or press [1-8] for instant selection.", width)).append("\n");
        sb.append(TUIBox.bottom(width)).append("\n");

        sb.append(ConsoleTheme.keyGuide(FOOTER)).append("\n");

        return sb.toString();
    }
}
