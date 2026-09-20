package com.bank.console.screens;

import com.bank.console.ScreenNavigator;
import com.bank.console.TUISession;
import com.bank.console.components.ScreenRenderer;
import com.bank.console.components.TUIBox;
import com.bank.console.components.TUIFormHelper;
import com.bank.console.components.TUIFormHelper.KeyAction;
import com.bank.console.components.TUIFormHelper.KeyEvent;
import com.bank.console.components.TUILayout;
import com.bank.console.theme.ConsoleTheme;
import org.jline.terminal.Attributes;
import org.jline.terminal.Terminal;
import org.jline.utils.NonBlockingReader;

import java.io.IOException;

/**
 * SCREEN 1: WELCOME & SYSTEM GATEWAY (82 Columns)
 */
public class WelcomeScreen implements Screen {

    private static final String[] MENU_ITEMS = {
            "Sign In",
            "Open Bank Account",
            "Exit System"
    };

    private static final String[] LOGO_LINES = {
            "██████╗ ██╗ ██████╗ ██╗██████╗  █████╗ ███╗   ██╗██╗  ██╗",
            "██╔══██╗██║██╔════╝ ██║██╔══██╗██╔══██╗████╗  ██║██║ ██╔╝",
            "██║  ██║██║██║  ███╗██║██████╔╝███████║██╔██╗ ██║█████╔╝ ",
            "██║  ██║██║██║   ██║██║██╔══██╗██╔══██║██║╚██╗██║██╔═██╗ ",
            "██████╔╝██║╚██████╔╝██║██████╔╝██║  ██║██║ ╚████║██║  ██╗",
            "╚═════╝ ╚═╝ ╚═════╝ ╚═╝╚═════╝ ╚═╝  ╚═╝╚═╝  ╚═══╝╚═╝  ╚═╝"
    };

    @Override
    public void render(ScreenNavigator navigator, TUISession session) {
        int selectedIndex = 0;
        Terminal terminal = session.getTerminal();
        Attributes origAttributes = terminal.enterRawMode();
        NonBlockingReader reader = terminal.reader();

        boolean firstRender = true;
        try {
            while (true) {
                renderScreen(session, selectedIndex, firstRender);
                firstRender = false;

                KeyEvent event = TUIFormHelper.readKey(reader);
                if (event.action() == KeyAction.ESCAPE) {
                    executeChoice(2, navigator, session, terminal, origAttributes);
                    return;
                } else if (event.action() == KeyAction.UP || (event.action() == KeyAction.CHAR && (event.ch() == 'k' || event.ch() == 'K'))) {
                    selectedIndex = (selectedIndex - 1 + MENU_ITEMS.length) % MENU_ITEMS.length;
                } else if (event.action() == KeyAction.DOWN || event.action() == KeyAction.TAB || (event.action() == KeyAction.CHAR && (event.ch() == 'j' || event.ch() == 'J'))) {
                    selectedIndex = (selectedIndex + 1) % MENU_ITEMS.length;
                } else if (event.action() == KeyAction.SHIFT_TAB) {
                    selectedIndex = (selectedIndex - 1 + MENU_ITEMS.length) % MENU_ITEMS.length;
                } else if (event.action() == KeyAction.ENTER) {
                    executeChoice(selectedIndex, navigator, session, terminal, origAttributes);
                    return;
                } else if (event.action() == KeyAction.DIGIT && event.ch() >= '1' && event.ch() <= '3') {
                    int num = event.ch() - '1';
                    executeChoice(num, navigator, session, terminal, origAttributes);
                    return;
                }
            }
        } catch (IOException e) {
            executeChoice(selectedIndex, navigator, session, terminal, origAttributes);
        } finally {
            terminal.setAttributes(origAttributes);
        }
    }

    private void renderScreen(TUISession session, int selectedIndex, boolean firstRender) {
        String content = renderContent(selectedIndex, TUILayout.APP_WIDTH);
        ScreenRenderer.render(content, firstRender);
    }

    private static String renderPortalOption(int key, String label, boolean isSelected, int width) {
        String prefix = isSelected ? "▸ " : "  ";
        String content = String.format("%s[%d] %s", prefix, key, label);
        // Pad to exactly 74 characters printable width
        content = String.format("%-74s", content);

        if (isSelected) {
            return TUIBox.line(" \033[7m" + content + "\033[0m", width);
        } else {
            return TUIBox.line(" " + content, width);
        }
    }

    public static String renderContent(int selectedIndex, int width) {
        StringBuilder sb = new StringBuilder();

        // Top Box Border
        sb.append(TUIBox.top(width)).append("\n");

        // Header: Title and Build
        String left = ConsoleTheme.primary("DIGIBANK CORE > SYSTEM GATEWAY");
        String right = ConsoleTheme.muted("[BUILD: v1.4.2]");
        int leftLen = TUIBox.stripAnsi(left).length();
        int rightLen = TUIBox.stripAnsi(right).length();
        int space = width - 3 - leftLen - rightLen;
        sb.append(ConsoleTheme.border(String.valueOf(TUIBox.V)))
                .append(" ")
                .append(left)
                .append(" ".repeat(Math.max(1, space)))
                .append(right)
                .append(ConsoleTheme.border(String.valueOf(TUIBox.V)))
                .append("\n");

        // Divider
        sb.append(TUIBox.divider(width)).append("\n");
        sb.append(TUIBox.emptyLine(width)).append("\n");

        // Centered ASCII Logo (with 5 spaces inside margin for 7 total leading spaces)
        for (String line : LOGO_LINES) {
            sb.append(TUIBox.line("     " + ConsoleTheme.logo(line), width)).append("\n");
        }

        sb.append(TUIBox.emptyLine(width)).append("\n");
        sb.append(TUIBox.center(ConsoleTheme.bold("ENTERPRISE CORE BANKING & LEDGER ENGINE"), width)).append("\n");
        sb.append(TUIBox.center(ConsoleTheme.muted("High-Performance  •  ACID-Compliant  •  Zero-Trust"), width)).append("\n");
        sb.append(TUIBox.emptyLine(width)).append("\n");
        sb.append(TUIBox.divider(width)).append("\n");

        // Section: PORTAL SELECTION
        sb.append(TUIBox.line(ConsoleTheme.primary("PORTAL SELECTION"), width)).append("\n");
        sb.append(TUIBox.emptyLine(width)).append("\n");

        // Menu items with 74-character uniform highlight row
        for (int i = 0; i < MENU_ITEMS.length; i++) {
            sb.append(renderPortalOption(i + 1, MENU_ITEMS[i], i == selectedIndex, width)).append("\n");
        }

        sb.append(TUIBox.emptyLine(width)).append("\n");
        sb.append(TUIBox.divider(width)).append("\n");
        sb.append(TUIBox.line("Status: System gateway ready. Select an option or press [1-3] to begin.", width)).append("\n");
        sb.append(TUIBox.bottom(width)).append("\n");

        // Outer footer hint
        sb.append(ConsoleTheme.keyGuide("[↑/↓] Navigate  •  [Enter] Confirm  •  [1-3] Quick Jump  •  [Esc] Exit")).append("\n");

        return sb.toString();
    }

    private void executeChoice(int index, ScreenNavigator navigator, TUISession session,
                               Terminal terminal, Attributes origAttributes) {
        terminal.setAttributes(origAttributes);
        switch (index) {
            case 0 -> navigator.push(new LoginScreen());
            case 1 -> navigator.push(new RegisterScreen());
            case 2 -> {
                session.clearScreen();
                System.out.println(ConsoleTheme.muted("Thank you for choosing DigiBank. Goodbye!"));
                System.exit(0);
            }
        }
    }
}
