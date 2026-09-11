package com.bank.console.components;

import com.bank.console.theme.ConsoleTheme;

import java.util.ArrayList;
import java.util.List;

public class ConsoleTable {
    private final List<String> headers = new ArrayList<>();
    private final List<List<String>> rows = new ArrayList<>();
    private final List<Boolean> alignRight = new ArrayList<>();

    public ConsoleTable addColumn(String header, boolean rightAligned) {
        headers.add(header);
        alignRight.add(rightAligned);
        return this;
    }

    public ConsoleTable addRow(Object... values) {
        List<String> row = new ArrayList<>();
        for (Object val : values) {
            row.add(val == null ? "" : val.toString());
        }
        rows.add(row);
        return this;
    }

    public String render() {
        if (headers.isEmpty()) return "";

        int cols = headers.size();
        int[] widths = new int[cols];

        for (int i = 0; i < cols; i++) {
            widths[i] = TUIBox.stripAnsi(headers.get(i)).length();
        }

        for (List<String> row : rows) {
            for (int i = 0; i < Math.min(row.size(), cols); i++) {
                int len = TUIBox.stripAnsi(row.get(i)).length();
                if (len > widths[i]) {
                    widths[i] = len;
                }
            }
        }

        StringBuilder sb = new StringBuilder();

        // Top Border
        appendDivider(sb, '┌', '┬', '┐', widths);

        // Header Row
        sb.append(ConsoleTheme.border("│"));
        for (int i = 0; i < cols; i++) {
            sb.append(" ").append(ConsoleTheme.BOLD).append(ConsoleTheme.FG_DEFAULT)
              .append(pad(headers.get(i), widths[i], alignRight.get(i)))
              .append(ConsoleTheme.RESET).append(" ").append(ConsoleTheme.border("│"));
        }
        sb.append("\n");

        // Divider
        appendDivider(sb, '├', '┼', '┤', widths);

        // Data Rows
        if (rows.isEmpty()) {
            int total = 0;
            for (int w : widths) total += w + 3;
            total = Math.max(0, total - 1);
            String empty = "No records found";
            int padL = Math.max(0, (total - empty.length()) / 2);
            int padR = Math.max(0, total - padL - empty.length());
            sb.append(ConsoleTheme.border("│")).append(" ".repeat(padL))
              .append(ConsoleTheme.muted(empty)).append(" ".repeat(padR))
              .append(ConsoleTheme.border("│")).append("\n");
        } else {
            for (List<String> row : rows) {
                sb.append(ConsoleTheme.border("│"));
                for (int i = 0; i < cols; i++) {
                    String val = i < row.size() ? row.get(i) : "";
                    sb.append(" ").append(pad(val, widths[i], alignRight.get(i))).append(" ").append(ConsoleTheme.border("│"));
                }
                sb.append("\n");
            }
        }

        // Bottom Border
        appendDivider(sb, '└', '┴', '┘', widths);

        return sb.toString();
    }

    public void print() {
        System.out.print(TUILayout.indentLines(render()));
    }

    private void appendDivider(StringBuilder sb, char left, char mid, char right, int[] widths) {
        StringBuilder div = new StringBuilder();
        div.append(left);
        for (int i = 0; i < widths.length; i++) {
            div.append(String.valueOf(TUIBox.H).repeat(widths[i] + 2));
            if (i < widths.length - 1) {
                div.append(mid);
            }
        }
        div.append(right);
        sb.append(ConsoleTheme.border(div.toString())).append("\n");
    }

    private String pad(String s, int width, boolean right) {
        int visibleLen = TUIBox.stripAnsi(s).length();
        int space = Math.max(0, width - visibleLen);
        if (right) {
            return " ".repeat(space) + s;
        } else {
            return s + " ".repeat(space);
        }
    }
}