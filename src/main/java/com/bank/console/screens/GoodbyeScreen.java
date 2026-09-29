package com.bank.console.screens;

import com.bank.console.ScreenNavigator;
import com.bank.console.TUISession;
import com.bank.console.components.ScreenRenderer;
import com.bank.console.components.TUIBox;
import com.bank.console.components.TUILayout;
import com.bank.console.theme.ConsoleTheme;
import org.jline.terminal.Attributes;
import org.jline.terminal.Terminal;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

/**
 * SYSTEM LOGOUT & TERMINATION SCREEN (82 Columns)
 * Interactive shutdown screen with 82-column layout, countdown animation,
 * and graceful terminal session teardown.
 */
public class GoodbyeScreen implements Screen {

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss 'UTC'");

    @Override
    public void render(ScreenNavigator navigator, TUISession session) {
        show(session);
    }

    /**
     * Executes interactive shutdown sequence with a 2-second countdown before exiting.
     */
    public static void show(TUISession session) {
        show(session, 2, true);
    }

    /**
     * Parameterized shutdown sequence for testing and execution control.
     *
     * @param session          active TUISession
     * @param countdownSeconds countdown duration in seconds (default 2)
     * @param doExit           whether to call System.exit(0) upon completion
     */
    public static void show(TUISession session, int countdownSeconds, boolean doExit) {
        Terminal terminal = session != null ? session.getTerminal() : null;
        Attributes origAttrs = null;
        if (terminal != null) {
            try {
                origAttrs = terminal.enterRawMode();
            } catch (Exception ignored) {}
        }

        int width = TUILayout.APP_WIDTH;
        String timestamp = ZonedDateTime.now(ZoneId.of("UTC")).format(TIME_FMT);

        try {
            boolean firstRender = true;
            if (countdownSeconds <= 0) {
                String content = renderContent(0, timestamp, width);
                ScreenRenderer.render(content, firstRender);
            } else {
                for (int sec = countdownSeconds; sec >= 1; sec--) {
                    String content = renderContent(sec, timestamp, width);
                    ScreenRenderer.render(content, firstRender);
                    firstRender = false;

                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        } finally {
            if (terminal != null && origAttrs != null) {
                try {
                    terminal.setAttributes(origAttrs);
                } catch (Exception ignored) {}
            }
            // Ensure ANSI codes are reset so the user's terminal/PowerShell returns to normal text styling
            System.out.print("\033[0m\n");
            System.out.flush();
        }

        if (session != null) {
            try {
                session.close();
            } catch (Exception ignored) {}
        }

        if (doExit) {
            System.exit(0);
        }
    }

    /**
     * Generates the 82-column minimal logout box content.
     */
    public static String renderContent(int seconds, String timestamp, int width) {
        StringBuilder sb = new StringBuilder();

        // 1. Header Box
        sb.append(TUIBox.top(width)).append("\n");
        sb.append(TUIBox.line(ConsoleTheme.primary("DIGIBANK CORE > SYSTEM LOGOUT & TERMINATION"), width)).append("\n");
        sb.append(TUIBox.divider(width)).append("\n");

        // 2. Centered Messages
        sb.append(TUIBox.emptyLine(width)).append("\n");
        sb.append(TUIBox.center("\033[1;36mThank you for using DigiBank Core!\033[0m", width)).append("\n");
        sb.append(TUIBox.emptyLine(width)).append("\n");

        String timeText = "Session terminated at: " + (timestamp != null ? timestamp : ZonedDateTime.now(ZoneId.of("UTC")).format(TIME_FMT));
        sb.append(TUIBox.center(timeText, width)).append("\n");
        sb.append(TUIBox.emptyLine(width)).append("\n");

        String countdownText = "Closing terminal gateway in " + seconds + "s...";
        sb.append(TUIBox.center("\033[33m" + countdownText + "\033[0m", width)).append("\n");
        sb.append(TUIBox.emptyLine(width)).append("\n");

        // 3. Status Bar
        sb.append(TUIBox.divider(width)).append("\n");
        sb.append(TUIBox.line("Status: Goodbye! Have a great day.", width)).append("\n");
        sb.append(TUIBox.bottom(width)).append("\n");

        // 4. Footer Hint
        sb.append(" \033[2;90mConnection closed safely.\033[0m\n");

        return sb.toString();
    }

    public static String renderContent(int seconds, int width) {
        return renderContent(seconds, null, width);
    }

    public static String renderContent(int seconds) {
        return renderContent(seconds, null, TUILayout.APP_WIDTH);
    }
}
