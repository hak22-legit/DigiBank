package com.bank.console.components;

import com.bank.console.theme.ConsoleTheme;

public final class TUILayout {
    public static final int APP_WIDTH = 82;

    private TUILayout() {}

    public static int getWidth() {
        return APP_WIDTH;
    }

    public static String header(String userOrRole) {
        int width = getWidth();
        StringBuilder sb = new StringBuilder();
        sb.append(TUIBox.top(width)).append("\n");

        String brand = ConsoleTheme.logo("DIGIBANK") + " " + ConsoleTheme.muted("• Digital Banking");
        String rightText = userOrRole != null ? ConsoleTheme.highlight(userOrRole) : ConsoleTheme.muted("Guest");

        int brandVis = TUIBox.stripAnsi(brand).length();
        int rightVis = TUIBox.stripAnsi(rightText).length();
        int space = Math.max(1, width - 4 - brandVis - rightVis);

        sb.append(ConsoleTheme.border(String.valueOf(TUIBox.V))).append(" ")
          .append(brand).append(" ".repeat(space)).append(rightText).append(" ")
          .append(ConsoleTheme.border(String.valueOf(TUIBox.V))).append("\n");
        sb.append(TUIBox.divider(width)).append("\n");
        return sb.toString();
    }

    public static void printHeader(String userOrRole) {
        System.out.print(header(userOrRole));
    }

    public static String screenTitle(String title) {
        int width = getWidth();
        StringBuilder sb = new StringBuilder();
        sb.append(TUIBox.emptyLine(width)).append("\n");
        sb.append(TUIBox.center(ConsoleTheme.BOLD + ConsoleTheme.FG_DEFAULT + title.toUpperCase() + ConsoleTheme.RESET, width)).append("\n");
        sb.append(TUIBox.emptyLine(width)).append("\n");
        sb.append(TUIBox.divider(width)).append("\n");
        return sb.toString();
    }

    public static void printScreenTitle(String title) {
        System.out.print(screenTitle(title));
    }

    public static String footer(String hints) {
        int width = getWidth();
        StringBuilder sb = new StringBuilder();
        sb.append(TUIBox.divider(width)).append("\n");
        String defaultHints = hints != null ? hints : "↑/↓ Move   Enter Select   Esc Back   Ctrl+C Quit";
        sb.append(TUIBox.center(ConsoleTheme.muted(defaultHints), width)).append("\n");
        sb.append(TUIBox.bottom(width)).append("\n");
        return sb.toString();
    }

    public static void printFooter(String hints) {
        System.out.print(footer(hints));
    }

    public static String alert(String message, boolean isError) {
        int width = getWidth();
        String alertText = isError ? ConsoleTheme.error("✗ " + message) : ConsoleTheme.success("✔ " + message);
        return TUIBox.center(alertText, width) + "\n";
    }

    public static void printAlert(String message, boolean isError) {
        System.out.print(alert(message, isError));
    }
}