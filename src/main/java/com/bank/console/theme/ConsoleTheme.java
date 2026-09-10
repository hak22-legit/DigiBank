package com.bank.console.theme;

import com.williamcallahan.tui4j.compat.lipgloss.Style;
import com.williamcallahan.tui4j.compat.lipgloss.color.Color;
import com.williamcallahan.tui4j.term.TerminalInfo;

/**
 * Adaptive Terminal Theme Engine backed by TUI4J Lipgloss styling.
 * Engineered specifically to guarantee visual fidelity, readability, and high contrast
 * on BOTH light-mode terminals (white/beige background) and dark-mode terminals (black/navy background).
 *
 * Adheres strictly to:
 * - NO hardcoded bright-white or light-gray foreground body text (which disappears on white terminals)
 * - Inverse / Reversed Video for high-contrast active selections and highlights
 * - Safe 16-color ANSI semantic palette with standard weights and Dim/Faint mode
 */
public final class ConsoleTheme {
    static {
        try {
            if (TerminalInfo.get() == null) {
                TerminalInfo.provide(() -> new TerminalInfo(true, null));
            }
        } catch (Exception e) {
            TerminalInfo.provide(() -> new TerminalInfo(true, null));
        }
    }

    private ConsoleTheme() {}

    // TUI4J Lipgloss Styles
    public static final Style BORDER_STYLE = Style.newStyle().foreground(Color.color("32"));
    public static final Style BOLD_STYLE = Style.newStyle().bold(true);
    public static final Style MUTED_STYLE = Style.newStyle().faint(true);
    public static final Style SUCCESS_STYLE = Style.newStyle().bold(true).foreground(Color.color("32"));
    public static final Style WARNING_STYLE = Style.newStyle().bold(true).foreground(Color.color("33"));
    public static final Style ERROR_STYLE = Style.newStyle().bold(true).foreground(Color.color("31"));
    public static final Style INFO_STYLE = Style.newStyle().bold(true).foreground(Color.color("36"));
    public static final Style HIGHLIGHT_STYLE = Style.newStyle().bold(true).reverse(true);
    public static final Style SELECTED_STYLE = Style.newStyle().reverse(true);

    // Reset & Font Attributes
    public static final String RESET = "\u001B[0m";
    public static final String BOLD = "\u001B[1m";
    public static final String DIM = "\u001B[2m";
    public static final String ITALIC = "\u001B[3m";
    public static final String UNDERLINE = "\u001B[4m";
    public static final String REVERSE = "\u001B[7m"; // Inverted contrast

    // Safe 16-Color Palette (Standard Terminal Colors)
    public static final String FG_DEFAULT = "\u001B[39m"; // Adapts automatically to terminal default
    public static final String BG_DEFAULT = "\u001B[49m";
    public static final String FG_BLACK = "\u001B[30m";
    public static final String FG_RED = "\u001B[31m";
    public static final String FG_GREEN = "\u001B[32m";
    public static final String FG_YELLOW = "\u001B[33m";
    public static final String FG_BLUE = "\u001B[34m";
    public static final String FG_MAGENTA = "\u001B[35m";
    public static final String FG_CYAN = "\u001B[36m";
    public static final String FG_WHITE = "\u001B[37m";

    // Bright Colors (Use cautiously with background contrast)
    public static final String FG_BRIGHT_RED = "\u001B[91m";
    public static final String FG_BRIGHT_GREEN = "\u001B[92m";
    public static final String FG_BRIGHT_YELLOW = "\u001B[93m";
    public static final String FG_BRIGHT_BLUE = "\u001B[94m";
    public static final String FG_BRIGHT_MAGENTA = "\u001B[95m";
    public static final String FG_BRIGHT_CYAN = "\u001B[96m";
    public static final String FG_BRIGHT_WHITE = "\u001B[97m";
    public static final String FG_GRAY = "\u001B[90m";

    // Legacy Brand Compatibility Tokens
    public static final String BRAND_GOLD = "\u001B[33m"; // Safe standard yellow across all backgrounds
    public static final String BRAND_GREEN = "\u001B[32m"; // Safe standard green

    // Terminal Screen & Cursor Control
    public static final String CLEAR_SCREEN = "\u001B[H\u001B[2J";
    public static final String HIDE_CURSOR = "\u001B[?25l";
    public static final String SHOW_CURSOR = "\u001B[?25h";

    // Semantic Adaptive Formatters backed by TUI4J

    /**
     * Creates a new TUI4J Lipgloss Style instance.
     */
    public static Style style() {
        return Style.newStyle();
    }

    /**
     * Bold text using TUI4J bold style.
     */
    public static String bold(String text) {
        if (text == null) return "";
        return BOLD_STYLE.render(text);
    }

    /**
     * Active selection items or prominent badges use Inverted Video.
     * Inverted text guarantees high contrast whether the terminal background
     * is pure white, paper cream, solarized light, navy, or pitch black.
     */
    public static String highlight(String text) {
        if (text == null) return "";
        return HIGHLIGHT_STYLE.render(" " + text + " ");
    }

    /**
     * Inline highlight without added padding spaces.
     */
    public static String inlineHighlight(String text) {
        if (text == null) return "";
        return HIGHLIGHT_STYLE.render(text);
    }

    /**
     * Primary body text: bold with terminal's default foreground color.
     */
    public static String primary(String text) {
        if (text == null) return "";
        return BOLD_STYLE.render(text);
    }

    /**
     * Muted / secondary / breadcrumb text: utilizes faint mode.
     * Works identically on light and dark terminals by reducing luminescence of the host text.
     */
    public static String muted(String text) {
        if (text == null) return "";
        return MUTED_STYLE.render(text);
    }

    /**
     * ASCII logo banner in bold standard green.
     */
    public static String logo(String text) {
        if (text == null) return "";
        return SUCCESS_STYLE.render(text);
    }

    /**
     * Structural box border: uses safe standard green.
     */
    public static String border(String text) {
        if (text == null) return "";
        return BORDER_STYLE.render(text);
    }

    /**
     * Success / Active / Approved: Standard Green with Bold weight.
     */
    public static String success(String text) {
        if (text == null) return "";
        return SUCCESS_STYLE.render(text);
    }

    /**
     * Warning / Attention: Standard Yellow / Amber.
     */
    public static String warning(String text) {
        if (text == null) return "";
        return WARNING_STYLE.render(text);
    }

    /**
     * Critical / Danger / Debt: Standard Red with Bold weight.
     */
    public static String error(String text) {
        if (text == null) return "";
        return ERROR_STYLE.render(text);
    }

    /**
     * Informational accents: Standard Blue or Cyan.
     */
    public static String info(String text) {
        if (text == null) return "";
        return INFO_STYLE.render(text);
    }

    /**
     * Neutral inverted bar for selected menu rows.
     */
    public static String selectedBar(String text) {
        if (text == null) return "";
        return SELECTED_STYLE.render(" " + text + " ");
    }
}