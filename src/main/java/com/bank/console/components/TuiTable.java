package com.bank.console.components;

import com.bank.console.theme.ConsoleTheme;

import java.util.ArrayList;
import java.util.List;

/**
 * High-contrast ASCII Table Component.
 * Engineered strictly for an 82-column layout grid to eliminate horizontal wrapping anomalies.
 * Headers, borders, and rows use adaptive 16-color ANSI codes ensuring legibility
 * across both light and dark terminals.
 */
public class TuiTable {
    public static final int MAX_TABLE_WIDTH = 82;

    public record Column(String header, boolean rightAligned, int minWidth) {}

    private final List<Column> columns = new ArrayList<>();
    private final List<List<String>> rows = new ArrayList<>();

    public TuiTable addColumn(String header, boolean rightAligned) {
        columns.add(new Column(header, rightAligned, 0));
        return this;
    }

    public TuiTable addColumn(String header, int minWidth, boolean rightAligned) {
        columns.add(new Column(header, rightAligned, minWidth));
        return this;
    }

    public TuiTable addRow(Object... values) {
        List<String> row = new ArrayList<>();
        for (Object val : values) {
            row.add(val == null ? "" : val.toString());
        }
        rows.add(row);
        return this;
    }

    public void print() {
        System.out.print(renderToString());
    }

    public String renderToString() {
        if (columns.isEmpty()) return "";

        int colCount = columns.size();
        int[] widths = new int[colCount];

        // 1. Initial widths from headers and column configuration
        for (int i = 0; i < colCount; i++) {
            Column col = columns.get(i);
            int hLen = TUIBox.stripAnsi(col.header()).length();
            widths[i] = Math.max(hLen, col.minWidth());
        }

        // 2. Expand widths based on row data
        for (List<String> row : rows) {
            for (int i = 0; i < Math.min(row.size(), colCount); i++) {
                int cellLen = TUIBox.stripAnsi(row.get(i)).length();
                if (cellLen > widths[i]) {
                    widths[i] = cellLen;
                }
            }
        }

        // 3. Constrain total table width to MAX_TABLE_WIDTH (82 columns)
        // Total width = sum(widths) + 3 * colCount + 1 (for borders and padding)
        int totalWidth = 1;
        for (int w : widths) totalWidth += w + 3;

        if (totalWidth > MAX_TABLE_WIDTH) {
            int excess = totalWidth - MAX_TABLE_WIDTH;
            // Reduce largest columns proportionally
            while (excess > 0) {
                int maxIdx = 0;
                for (int i = 1; i < colCount; i++) {
                    if (widths[i] > widths[maxIdx]) {
                        maxIdx = i;
                    }
                }
                if (widths[maxIdx] > 4) {
                    widths[maxIdx]--;
                    excess--;
                } else {
                    break; // Cannot shrink further
                }
            }
        }

        StringBuilder sb = new StringBuilder();

        // Top Border ┌───┬───┐
        sb.append(renderBorderLine('┌', '┬', '┐', widths)).append("\n");

        // Header Row │ Header 1 │ Header 2 │
        sb.append(ConsoleTheme.border("│"));
        for (int i = 0; i < colCount; i++) {
            Column col = columns.get(i);
            String padded = padCell(col.header(), widths[i], col.rightAligned());
            sb.append(" ").append(ConsoleTheme.BOLD).append(ConsoleTheme.FG_DEFAULT)
              .append(padded).append(ConsoleTheme.RESET).append(" ")
              .append(ConsoleTheme.border("│"));
        }
        sb.append("\n");

        // Header Separator ├───┼───┤
        sb.append(renderBorderLine('├', '┼', '┤', widths)).append("\n");

        // Data Rows
        if (rows.isEmpty()) {
            int innerWidth = 0;
            for (int w : widths) innerWidth += w + 3;
            innerWidth = Math.max(0, innerWidth - 1);
            String emptyMsg = "No records found";
            int padLeft = Math.max(0, (innerWidth - emptyMsg.length()) / 2);
            int padRight = Math.max(0, innerWidth - padLeft - emptyMsg.length());
            sb.append(ConsoleTheme.border("│")).append(" ".repeat(padLeft))
              .append(ConsoleTheme.muted(emptyMsg)).append(" ".repeat(padRight))
              .append(ConsoleTheme.border("│")).append("\n");
        } else {
            for (List<String> row : rows) {
                sb.append(ConsoleTheme.border("│"));
                for (int i = 0; i < colCount; i++) {
                    Column col = columns.get(i);
                    String val = i < row.size() ? row.get(i) : "";
                    String padded = padCell(val, widths[i], col.rightAligned());
                    sb.append(" ").append(padded).append(" ").append(ConsoleTheme.border("│"));
                }
                sb.append("\n");
            }
        }

        // Bottom Border └───┴───┘
        sb.append(renderBorderLine('└', '┴', '┘', widths)).append("\n");

        return sb.toString();
    }

    private String renderBorderLine(char left, char mid, char right, int[] widths) {
        StringBuilder sb = new StringBuilder();
        sb.append(left);
        for (int i = 0; i < widths.length; i++) {
            sb.append(String.valueOf(TUIBox.H).repeat(widths[i] + 2));
            if (i < widths.length - 1) {
                sb.append(mid);
            }
        }
        sb.append(right);
        return ConsoleTheme.border(sb.toString());
    }

    private String padCell(String text, int width, boolean rightAligned) {
        int visibleLen = TUIBox.stripAnsi(text).length();
        if (visibleLen > width) {
            // Truncate cleanly if wider than allocated cell
            String plain = TUIBox.stripAnsi(text);
            return plain.substring(0, Math.max(0, width - 1)) + "…";
        }
        int spaces = Math.max(0, width - visibleLen);
        if (rightAligned) {
            return " ".repeat(spaces) + text;
        } else {
            return text + " ".repeat(spaces);
        }
    }
}
