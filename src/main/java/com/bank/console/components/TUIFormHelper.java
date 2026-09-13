package com.bank.console.components;

import com.bank.console.theme.ConsoleTheme;
import org.jline.utils.NonBlockingReader;

import java.io.IOException;

/**
 * Reusable helper for enclosed TUI form rendering and keystroke handling.
 * Provides strict 82-column bracketed field rows, arrow/tab navigation,
 * and text editing inside box containers.
 */
public final class TUIFormHelper {

    public enum KeyAction {
        UP,
        DOWN,
        LEFT,
        RIGHT,
        TAB,
        SHIFT_TAB,
        ENTER,
        ESCAPE,
        BACKSPACE,
        CHAR,
        DIGIT,
        OTHER
    }

    public record KeyEvent(KeyAction action, char ch, int code) {}

    private TUIFormHelper() {}

    /**
     * Reads a raw key event from NonBlockingReader with escape sequence decoding.
     */
    public static KeyEvent readKey(NonBlockingReader reader) throws IOException {
        int ch = reader.read();
        if (ch == -1) {
            return new KeyEvent(KeyAction.OTHER, '\0', -1);
        }

        if (ch == 27) { // ESC or Escape Sequence
            int next = reader.read(25);
            if (next == -2 || next == -1) {
                return new KeyEvent(KeyAction.ESCAPE, (char) 27, 27);
            }
            if (next == '[' || next == 'O') {
                int code = reader.read(25);
                return switch (code) {
                    case 'A' -> new KeyEvent(KeyAction.UP, 'A', code);
                    case 'B' -> new KeyEvent(KeyAction.DOWN, 'B', code);
                    case 'C' -> new KeyEvent(KeyAction.RIGHT, 'C', code);
                    case 'D' -> new KeyEvent(KeyAction.LEFT, 'D', code);
                    case 'Z' -> new KeyEvent(KeyAction.SHIFT_TAB, 'Z', code);
                    default -> new KeyEvent(KeyAction.OTHER, (char) (code > 0 ? code : 0), code);
                };
            }
            return new KeyEvent(KeyAction.ESCAPE, (char) next, next);
        } else if (ch == '\t') {
            return new KeyEvent(KeyAction.TAB, '\t', 9);
        } else if (ch == '\r' || ch == '\n') {
            return new KeyEvent(KeyAction.ENTER, '\n', ch);
        } else if (ch == 127 || ch == 8) {
            return new KeyEvent(KeyAction.BACKSPACE, '\b', ch);
        } else if (ch >= '0' && ch <= '9') {
            return new KeyEvent(KeyAction.DIGIT, (char) ch, ch);
        } else if (ch >= 32 && ch <= 126) {
            return new KeyEvent(KeyAction.CHAR, (char) ch, ch);
        } else if (ch == 3) { // Ctrl+C
            System.exit(0);
        }

        return new KeyEvent(KeyAction.OTHER, (char) ch, ch);
    }

    /**
     * Formats a bracketed input row matching the 82-column layout.
     * Example: "  Deposit Amount    : [ 1,000.00                                              ]"
     */
    public static String formatFieldRow(String label, String value, boolean isFocused, int labelWidth, int valueWidth) {
        String displayVal = value != null ? value : "";
        if (isFocused) {
            displayVal = displayVal + "_";
        }
        if (displayVal.length() > valueWidth) {
            displayVal = displayVal.substring(0, valueWidth);
        }

        String paddedVal = String.format("%-" + valueWidth + "s", displayVal);
        String bracketContent = isFocused ? ConsoleTheme.highlight(paddedVal) : paddedVal;
        String labelPart = String.format("  %-" + labelWidth + "s: [ ", label);
        String row = labelPart + bracketContent + " ]";
        return TUIBox.line(row, TUILayout.APP_WIDTH);
    }

    /**
     * Overload with standard 18-char label and 50-char value slot.
     */
    public static String formatFieldRow(String label, String value, boolean isFocused) {
        return formatFieldRow(label, value, isFocused, 18, 50);
    }

    /**
     * Formats a read-only info row without brackets.
     * Example: "  Current Balance   :   $ 2,700.00 USD                                          "
     */
    public static String formatInfoRow(String label, String value, int labelWidth, int valueWidth) {
        String displayVal = value != null ? value : "";
        if (displayVal.length() > valueWidth) {
            displayVal = displayVal.substring(0, valueWidth);
        }
        String row = String.format("  %-" + labelWidth + "s:   %-" + (valueWidth + 2) + "s", label, displayVal);
        return TUIBox.line(row, TUILayout.APP_WIDTH);
    }

    public static String formatInfoRow(String label, String value) {
        return formatInfoRow(label, value, 18, 50);
    }
}
