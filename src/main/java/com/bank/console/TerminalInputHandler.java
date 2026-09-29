package com.bank.console;

import org.jline.utils.NonBlockingReader;

import java.io.IOException;

/**
 * Standardized Terminal Input Handler & Keystroke Parser.
 * Unifies ANSI escape sequence decoding, Vim-style navigation keys (hjkl),
 * special control keys, normalized hotkeys, and collision-free text/numeric input mode.
 */
public final class TerminalInputHandler {

    public enum KeyType {
        UP,
        DOWN,
        LEFT,
        RIGHT,
        ENTER,
        ESCAPE,
        TAB,
        SHIFT_TAB,
        BACKSPACE,
        DIGIT,
        CHAR,
        UNKNOWN
    }

    public record KeyCode(KeyType type, char ch, int code, String rawSequence) {
        public boolean isUp() { return type == KeyType.UP; }
        public boolean isDown() { return type == KeyType.DOWN; }
        public boolean isLeft() { return type == KeyType.LEFT; }
        public boolean isRight() { return type == KeyType.RIGHT; }
        public boolean isEnter() { return type == KeyType.ENTER; }
        public boolean isEscape() { return type == KeyType.ESCAPE; }
        public boolean isTab() { return type == KeyType.TAB; }
        public boolean isShiftTab() { return type == KeyType.SHIFT_TAB; }
        public boolean isBackspace() { return type == KeyType.BACKSPACE; }
        public boolean isDigit() { return type == KeyType.DIGIT; }
        public boolean isChar() { return type == KeyType.CHAR; }

        /**
         * Returns normalized uppercase character for collision-free single-char hotkey matching.
         */
        public char getUpperChar() {
            return Character.toUpperCase(ch);
        }

        /**
         * Checks if the character matches a target key (case-insensitive).
         */
        public boolean is(char target) {
            return Character.toUpperCase(ch) == Character.toUpperCase(target);
        }

        /**
         * Checks if the character matches any of the target keys (case-insensitive).
         */
        public boolean isAny(char... targets) {
            char upper = Character.toUpperCase(ch);
            for (char t : targets) {
                if (upper == Character.toUpperCase(t)) return true;
            }
            return false;
        }

        /**
         * Checks if the key represents an exit / back command (Esc key or 'B'/'b').
         */
        public boolean isEscapeOrBack() {
            return type == KeyType.ESCAPE || is('B');
        }

        /**
         * Returns a clean normalized string representation (e.g. "UP", "DOWN", "ESC", "ENTER", "TAB", "H", "1").
         */
        public String asNormalizedKey() {
            return switch (type) {
                case UP -> "UP";
                case DOWN -> "DOWN";
                case LEFT -> "LEFT";
                case RIGHT -> "RIGHT";
                case ENTER -> "ENTER";
                case ESCAPE -> "ESC";
                case TAB -> "TAB";
                case SHIFT_TAB -> "SHIFT_TAB";
                case BACKSPACE -> "BACKSPACE";
                case DIGIT, CHAR -> String.valueOf(Character.toUpperCase(ch));
                default -> "UNKNOWN";
            };
        }
    }

    private TerminalInputHandler() {}

    /**
     * Checks if the key represents an exit / back command (Esc key or 'B'/'b').
     */
    public static boolean isEscapeOrBack(KeyCode event) {
        return event != null && event.isEscapeOrBack();
    }

    /**
     * Drains any lingering newline ('\n' or '\r') characters from the input stream
     * when transitioning between screens to prevent ghost keypresses or unintended fall-through.
     */
    public static void drainBuffer(NonBlockingReader reader) {
        if (reader == null) return;
        try {
            while (reader.ready()) {
                int peek = reader.peek(10);
                if (peek == '\n' || peek == '\r') {
                    reader.read();
                } else {
                    break;
                }
            }
        } catch (Throwable ignored) {}

        try {
            while (System.in.available() > 0) {
                int b = System.in.read();
                if (b != '\n' && b != '\r') {
                    break;
                }
            }
        } catch (Exception ignored) {}
    }

    /**
     * Cycles a filter tab forward: filterIdx = (filterIdx + 1) % totalTabs.
     */
    public static int cycleFilterTab(int currentTab, int totalTabs) {
        if (totalTabs <= 0) return 0;
        return (currentTab + 1) % totalTabs;
    }

    /**
     * Reads a navigation or menu key event.
     * Decodes ANSI arrow escapes, vim keys (k=UP, j=DOWN, h=LEFT, l=RIGHT),
     * Tab, Enter, Esc, Backspace, digits, and normalized characters.
     */
    public static KeyCode readNavigationKey(NonBlockingReader reader) throws IOException {
        return readKey(reader, false);
    }

    /**
     * Reads a key in active typing state (e.g. Search Bar, Password Input).
     * Prevents key collision by ensuring letters (including j, k, h, l) and digits ('0'-'9')
     * are treated as text characters rather than navigation commands or menu hotkeys.
     */
    public static KeyCode readTypingKey(NonBlockingReader reader) throws IOException {
        return readKey(reader, true);
    }

    /**
     * Centralized parser for raw terminal input streams.
     *
     * @param reader       NonBlockingReader from JLine Terminal
     * @param isTypingMode If true, vim keys (hjkl) and digits are preserved as literal characters
     * @return Decoded KeyCode event
     */
    public static KeyCode readKey(NonBlockingReader reader, boolean isTypingMode) throws IOException {
        int ch = reader.read();
        if (ch == -1) {
            return new KeyCode(KeyType.UNKNOWN, '\0', -1, "");
        }

        if (ch == 27) { // ESC or Escape Sequence
            int next = reader.read(25);
            if (next == -2 || next == -1) {
                return new KeyCode(KeyType.ESCAPE, (char) 27, 27, "\033");
            }
            if (next == '[' || next == 'O') {
                int code = reader.read(25);
                return switch (code) {
                    case 'A' -> new KeyCode(KeyType.UP, 'A', code, "\033[" + (char) code);
                    case 'B' -> new KeyCode(KeyType.DOWN, 'B', code, "\033[" + (char) code);
                    case 'C' -> new KeyCode(KeyType.RIGHT, 'C', code, "\033[" + (char) code);
                    case 'D' -> new KeyCode(KeyType.LEFT, 'D', code, "\033[" + (char) code);
                    case 'Z' -> new KeyCode(KeyType.SHIFT_TAB, 'Z', code, "\033[Z");
                    default  -> new KeyCode(KeyType.UNKNOWN, (char) (code > 0 ? code : 0), code, "\033[" + (char) code);
                };
            }
            return new KeyCode(KeyType.ESCAPE, (char) next, next, "\033" + (char) next);
        } else if (ch == '\t' || ch == 9) {
            return new KeyCode(KeyType.TAB, '\t', 9, "\t");
        } else if (ch == '\r' || ch == '\n') {
            return new KeyCode(KeyType.ENTER, '\n', ch, "\n");
        } else if (ch == 127 || ch == 8) {
            return new KeyCode(KeyType.BACKSPACE, '\b', ch, "\b");
        } else if (ch == 3) { // Ctrl+C
            System.exit(0);
        }

        char rawChar = (char) ch;

        // In navigation mode, map vim navigation keys (k/j/h/l) to arrows
        if (!isTypingMode) {
            if (rawChar == 'k' || rawChar == 'K') {
                return new KeyCode(KeyType.UP, rawChar, ch, String.valueOf(rawChar));
            }
            if (rawChar == 'j' || rawChar == 'J') {
                return new KeyCode(KeyType.DOWN, rawChar, ch, String.valueOf(rawChar));
            }
            if (rawChar == 'h' || rawChar == 'H') {
                return new KeyCode(KeyType.LEFT, rawChar, ch, String.valueOf(rawChar));
            }
            if (rawChar == 'l' || rawChar == 'L') {
                return new KeyCode(KeyType.RIGHT, rawChar, ch, String.valueOf(rawChar));
            }
        }

        if (ch >= '0' && ch <= '9') {
            return new KeyCode(KeyType.DIGIT, rawChar, ch, String.valueOf(rawChar));
        }

        if (ch >= 32 && ch <= 126) {
            return new KeyCode(KeyType.CHAR, rawChar, ch, String.valueOf(rawChar));
        }

        return new KeyCode(KeyType.UNKNOWN, rawChar, ch, String.valueOf(rawChar));
    }

    /**
     * Clamped table row selection index update.
     * Prevents index out of bounds or negative cursor states:
     * selectedIndex = Math.max(0, Math.min(itemsSize - 1, selectedIndex + delta));
     */
    public static int moveSelectionClamped(int currentIndex, int delta, int itemsSize) {
        if (itemsSize <= 0) return 0;
        return Math.max(0, Math.min(itemsSize - 1, currentIndex + delta));
    }

    /**
     * Modulo menu item selection index update.
     */
    public static int moveSelectionModulo(int currentIndex, int delta, int totalOptions) {
        if (totalOptions <= 0) return 0;
        return (currentIndex + delta % totalOptions + totalOptions) % totalOptions;
    }
}
