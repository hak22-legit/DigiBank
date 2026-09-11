package com.bank.console.components;

import com.bank.console.TUISession;
import com.bank.console.TerminalContext;
import org.jline.terminal.Terminal;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;

/**
 * Reusable dynamic terminal layout rendering engine.
 * Dynamically centers 82-column TUI boxes and screens both horizontally and vertically
 * based on live terminal dimensions, with graceful fallback boundaries for narrow/short windows.
 */
public final class ScreenRenderer {
    public static final int DEFAULT_BOX_WIDTH = 82;

    private ScreenRenderer() {}

    /**
     * Calculates the horizontal left padding (in spaces) to center a box of width {@code boxWidth}.
     * If {@code termWidth < boxWidth}, returns 0 to prevent line-wrapping or negative margins.
     */
    public static int computeLeftPadding(int termWidth, int boxWidth) {
        if (termWidth < boxWidth) {
            return 0;
        }
        return Math.max(0, (termWidth - boxWidth) / 2);
    }

    /**
     * Calculates the vertical top padding (in blank lines) to center a box of height {@code boxHeight}.
     * If {@code termHeight <= boxHeight}, returns 0 to prevent content clipping or scrolling.
     */
    public static int computeTopPadding(int termHeight, int boxHeight) {
        if (termHeight <= boxHeight) {
            return 0;
        }
        return Math.max(0, (termHeight - boxHeight) / 2);
    }

    /**
     * Resolves the active terminal column width.
     */
    public static int getTerminalWidth() {
        try {
            TUISession session = TUISession.getInstance();
            if (session != null) {
                int w = session.getTerminalWidth();
                if (w > 0) return w;
            }
        } catch (Throwable ignored) {}

        try {
            TerminalContext ctx = TerminalContext.getInstance();
            if (ctx != null) {
                int w = ctx.getTerminalWidth();
                if (w > 0) return w;
            }
        } catch (Throwable ignored) {}

        return DEFAULT_BOX_WIDTH;
    }

    /**
     * Resolves the active terminal row height.
     */
    public static int getTerminalHeight() {
        try {
            TUISession session = TUISession.getInstance();
            if (session != null) {
                int h = session.getTerminalHeight();
                if (h > 0) return h;
            }
        } catch (Throwable ignored) {}

        try {
            TerminalContext ctx = TerminalContext.getInstance();
            if (ctx != null) {
                int h = ctx.getTerminalHeight();
                if (h > 0) return h;
            }
        } catch (Throwable ignored) {}

        return 24;
    }

    /**
     * Returns the horizontal left padding for the default 82-column box.
     */
    public static int getLeftPadding() {
        return getLeftPadding(DEFAULT_BOX_WIDTH);
    }

    /**
     * Returns the horizontal left padding for a custom box width.
     */
    public static int getLeftPadding(int boxWidth) {
        return computeLeftPadding(getTerminalWidth(), boxWidth);
    }

    /**
     * Returns a string of spaces corresponding to {@code getLeftPadding()}.
     */
    public static String getLeftPaddingSpaces() {
        return " ".repeat(getLeftPadding());
    }

    /**
     * Returns a string of spaces corresponding to {@code getLeftPadding(boxWidth)}.
     */
    public static String getLeftPaddingSpaces(int boxWidth) {
        return " ".repeat(getLeftPadding(boxWidth));
    }

    /**
     * Returns the vertical top padding for a given box height.
     */
    public static int getTopPadding(int boxHeight) {
        return computeTopPadding(getTerminalHeight(), boxHeight);
    }

    /**
     * Formats raw content into a dynamically centered frame.
     *
     * @param content     The multiline box/screen content to center.
     * @param termWidth   Terminal width in columns.
     * @param termHeight  Terminal height in rows.
     * @param boxWidth    Fixed content box width (typically 82).
     * @param firstRender True on initial screen display (clears entire buffer),
     *                    false on redrawing during raw-key loops.
     * @return Fully formatted ANSI frame centered horizontally and vertically.
     */
    public static String formatCentered(String content, int termWidth, int termHeight, int boxWidth, boolean firstRender) {
        if (content == null || content.isBlank()) {
            return "";
        }

        // Strip leading cursor-home or screen-clearing ANSI escape sequences
        // so that cursor positioning is not reset after topPadding is emitted.
        String clean = content.replaceAll("^(\\u001B\\[[0-9;]*[HJK])+\\r?\\n?", "");

        List<String> lines = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new StringReader(clean))) {
            String line;
            while ((line = reader.readLine()) != null) {
                lines.add(line);
            }
        } catch (IOException ignored) {}

        // Trim any trailing blank lines that result from trailing newlines
        while (!lines.isEmpty() && lines.get(lines.size() - 1).trim().isEmpty()) {
            lines.remove(lines.size() - 1);
        }

        int boxHeight = lines.size();
        int leftPadding = computeLeftPadding(termWidth, boxWidth);
        int topPadding = computeTopPadding(termHeight, boxHeight);
        String indent = " ".repeat(leftPadding);

        StringBuilder sb = new StringBuilder();

        // Screen buffer control sequence
        if (firstRender) {
            sb.append("\u001B[H\u001B[2J");
        } else {
            sb.append("\u001B[H\u001B[J");
        }

        // Vertical centering: topPadding blank lines
        for (int i = 0; i < topPadding; i++) {
            sb.append("\n");
        }

        // Horizontal centering: prefix each row with leftPadding spaces
        for (String line : lines) {
            sb.append(indent).append(line).append("\n");
        }

        return sb.toString();
    }

    /**
     * Renders centered content directly to {@code System.out} with buffer clearing.
     */
    public static void render(String content) {
        render(content, DEFAULT_BOX_WIDTH, true);
    }

    /**
     * Renders centered content directly to {@code System.out} with specified redraw mode.
     */
    public static void render(String content, boolean firstRender) {
        render(content, DEFAULT_BOX_WIDTH, firstRender);
    }

    /**
     * Renders centered content with custom box width and redraw mode.
     */
    public static void render(String content, int boxWidth, boolean firstRender) {
        int termWidth = getTerminalWidth();
        int termHeight = getTerminalHeight();
        String formatted = formatCentered(content, termWidth, termHeight, boxWidth, firstRender);
        System.out.print(formatted);
        System.out.flush();
    }
}
