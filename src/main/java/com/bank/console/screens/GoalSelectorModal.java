package com.bank.console.screens;

import com.bank.console.components.ConsoleFormatter;
import com.bank.console.components.TUIBox;
import com.bank.console.theme.ConsoleTheme;
import com.bank.model.entity.SavingGoal;
import org.jline.terminal.Attributes;
import org.jline.terminal.Terminal;
import org.jline.utils.NonBlockingReader;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.util.List;

/**
 * Interactive 82-column modal selector for Savings Goals.
 */
public class GoalSelectorModal {

    public static final String TITLE = "DIGIBANK CORE > SAVINGS GOALS > SELECT TARGET GOAL";
    public static final String COMPARTMENT_HEADER = "AVAILABLE ACTIVE SAVINGS GOALS";
    public static final String TIP_TEXT = "Tip: Choose the goal you want to contribute funds toward.";
    public static final String FOOTER_FORMAT = " [↑/↓] Navigate  •  [Enter] Confirm  •  [%s] Hotkey  •  [Esc] Cancel";

    public static SavingGoal selectGoal(Terminal terminal, Attributes origAttr, NonBlockingReader reader,
                                        List<SavingGoal> goals, SavingGoal currentSelected, int width) {
        if (goals == null || goals.isEmpty()) {
            return null;
        }

        int selectedIdx = 0;
        if (currentSelected != null) {
            for (int i = 0; i < goals.size(); i++) {
                if (goals.get(i).getGoalId().equals(currentSelected.getGoalId())) {
                    selectedIdx = i;
                    break;
                }
            }
        }

        boolean first = true;
        try {
            while (true) {
                String modal = renderModalContent(goals, selectedIdx, width);
                if (first) {
                    System.out.print(modal);
                    first = false;
                } else {
                    int lineCount = modal.split("\n", -1).length;
                    System.out.print(String.format("\033[%dA\r%s", lineCount - 1, modal));
                }
                System.out.flush();

                int ch = reader.read();
                if (ch == 27) { // ESC or arrow
                    int next = reader.read(60);
                    if (next == -2 || next == -1) {
                        return currentSelected != null ? currentSelected : goals.get(0);
                    }
                    if (next == '[' || next == 'O') {
                        int code = reader.read();
                        if (code == 'A') { // Up
                            selectedIdx = (selectedIdx - 1 + goals.size()) % goals.size();
                        } else if (code == 'B') { // Down
                            selectedIdx = (selectedIdx + 1) % goals.size();
                        }
                    }
                } else if (ch == '\t') {
                    selectedIdx = (selectedIdx + 1) % goals.size();
                } else if (ch == '\r' || ch == '\n') {
                    return goals.get(selectedIdx);
                } else if (ch >= '1' && ch <= '9') {
                    int chosen = ch - '1';
                    if (chosen < goals.size()) {
                        return goals.get(chosen);
                    }
                }
            }
        } catch (Exception e) {
            return currentSelected != null ? currentSelected : goals.get(0);
        }
    }

    public static SavingGoal selectGoal(Terminal terminal, Attributes origAttr, NonBlockingReader reader,
                                        List<SavingGoal> goals, SavingGoal currentSelected) {
        return selectGoal(terminal, origAttr, reader, goals, currentSelected, 82);
    }

    public static String renderModalContent(List<SavingGoal> goals, int selectedIdx, int width) {
        DecimalFormat df = new DecimalFormat("#,##0.00");
        StringBuilder sb = new StringBuilder();
        sb.append(TUIBox.top(width)).append("\n");
        sb.append(TUIBox.line(ConsoleTheme.primary(TITLE), width)).append("\n");
        sb.append(TUIBox.divider(width)).append("\n");
        sb.append(TUIBox.line(COMPARTMENT_HEADER, width)).append("\n");
        sb.append(TUIBox.emptyLine(width)).append("\n");

        for (int i = 0; i < goals.size(); i++) {
            SavingGoal g = goals.get(i);
            BigDecimal target = g.getTargetAmount() != null ? g.getTargetAmount() : BigDecimal.ONE;
            BigDecimal current = g.getCurrentAmount() != null ? g.getCurrentAmount() : BigDecimal.ZERO;
            int pct = (target.compareTo(BigDecimal.ZERO) > 0)
                    ? (int) Math.round(current.doubleValue() / target.doubleValue() * 100)
                    : 0;

            String row = String.format("[%d] %-18s Target: $ %9s  │  Saved: $ %9s (%3d%%)",
                    i + 1, g.getName(), df.format(target), df.format(current), pct);

            if (i == selectedIdx) {
                sb.append(TUIBox.line("  ▸ " + ConsoleTheme.highlight(row), width)).append("\n");
            } else {
                sb.append(TUIBox.line("    " + row, width)).append("\n");
            }
        }

        sb.append(TUIBox.emptyLine(width)).append("\n");
        sb.append(TUIBox.divider(width)).append("\n");
        sb.append(TUIBox.line(TIP_TEXT, width)).append("\n");
        sb.append(TUIBox.bottom(width)).append("\n");

        int maxHotkey = Math.min(goals.size(), 9);
        String hotkeyRange = maxHotkey > 1 ? "1-" + maxHotkey : "1";
        String footer = String.format(FOOTER_FORMAT, hotkeyRange);
        sb.append(ConsoleTheme.keyGuide(footer)).append("\n");

        return sb.toString();
    }
}
