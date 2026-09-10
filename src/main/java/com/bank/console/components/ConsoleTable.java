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

    public void print() {
        if (headers.isEmpty()) return;

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

        // Top Border
        printDivider('┌', '┬', '┐', widths);

        // Header Row
        System.out.print(ConsoleTheme.border("│"));
        for (int i = 0; i < cols; i++) {
            System.out.print(" " + ConsoleTheme.BOLD + ConsoleTheme.FG_DEFAULT +
                    pad(headers.get(i), widths[i], alignRight.get(i)) + ConsoleTheme.RESET + " " + ConsoleTheme.border("│"));
        }
        System.out.println();

        // Divider
        printDivider('├', '┼', '┤', widths);

        // Data Rows
        if (rows.isEmpty()) {
            int total = 0;
            for (int w : widths) total += w + 3;
            total = Math.max(0, total - 1);
            String empty = "No records found";
            int padL = Math.max(0, (total - empty.length()) / 2);
            int padR = Math.max(0, total - padL - empty.length());
            System.out.println(ConsoleTheme.border("│") + " ".repeat(padL) + ConsoleTheme.muted(empty) + " ".repeat(padR) + ConsoleTheme.border("│"));
        } else {
            for (List<String> row : rows) {
                System.out.print(ConsoleTheme.border("│"));
                for (int i = 0; i < cols; i++) {
                    String val = i < row.size() ? row.get(i) : "";
                    System.out.print(" " + pad(val, widths[i], alignRight.get(i)) + " " + ConsoleTheme.border("│"));
                }
                System.out.println();
            }
        }

        // Bottom Border
        printDivider('└', '┴', '┘', widths);
    }

    private void printDivider(char left, char mid, char right, int[] widths) {
        StringBuilder sb = new StringBuilder();
        sb.append(left);
        for (int i = 0; i < widths.length; i++) {
            sb.append(String.valueOf(TUIBox.H).repeat(widths[i] + 2));
            if (i < widths.length - 1) {
                sb.append(mid);
            }
        }
        sb.append(right);
        System.out.println(ConsoleTheme.border(sb.toString()));
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