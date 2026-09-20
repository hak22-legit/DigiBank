package com.bank.ui;

import java.util.regex.Pattern;

/**
 * Utility for safe ANSI color formatting within fixed-width terminal box layouts.
 *
 * Safety Rule:
 * Because ANSI escape sequences (e.g. \033[32m) consume zero printable spaces in the
 * terminal emulator but are counted by String.length(), layout padding must always be
 * calculated on raw printable strings BEFORE injecting ANSI color tags.
 */
public class AnsiFormatter {

    public static final String RESET    = "\033[0m";
    public static final String INVERSE  = "\033[7m";
    public static final String BOLD     = "\033[1m";
    public static final String GREEN    = "\033[32m";
    public static final String RED      = "\033[31m";
    public static final String YELLOW   = "\033[33m";
    public static final String BLUE     = "\033[34m";
    public static final String MAGENTA  = "\033[35m";
    public static final String CYAN     = "\033[36m";
    public static final String WHITE    = "\033[37m";
    public static final String GRAY     = "\033[90m";

    private static final Pattern ANSI_PATTERN = Pattern.compile("\\033\\[[0-9;]*[a-zA-Z]");

    /**
     * Removes all ANSI escape sequences from the given text to calculate
     * true visible terminal length.
     *
     * @param text String potentially containing ANSI escape codes
     * @return Raw printable text without ANSI codes
     */
    public static String stripAnsi(String text) {
        if (text == null) {
            return "";
        }
        return ANSI_PATTERN.matcher(text).replaceAll("");
    }

    /**
     * Calculates the visible printable length of a string in terminal columns.
     *
     * @param text String to measure
     * @return Number of visible characters
     */
    public static int visibleLength(String text) {
        return stripAnsi(text).length();
    }

    /**
     * Colorize a numeric balance string without breaking table width.
     *
     * @param formattedAmount Pre-formatted and padded numeric amount string
     * @param isCredit True for credit (GREEN), false for debit (RED)
     * @return Colorized string with reset code appended
     */
    public static String colorizeDelta(String formattedAmount, boolean isCredit) {
        String color = isCredit ? GREEN : RED;
        return color + formattedAmount + RESET;
    }

    /**
     * Wraps raw text in the specified ANSI color and appends RESET.
     *
     * @param text The text to wrap
     * @param ansiColor The ANSI escape code to prepend
     * @return Colorized string
     */
    public static String colorize(String text, String ansiColor) {
        if (text == null) {
            text = "";
        }
        return ansiColor + text + RESET;
    }

    /**
     * Formats a 74-character printable line within outer border pipes:
     * │  <74 chars>  │
     *
     * @param raw74Chars The content to format
     * @return Clamped and boxed row string
     */
    public static String formatRow(String raw74Chars) {
        if (raw74Chars == null) {
            raw74Chars = "";
        }
        if (raw74Chars.length() > 74) {
            raw74Chars = raw74Chars.substring(0, 71) + "...";
        }
        return String.format("│  %-74s  │", raw74Chars);
    }

    /**
     * Renders a full 74-character printable line within outer border pipes.
     *
     * @param raw74Chars The printable string (truncated to 71 + '...' if longer than 74)
     */
    public static void printRow(String raw74Chars) {
        if (raw74Chars == null) {
            raw74Chars = "";
        }
        if (raw74Chars.length() > 74) {
            raw74Chars = raw74Chars.substring(0, 71) + "...";
        }
        System.out.printf("│  %-74s  │%n", raw74Chars);
    }
}
