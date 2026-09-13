package com.bank.console.screens;

import com.bank.console.ControllerFactory;
import com.bank.console.ScreenNavigator;
import com.bank.console.TUISession;
import com.bank.console.components.ConsolePrompt;
import com.bank.console.components.ScreenRenderer;
import com.bank.console.components.TUIBox;
import com.bank.console.components.TUILayout;
import com.bank.console.theme.ConsoleTheme;
import com.bank.controller.CategoryController;
import com.bank.model.dto.UserDTO;
import com.bank.model.entity.Category;
import com.bank.model.entity.User;
import com.bank.security.SessionManager;
import org.jline.terminal.Attributes;
import org.jline.terminal.Terminal;
import org.jline.utils.NonBlockingReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.List;

/**
 * DEDICATED SCREEN: CATEGORY MANAGEMENT (82 Columns)
 * Displays system and custom transaction categories with direct keyboard navigation.
 */
public class CategoryManagementScreen implements Screen {
    private static final Logger logger = LoggerFactory.getLogger(CategoryManagementScreen.class);

    private final CategoryController categoryController;
    private String statusMessage;
    private boolean isErrorStatus;

    public CategoryManagementScreen() {
        this(ControllerFactory.getCategoryController());
    }

    public CategoryManagementScreen(CategoryController categoryController) {
        this.categoryController = categoryController;
        this.statusMessage = null;
        this.isErrorStatus = false;
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
        Terminal terminal = session.getTerminal();
        Attributes origAttributes = terminal.enterRawMode();
        NonBlockingReader reader = terminal.reader();

        int selectedIndex = 0;
        boolean firstRender = true;
        boolean needsReload = true;
        List<Category> categories = null;

        try {
            while (true) {
                if (needsReload) {
                    categories = categoryController.getVisibleCategories(userEntity);
                    needsReload = false;
                }

                StringBuilder sb = new StringBuilder();
                sb.append(TUIBox.top(width)).append("\n");
                sb.append(TUIBox.line(ConsoleTheme.primary("DIGIBANK CORE > CATEGORY MANAGEMENT"), width)).append("\n");
                sb.append(TUIBox.divider(width)).append("\n");
                sb.append(TUIBox.emptyLine(width)).append("\n");
                sb.append(TUIBox.line("  All categories available for transaction tagging and monthly budgets:", width)).append("\n");
                sb.append(TUIBox.emptyLine(width)).append("\n");

                // Table Header
                String th = String.format("  %-4s  %-20s  %-10s  %-36s", "ID", "CATEGORY NAME", "TYPE", "DESCRIPTION");
                sb.append(TUIBox.line(ConsoleTheme.bold(th), width)).append("\n");
                sb.append(TUIBox.line("  " + "─".repeat(76), width)).append("\n");

                if (categories == null || categories.isEmpty()) {
                    sb.append(TUIBox.line(ConsoleTheme.muted("  No categories found."), width)).append("\n");
                } else {
                    for (Category c : categories) {
                        String typeBadge = c.isSystem() ? ConsoleTheme.info("[SYSTEM]") : ConsoleTheme.success("[CUSTOM]");
                        String rawType = c.isSystem() ? "[SYSTEM]" : "[CUSTOM]";
                        String name = c.getName() != null ? c.getName() : "";
                        if (name.length() > 20) name = name.substring(0, 17) + "...";
                        String desc = c.getDescription() != null ? c.getDescription() : "";
                        if (desc.length() > 36) desc = desc.substring(0, 33) + "...";

                        String idStr = String.format("%-4d", c.getCategoryId() != null ? c.getCategoryId() : 0);
                        String nameStr = String.format("%-20s", name);
                        String descStr = String.format("%-36s", desc);
                        int typePad = Math.max(0, 10 - rawType.length());

                        String row = "  " + idStr + "  " + nameStr + "  " + typeBadge + " ".repeat(typePad) + "  " + descStr;
                        sb.append(TUIBox.line(row, width)).append("\n");
                    }
                }

                sb.append(TUIBox.emptyLine(width)).append("\n");
                sb.append(TUIBox.divider(width)).append("\n");

                String b0 = selectedIndex == 0 ? "  ► " + ConsoleTheme.highlight("[1] Create Custom Category") : "    " + "[1] Create Custom Category";
                String b1 = selectedIndex == 1 ? "► " + ConsoleTheme.highlight("[0] Return to Main Menu") : "  " + ConsoleTheme.muted("[0] Return to Main Menu");
                sb.append(TUIBox.line(b0 + "        " + b1, width)).append("\n");
                sb.append(TUIBox.bottom(width)).append("\n");

                if (statusMessage != null) {
                    String statusDisplay = isErrorStatus ? ConsoleTheme.error(statusMessage) : ConsoleTheme.success(statusMessage);
                    sb.append(" Status: ").append(statusDisplay).append("\n");
                }
                sb.append(ConsoleTheme.muted("  [↑/↓] Navigate  •  [Enter] Select  •  [1/0] Quick Select  •  [Esc] Back")).append("\n");

                ScreenRenderer.render(sb.toString(), firstRender);
                firstRender = false;

                int ch = reader.read();

                if (ch == 27) { // ESC or Escape sequence
                    int next = reader.read(60);
                    if (next == -2 || next == -1) {
                        navigator.pop();
                        return;
                    }
                    if (next == '[' || next == 'O') {
                        int code = reader.read();
                        if (code == 'A' || code == 'D') { // Up / Left
                            selectedIndex = (selectedIndex - 1 + 2) % 2;
                        } else if (code == 'B' || code == 'C') { // Down / Right
                            selectedIndex = (selectedIndex + 1) % 2;
                        }
                    }
                } else if (ch == '\t') {
                    selectedIndex = (selectedIndex + 1) % 2;
                } else if (ch == '\r' || ch == '\n') {
                    if (selectedIndex == 0) {
                        terminal.setAttributes(origAttributes);
                        handleCreateCategory(userEntity, width);
                        origAttributes = terminal.enterRawMode();
                        needsReload = true;
                        firstRender = true;
                    } else {
                        navigator.pop();
                        return;
                    }
                } else if (ch == '1') {
                    terminal.setAttributes(origAttributes);
                    handleCreateCategory(userEntity, width);
                    origAttributes = terminal.enterRawMode();
                    needsReload = true;
                    firstRender = true;
                } else if (ch == 'r' || ch == 'R') {
                    needsReload = true;
                } else if (ch == '0' || ch == 'b' || ch == 'B') {
                    navigator.pop();
                    return;
                } else if (ch == 3) { // Ctrl+C
                    session.clearScreen();
                    System.exit(0);
                }
            }
        } catch (IOException e) {
            logger.error("Error in category management loop", e);
        } finally {
            terminal.setAttributes(origAttributes);
        }
    }

    private void handleCreateCategory(User user, int width) {
        System.out.println();
        String name = ConsolePrompt.promptLine("Category Name (or '0' to cancel)");
        if (name.isEmpty() || "0".equals(name)) {
            return;
        }

        String desc = ConsolePrompt.promptLine("Category Description");
        try {
            Category created = categoryController.createCustomCategory(name, desc, user);
            this.statusMessage = "Created category #" + created.getCategoryId() + " (" + created.getName() + ") successfully.";
            this.isErrorStatus = false;
        } catch (Exception e) {
            logger.error("Failed to create category", e);
            this.statusMessage = "Failed to create category: " + e.getMessage();
            this.isErrorStatus = true;
        }
    }
}
