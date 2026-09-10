package com.bank.console.components;

import com.bank.console.theme.ConsoleTheme;

/**
 * Reusable High-Fidelity TUI Components for DigiBank.
 * Strict 82-column layout grid, adaptive light/dark contrast,
 * and structural unicode progress bars.
 */
public final class TuiComponents {
    public static final int APP_WIDTH = 82;

    private TuiComponents() {}

    // =========================================================
    // High-Contrast Structural Progress Bar
    // =========================================================

    /**
     * Renders a dual-shade progress bar using structural unicode characters (█, ░).
     * Works with 100% clarity on white, black, solarized, or colored terminals
     * without relying exclusively on color hue.
     *
     * Example output: [████████░░░░] 67.5%
     *
     * @param current Current value
     * @param max     Maximum value
     * @param barWidth Number of block characters inside brackets
     * @return Formatted progress bar string
     */
    public static String getProgressBar(double current, double max, int barWidth) {
        if (max <= 0) max = 1.0;
        double ratio = Math.min(1.0, Math.max(0.0, current / max));
        int filled = (int) Math.round(ratio * barWidth);
        int empty = Math.max(0, barWidth - filled);

        String filledBlocks = "█".repeat(filled);
        String emptyBlocks = "░".repeat(empty);
        double percent = ratio * 100.0;

        String color = (ratio >= 0.90) ? ConsoleTheme.warning("") : ConsoleTheme.success("");
        return String.format("[%s%s%s%s] %5.1f%%",
                color, filledBlocks, ConsoleTheme.muted(emptyBlocks), ConsoleTheme.RESET, percent);
    }

    /**
     * Overload for budget meter with warning threshold styling.
     */
    public static String getBudgetMeter(double spent, double limit, int barWidth) {
        if (limit <= 0) limit = 1.0;
        double ratio = Math.min(1.5, Math.max(0.0, spent / limit));
        int effectiveBar = Math.min(barWidth, (int) Math.round(Math.min(1.0, ratio) * barWidth));
        int empty = Math.max(0, barWidth - effectiveBar);

        String filledBlocks = "█".repeat(effectiveBar);
        String emptyBlocks = "░".repeat(empty);
        double percent = (spent / limit) * 100.0;

        String style;
        if (percent > 100.0) {
            style = ConsoleTheme.error("");
        } else if (percent > 80.0) {
            style = ConsoleTheme.warning("");
        } else {
            style = ConsoleTheme.success("");
        }

        return String.format("[%s%s%s%s] %5.1f%%%s",
                style, filledBlocks, ConsoleTheme.muted(emptyBlocks), ConsoleTheme.RESET,
                percent, percent > 100.0 ? " " + ConsoleTheme.error("[OVER LIMIT]") : "");
    }

    // =========================================================
    // Inverted Contrast Menu Row
    // =========================================================

    /**
     * Renders a menu item. When selected, applies Inverse / Reversed Video (\u001B[7m)
     * with an active pointer (►), guaranteeing optimal contrast on both dark and light modes.
     */
    public static String renderMenuItem(String key, String title, String desc, boolean selected, int width) {
        String keyDisplay = "[" + key + "]";
        if (selected) {
            String leftPart = " ► " + keyDisplay + " " + title;
            String rightPart = desc != null ? " (" + desc + ")" : "";
            String plainRow = leftPart + rightPart;
            int visLen = TUIBox.stripAnsi(plainRow).length();
            int totalPad = Math.max(0, width - 4 - visLen);
            String content = leftPart + rightPart + " ".repeat(totalPad);
            return ConsoleTheme.border("│") + " " + ConsoleTheme.highlight(content) + " " + ConsoleTheme.border("│");
        } else {
            String leftPart = "   " + ConsoleTheme.muted(keyDisplay) + " " + title;
            String rightPart = desc != null ? " " + ConsoleTheme.muted("— " + desc) : "";
            String full = leftPart + rightPart;
            return TUIBox.line(full, width);
        }
    }

    // =========================================================
    // Layout and Framing Components (Strict 82-Column Grid)
    // =========================================================

    public static void printHeader(String userOrRole) {
        int width = APP_WIDTH;
        System.out.println(TUIBox.top(width));

        String brand = ConsoleTheme.logo("DIGIBANK") + " " + ConsoleTheme.muted("• Enterprise Core");
        String rightText = userOrRole != null ? ConsoleTheme.highlight(userOrRole) : ConsoleTheme.muted("Guest");

        int brandVis = TUIBox.stripAnsi(brand).length();
        int rightVis = TUIBox.stripAnsi(rightText).length();
        int space = Math.max(1, width - 4 - brandVis - rightVis);

        System.out.println(ConsoleTheme.border("│") + " " + brand + " ".repeat(space) + rightText + " " + ConsoleTheme.border("│"));
        System.out.println(TUIBox.divider(width));
    }

    public static void printScreenTitle(String title) {
        int width = APP_WIDTH;
        System.out.println(TUIBox.emptyLine(width));
        System.out.println(TUIBox.center(ConsoleTheme.BOLD + ConsoleTheme.FG_DEFAULT + title.toUpperCase() + ConsoleTheme.RESET, width));
        System.out.println(TUIBox.emptyLine(width));
        System.out.println(TUIBox.divider(width));
    }

    public static void printFooter(String hints) {
        int width = APP_WIDTH;
        System.out.println(TUIBox.divider(width));
        String defaultHints = hints != null ? hints : "↑/↓ Move   Enter Select   1-9 Numeric Shortcut   Esc Back";
        System.out.println(TUIBox.center(ConsoleTheme.muted(defaultHints), width));
        System.out.println(TUIBox.bottom(width));
    }

    public static void printAlert(String message, boolean isError) {
        int width = APP_WIDTH;
        String alertText = isError ? ConsoleTheme.error("✗ " + message) : ConsoleTheme.success("✔ " + message);
        System.out.println(TUIBox.center(alertText, width));
    }

    public static void printWarning(String message) {
        int width = APP_WIDTH;
        System.out.println(TUIBox.center(ConsoleTheme.warning("⚠ " + message), width));
    }
}
