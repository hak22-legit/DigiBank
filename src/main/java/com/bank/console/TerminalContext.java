package com.bank.console;

import com.bank.console.theme.ConsoleTheme;
import org.jline.reader.LineReader;
import org.jline.reader.LineReaderBuilder;
import org.jline.terminal.Attributes;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;
import org.jline.utils.NonBlockingReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

/**
 * Terminal Context & Hardware Engine.
 * Configures JLine 3 terminal, handles PTY / dumb terminal fallback, manages POSIX signals,
 * provides non-blocking raw key capturing (arrows, enter, escape), and generates
 * theme-safe ANSI sequences that are legible on both light and dark terminals.
 */
public class TerminalContext implements AutoCloseable {
    private static final Logger logger = LoggerFactory.getLogger(TerminalContext.class);
    private static TerminalContext instance;

    public static final int DEFAULT_GRID_WIDTH = 82;

    private final Terminal terminal;
    private final LineReader lineReader;

    public enum Key {
        UP,
        DOWN,
        LEFT,
        RIGHT,
        ENTER,
        ESCAPE,
        BACKSPACE,
        DIGIT,
        CHAR,
        UNKNOWN
    }

    public record InputEvent(Key key, char ch, int code) {}

    private TerminalContext() {
        Terminal t = null;
        try {
            t = TerminalBuilder.builder()
                    .system(true)
                    .dumb(true)
                    .encoding(java.nio.charset.StandardCharsets.UTF_8)
                    .build();
        } catch (IOException e) {
            logger.warn("Unable to initialize system terminal, falling back to default dumb terminal: {}", e.getMessage());
            try {
                t = TerminalBuilder.builder()
                        .dumb(true)
                        .build();
            } catch (IOException ex) {
                throw new IllegalStateException("Failed to initialize any terminal context", ex);
            }
        }
        this.terminal = t;

        // Configure signal handlers
        try {
            this.terminal.handle(Terminal.Signal.INT, sig -> {
                logger.info("Received SIGINT, terminating cleanly...");
                System.out.println("\n" + ConsoleTheme.muted("Session terminated by user. Goodbye!"));
                System.exit(0);
            });
            this.terminal.handle(Terminal.Signal.WINCH, sig -> {
                // Window resize signal: terminal dimensions updated dynamically
            });
        } catch (Exception ignored) {
            // Signal handling might not be supported on some platforms/dumb terminals
        }

        this.lineReader = LineReaderBuilder.builder()
                .terminal(terminal)
                .build();
    }

    public static synchronized TerminalContext getInstance() {
        if (instance == null) {
            instance = new TerminalContext();
        }
        return instance;
    }

    public Terminal getTerminal() {
        return terminal;
    }

    public LineReader getLineReader() {
        return lineReader;
    }

    public int getWidth() {
        int w = terminal.getWidth();
        return (w > 0) ? Math.min(w, DEFAULT_GRID_WIDTH) : DEFAULT_GRID_WIDTH;
    }

    public int getTerminalWidth() {
        int w = terminal != null ? terminal.getWidth() : 0;
        return (w > 0) ? w : DEFAULT_GRID_WIDTH;
    }

    public int getTerminalHeight() {
        int h = terminal != null ? terminal.getHeight() : 0;
        return (h > 0) ? h : 24;
    }

    public void clearScreen() {
        terminal.puts(org.jline.utils.InfoCmp.Capability.clear_screen);
        terminal.flush();
        System.out.print(ConsoleTheme.CLEAR_SCREEN);
        System.out.flush();
    }

    /**
     * Reads a single raw keyboard event (arrow keys, enter, esc, digits) without requiring Enter.
     */
    public InputEvent readRawKey() {
        Attributes origAttributes = null;
        try {
            origAttributes = terminal.enterRawMode();
            NonBlockingReader reader = terminal.reader();

            int ch = reader.read();
            if (ch == -1) {
                return new InputEvent(Key.UNKNOWN, '\0', -1);
            }

            if (ch == 27) { // ESC or Escape Sequence
                int next = reader.read(25);
                if (next == -2 || next == -1) {
                    return new InputEvent(Key.ESCAPE, (char) 27, 27);
                }
                if (next == '[' || next == 'O') {
                    int code = reader.read(25);
                    return switch (code) {
                        case 'A' -> new InputEvent(Key.UP, 'A', code);
                        case 'B' -> new InputEvent(Key.DOWN, 'B', code);
                        case 'C' -> new InputEvent(Key.RIGHT, 'C', code);
                        case 'D' -> new InputEvent(Key.LEFT, 'D', code);
                        default -> new InputEvent(Key.UNKNOWN, (char) code, code);
                    };
                }
                return new InputEvent(Key.ESCAPE, (char) next, next);
            } else if (ch == '\r' || ch == '\n') {
                return new InputEvent(Key.ENTER, '\n', ch);
            } else if (ch == 127 || ch == 8) {
                return new InputEvent(Key.BACKSPACE, '\b', ch);
            } else if (ch >= '0' && ch <= '9') {
                return new InputEvent(Key.DIGIT, (char) ch, ch);
            } else if (ch >= 32 && ch <= 126) {
                return new InputEvent(Key.CHAR, (char) ch, ch);
            } else if (ch == 3) { // Ctrl+C
                System.exit(0);
            }

            return new InputEvent(Key.UNKNOWN, (char) ch, ch);

        } catch (IOException e) {
            logger.error("Error reading raw key: {}", e.getMessage());
            return new InputEvent(Key.UNKNOWN, '\0', -1);
        } finally {
            if (origAttributes != null) {
                terminal.setAttributes(origAttributes);
            }
        }
    }

    // =========================================================
    // Theme Helper Methods for Adaptive Light/Dark Contrast
    // =========================================================

    public String reset() {
        return ConsoleTheme.RESET;
    }

    public String highlight(String text) {
        return ConsoleTheme.highlight(text);
    }

    public String inlineHighlight(String text) {
        return ConsoleTheme.inlineHighlight(text);
    }

    public String dim(String text) {
        return ConsoleTheme.muted(text);
    }

    public String success(String text) {
        return ConsoleTheme.success(text);
    }

    public String error(String text) {
        return ConsoleTheme.error(text);
    }

    public String warning(String text) {
        return ConsoleTheme.warning(text);
    }

    public String info(String text) {
        return ConsoleTheme.info(text);
    }

    public String primary(String text) {
        return ConsoleTheme.primary(text);
    }

    public String border(String text) {
        return ConsoleTheme.border(text);
    }

    public String stripAnsi(String text) {
        if (text == null) return "";
        return text.replaceAll("\u001B\\[[;?0-9]*[a-zA-Z]", "");
    }

    @Override
    public void close() {
        try {
            if (terminal != null) {
                terminal.close();
            }
        } catch (IOException ignored) {}
    }
}
