package com.bank.console.components;

import com.bank.console.theme.ConsoleTheme;

public final class TUIBox {
    public static final int DEFAULT_WIDTH = 82;

    // Single-line box characters
    public static final char TL = '┌';
    public static final char TR = '┐';
    public static final char BL = '└';
    public static final char BR = '┘';
    public static final char H  = '─';
    public static final char V  = '│';
    public static final char ML = '├';
    public static final char MR = '┤';

    private TUIBox() {}

    public static String top(int width) {
        return ConsoleTheme.border(TL + String.valueOf(H).repeat(Math.max(0, width - 2)) + TR);
    }

    public static String bottom(int width) {
        return ConsoleTheme.border(BL + String.valueOf(H).repeat(Math.max(0, width - 2)) + BR);
    }

    public static String divider(int width) {
        return ConsoleTheme.border(ML + String.valueOf(H).repeat(Math.max(0, width - 2)) + MR);
    }

    public static String line(String text, int width) {
        int visibleLength = stripAnsi(text).length();
        int padding = Math.max(0, width - 4 - visibleLength);
        return ConsoleTheme.border(String.valueOf(V)) + " " + text + " ".repeat(padding) + " " + ConsoleTheme.border(String.valueOf(V));
    }

    public static String center(String text, int width) {
        int visibleLength = stripAnsi(text).length();
        int totalPad = Math.max(0, width - 4 - visibleLength);
        int padLeft = totalPad / 2;
        int padRight = totalPad - padLeft;
        return ConsoleTheme.border(String.valueOf(V)) + " " + " ".repeat(padLeft) + text + " ".repeat(padRight) + " " + ConsoleTheme.border(String.valueOf(V));
    }

    public static String emptyLine(int width) {
        return line("", width);
    }

    public static String top() {
        return top(DEFAULT_WIDTH);
    }

    public static String bottom() {
        return bottom(DEFAULT_WIDTH);
    }

    public static String divider() {
        return divider(DEFAULT_WIDTH);
    }

    public static String line(String text) {
        return line(text, DEFAULT_WIDTH);
    }

    public static String center(String text) {
        return center(text, DEFAULT_WIDTH);
    }

    public static String emptyLine() {
        return emptyLine(DEFAULT_WIDTH);
    }

    public static String rule(int width) {
        return ConsoleTheme.border(String.valueOf(H).repeat(Math.max(0, width)));
    }

    public static String rule() {
        return rule(DEFAULT_WIDTH);
    }

    public static String twoColumns(String left, String right, int width) {
        int leftLen = stripAnsi(left).length();
        int rightLen = stripAnsi(right).length();
        int space = Math.max(1, width - 4 - leftLen - rightLen);
        return ConsoleTheme.border(String.valueOf(V)) + " " + left + " ".repeat(space) + right + " " + ConsoleTheme.border(String.valueOf(V));
    }

    public static String twoColumns(String left, String right) {
        return twoColumns(left, right, DEFAULT_WIDTH);
    }

    public static String statusLine(String status, String session, String encoding, int width) {
        StringBuilder sb = new StringBuilder();
        sb.append(rule(width)).append("\n");
        String text = String.format(" Status: %s | Session: %s | Encoding: %s", status, session, encoding);
        int vis = stripAnsi(text).length();
        int pad = Math.max(0, width - vis);
        sb.append(text).append(" ".repeat(pad)).append("\n");
        sb.append(rule(width));
        return sb.toString();
    }

    public static String statusLine(String status, String session, String encoding) {
        return statusLine(status, session, encoding, DEFAULT_WIDTH);
    }

    public static int visibleLength(String input) {
        return stripAnsi(input).length();
    }

    public static String stripAnsi(String input) {
        if (input == null) return "";
        return input.replaceAll("\u001B\\[[;?0-9]*[a-zA-Z]", "");
    }
}