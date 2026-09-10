package com.bank.console.screens;

import com.bank.console.ScreenNavigator;
import com.bank.console.TUISession;
import com.bank.console.components.TUIBox;
import com.bank.console.components.TUILayout;
import com.bank.console.theme.ConsoleTheme;
import org.jline.terminal.Attributes;
import org.jline.terminal.Terminal;
import org.jline.utils.NonBlockingReader;

import java.io.IOException;

/**
 * SCREEN 1: WELCOME & LANDING GATEWAY (82 Columns)
 */
public class WelcomeScreen implements Screen {

    private static final String[] MENU_ITEMS = {
            "[1] Sign In (Customer & Staff)",
            "[2] Open New Account (Register)",
            "[3] Forgot Password (OTP Recovery)",
            "[4] Currency Exchange Rates & Calculator",
            "[5] Exit DigiBank"
    };

    private static final String[] LOGO_LINES = {
            "  ██████╗ ██╗ ██████╗ ██╗██████╗  █████╗ ███╗   ██╗██╗  ██╗",
            "  ██╔══██╗██║██╔════╝ ██║██╔══██╗██╔══██╗████╗  ██║██║ ██╔╝",
            "  ██║  ██║██║██║  ███╗██║██████╔╝███████║██╔██╗ ██║█████╔╝ ",
            "  ██║  ██║██║██║   ██║██║██╔══██╗██╔══██║██║╚██╗██║██╔═██╗ ",
            "  ██████╔╝██║╚██████╔╝██║██████╔╝██║  ██║██║ ╚████║██║  ██╗",
            "  ╚═════╝ ╚═╝ ╚═════╝ ╚═╝╚═════╝ ╚═╝  ╚═╝╚═╝  ╚═══╝╚═╝  ╚═╝"
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
                int ch = reader.read();

                if (ch == 27) { // Escape sequence
                    int next = reader.read(100);
                    if (next == '[' || next == 'O') {
                        int code = reader.read();
                        if (code == 'A') { // Up Arrow
                            selectedIndex = (selectedIndex - 1 + MENU_ITEMS.length) % MENU_ITEMS.length;
                        } else if (code == 'B') { // Down Arrow
                            selectedIndex = (selectedIndex + 1) % MENU_ITEMS.length;
                        }
                    }
                } else if (ch == '\r' || ch == '\n') { // Enter key
                    executeChoice(selectedIndex, navigator, session);
                    return;
                } else if (ch >= '1' && ch <= '5') { // Direct number key
                    int num = ch - '1';
                    executeChoice(num, navigator, session);
                    return;
                } else if (ch == 3) { // Ctrl+C
                    session.clearScreen();
                    System.exit(0);
                }
            }
        } catch (IOException e) {
            executeChoice(selectedIndex, navigator, session);
        } finally {
            terminal.setAttributes(origAttributes);
        }
    }

    private void renderScreen(TUISession session, int selectedIndex, boolean firstRender) {
        StringBuilder sb = new StringBuilder();
        if (firstRender) {
            sb.append(ConsoleTheme.CLEAR_SCREEN);
        } else {
            sb.append("\u001B[H"); // Cursor Home without wiping screen
        }
        int width = TUILayout.APP_WIDTH;

        // Top Box
        sb.append(TUIBox.top(width)).append("\n");
        sb.append(TUIBox.emptyLine(width)).append("\n");
        for (String line : LOGO_LINES) {
            sb.append(TUIBox.center(ConsoleTheme.logo(line), width)).append("\n");
        }
        sb.append(TUIBox.emptyLine(width)).append("\n");
        sb.append(TUIBox.center(ConsoleTheme.BOLD + ConsoleTheme.FG_DEFAULT + "ENTERPRISE BANKING & FINANCIAL SYSTEM" + ConsoleTheme.RESET, width)).append("\n");
        sb.append(TUIBox.center(ConsoleTheme.muted("Secure  •  Fast  •  Intelligent  •  ACID-Compliant"), width)).append("\n");
        sb.append(TUIBox.emptyLine(width)).append("\n");
        sb.append(TUIBox.divider(width)).append("\n");
        sb.append(TUIBox.center(ConsoleTheme.success("✔ PostgreSQL Connection Pool Ready"), width)).append("\n");
        sb.append(TUIBox.center(ConsoleTheme.muted("Flyway Migration: V1 -> V6 Synced"), width)).append("\n");
        sb.append(TUIBox.bottom(width)).append("\n\n");

        sb.append(" [WELCOME MENU] Use ↑ / ↓ to move, [ENTER] to select, or press number:\n\n");

        for (int i = 0; i < MENU_ITEMS.length; i++) {
            if (i == selectedIndex) {
                sb.append("   ► ").append(ConsoleTheme.highlight(MENU_ITEMS[i])).append("\n");
            } else {
                sb.append("     ").append(MENU_ITEMS[i]).append("   \n");
            }
        }

        sb.append("\n");
        sb.append(TUIBox.statusLine("Ready", "Guest", "UTF-8", width)).append("\n");

        System.out.print(sb.toString());
        System.out.flush();
    }

    private void executeChoice(int index, ScreenNavigator navigator, TUISession session) {
        switch (index) {
            case 0 -> navigator.push(new LoginScreen());
            case 1 -> navigator.push(new RegisterScreen());
            case 2 -> navigator.push(new ForgotPasswordScreen());
            case 3 -> navigator.push(new ExchangeScreen());
            case 4 -> {
                session.clearScreen();
                System.out.println(ConsoleTheme.muted("Thank you for choosing DigiBank. Goodbye!"));
                System.exit(0);
            }
        }
    }
}

