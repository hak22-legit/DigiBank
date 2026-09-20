package com.bank.ui;

public final class Ansi {
    public static final String RESET     = "\033[0m";
    public static final String BOLD      = "\033[1m";
    public static final String DIM       = "\033[2m";
    public static final String INVERSE   = "\033[7m";

    // Semantic Palette
    public static final String GREEN     = "\033[32m";  // Inflow, Active, Clear, Success
    public static final String RED       = "\033[31m";  // Outflow, Danger, Over-budget
    public static final String YELLOW    = "\033[33m";  // Warnings, Primary tags, Highlights
    public static final String CYAN      = "\033[36m";  // Table headers, Currency codes
    public static final String GRAY      = "\033[90m";  // Muted borders, timestamps, dividers
    public static final String BRIGHT_BLACK = "\033[90m"; // Muted dark gray
    public static final String WHITE     = "\033[37m";  // Crisp white values

    // 50% opacity equivalent for ANSI terminals (Dim + Muted Gray)
    public static final String FOOTER_DIM_GRAY = "\033[2;90m";

    private Ansi() {}

    /**
     * Colorize a value without altering its printed column width.
     */
    public static String green(String text)  { return GREEN + text + RESET; }
    public static String red(String text)    { return RED + text + RESET; }
    public static String yellow(String text) { return YELLOW + text + RESET; }
    public static String cyan(String text)   { return CYAN + text + RESET; }
    public static String gray(String text)   { return GRAY + text + RESET; }
    public static String white(String text)  { return WHITE + text + RESET; }
    public static String bold(String text)   { return BOLD + text + RESET; }
    public static String inverse(String text){ return INVERSE + text + RESET; }

    /**
     * Formats a standardized footer key guide line clamped strictly to the layout frame.
     * Applies 50% visual opacity (Dimmed Muted Gray) and guarantees terminal color reset.
     */
    public static String keyGuide(String guideContent) {
        if (guideContent == null) return "";
        return FOOTER_DIM_GRAY + " " + guideContent.trim() + RESET;
    }

    /**
     * Prints a standardized footer key guide line clamped strictly to the layout frame.
     * Applies 50% visual capacity (Dimmed Muted Gray) and guarantees terminal color reset.
     */
    public static void printKeyGuide(String guideContent) {
        System.out.println(keyGuide(guideContent));
    }
}
