package com.bank.console.components;

import com.bank.console.TUISession;
import com.bank.console.theme.ConsoleTheme;
import org.jline.terminal.Attributes;
import org.jline.terminal.Terminal;
import org.jline.utils.NonBlockingReader;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class ConsoleMenu {
    public static class MenuItem {
        private final String code;
        private final String title;
        private final String description;

        public MenuItem(String code, String title, String description) {
            this.code = code;
            this.title = title;
            this.description = description;
        }

        public String getCode() { return code; }
        public String getTitle() { return title; }
        public String getDescription() { return description; }
    }

    private final List<MenuItem> items = new ArrayList<>();
    private int selectedIndex = 0;
    private String headerSubtitle = null;
    private String screenTitle = "Navigation Menu";
    private String statusAlert = null;
    private boolean isErrorAlert = false;
    private String customContent = null;

    public ConsoleMenu setHeaderSubtitle(String subtitle) {
        this.headerSubtitle = subtitle;
        return this;
    }

    public ConsoleMenu setScreenTitle(String title) {
        this.screenTitle = title;
        return this;
    }

    public ConsoleMenu setAlert(String alert, boolean isError) {
        this.statusAlert = alert;
        this.isErrorAlert = isError;
        return this;
    }

    public ConsoleMenu setCustomContent(String customContent) {
        this.customContent = customContent;
        return this;
    }

    public ConsoleMenu addItem(String code, String title, String description) {
        items.add(new MenuItem(code, title, description));
        return this;
    }

    /**
     * Enters terminal raw mode, captures ↑/↓ arrows and Enter, then returns
     * the chosen MenuItem (or null if Esc was pressed).
     */
    public MenuItem select() {
        if (items.isEmpty()) return null;

        Terminal terminal = TUISession.getInstance().getTerminal();
        Attributes origAttributes = terminal.enterRawMode();
        NonBlockingReader reader = terminal.reader();

        boolean firstRender = true;
        try {
            while (true) {
                renderMenu(firstRender);
                firstRender = false;
                int ch = reader.read();

                if (ch == 27) { // Escape sequence or ESC key
                    int next = reader.read(100);
                    if (next == -2) {
                        // Bare ESC pressed -> return null (trigger Back)
                        return null;
                    } else if (next == '[' || next == 'O') {
                        int code = reader.read();
                        if (code == 'A') { // Up Arrow
                            selectedIndex = (selectedIndex - 1 + items.size()) % items.size();
                        } else if (code == 'B') { // Down Arrow
                            selectedIndex = (selectedIndex + 1) % items.size();
                        }
                    }
                } else if (ch == '\r' || ch == '\n') { // Enter key
                    return items.get(selectedIndex);
                } else if (ch >= '1' && ch <= '9') { // Instant number hotkey
                    int num = ch - '1';
                    if (num < items.size()) {
                        return items.get(num);
                    }
                } else if (ch == 'b' || ch == 'B') { // 'B' key for Back
                    return null;
                } else if (ch == 3) { // Ctrl+C
                    System.exit(0);
                }
            }
        } catch (IOException e) {
            return items.get(selectedIndex);
        } finally {
            terminal.setAttributes(origAttributes);
        }
    }

    private void renderMenu(boolean firstRender) {
        StringBuilder sb = new StringBuilder();
        sb.append(TUILayout.header(headerSubtitle));
        sb.append(TUILayout.screenTitle(screenTitle));

        if (statusAlert != null) {
            sb.append(TUILayout.alert(statusAlert, isErrorAlert));
            sb.append(TUIBox.emptyLine(TUILayout.APP_WIDTH)).append("\n");
        }

        if (customContent != null && !customContent.isEmpty()) {
            sb.append(customContent);
            if (!customContent.endsWith("\n")) {
                sb.append("\n");
            }
            sb.append(TUIBox.emptyLine(TUILayout.APP_WIDTH)).append("\n");
        }

        for (int i = 0; i < items.size(); i++) {
            MenuItem item = items.get(i);
            boolean isSelected = (i == selectedIndex);
            sb.append(TuiComponents.renderMenuItem(
                    item.getCode(), item.getTitle(), item.getDescription(), isSelected, TUILayout.APP_WIDTH)).append("\n");
        }

        sb.append(TUIBox.emptyLine(TUILayout.APP_WIDTH)).append("\n");
        sb.append(TUIBox.bottom(TUILayout.APP_WIDTH)).append("\n");
        String hotkeyRange = items.size() > 1 ? ("1-" + Math.min(items.size(), 9)) : "1";
        sb.append(ConsoleTheme.muted(String.format(" [↑/↓] Navigate  •  [Enter] Select  •  [%s] Hotkey  •  [Esc] Back", hotkeyRange))).append("\n");

        ScreenRenderer.render(sb.toString(), firstRender);
    }

    private String padRight(String s, int n) {
        return String.format("%-" + n + "s", s);
    }
}