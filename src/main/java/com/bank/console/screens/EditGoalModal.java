package com.bank.console.screens;

import com.bank.console.components.ScreenRenderer;
import com.bank.console.components.TUIBox;
import com.bank.console.components.TUIFormHelper;
import com.bank.console.components.TUIFormHelper.KeyAction;
import com.bank.console.components.TUIFormHelper.KeyEvent;
import com.bank.console.components.TUILayout;
import com.bank.console.theme.ConsoleTheme;
import com.bank.controller.SavingGoalController;
import com.bank.model.entity.SavingGoal;
import com.bank.model.entity.User;
import org.jline.terminal.Attributes;
import org.jline.terminal.Terminal;
import org.jline.utils.NonBlockingReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * Interactive 82-column modal dialog to edit an existing Savings Goal.
 */
public class EditGoalModal {
    private static final Logger logger = LoggerFactory.getLogger(EditGoalModal.class);
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    public static SavingGoal editGoal(Terminal terminal, Attributes origAttr, NonBlockingReader reader,
                                      SavingGoal goal, SavingGoalController savingGoalController,
                                      User user, int width) {
        if (goal == null) return null;

        DecimalFormat df = new DecimalFormat("#,##0.00");
        StringBuilder titleBuf = new StringBuilder(goal.getName() != null ? goal.getName() : "");
        StringBuilder targetBuf = new StringBuilder(goal.getTargetAmount() != null ? df.format(goal.getTargetAmount()).replace(",", "") : "1000.00");
        StringBuilder dateBuf = new StringBuilder(goal.getDeadline() != null ? goal.getDeadline().format(DATE_FMT) : "2026-12-31");

        // Fields:
        // 0: Goal Title
        // 1: Target Amount
        // 2: Target Deadline
        // 3: Action Bar ([1] Save Changes, [2] Cancel & Return)
        int focusedField = 0;
        int actionIdx = 0; // 0: Save, 1: Cancel

        String statusMessage = "Ready. Edit parameters and select [1] Save Changes.";
        boolean isErrorStatus = false;
        boolean firstRender = true;

        try {
            while (true) {
                BigDecimal targetAmt = parseDecimal(targetBuf.toString(), goal.getTargetAmount());
                LocalDate deadline = parseDate(dateBuf.toString(), goal.getDeadline());

                StringBuilder sb = new StringBuilder();
                sb.append(TUIBox.top(width)).append("\n");
                sb.append(TUIBox.line(ConsoleTheme.primary("DIGIBANK CORE > SAVINGS GOALS > EDIT GOAL"), width)).append("\n");
                sb.append(TUIBox.divider(width)).append("\n");
                sb.append(TUIBox.line("GOAL PARAMETERS", width)).append("\n");
                sb.append(TUIBox.emptyLine(width)).append("\n");

                sb.append(TUIFormHelper.formatFieldRow("Goal Title", titleBuf.toString(), focusedField == 0, 24, 46)).append("\n");
                sb.append(TUIFormHelper.formatFieldRow("Target Amount", "$ " + targetBuf.toString(), focusedField == 1, 24, 46)).append("\n");
                sb.append(TUIFormHelper.formatFieldRow("Deadline Date", dateBuf.toString(), focusedField == 2, 24, 46)).append("\n");
                sb.append(TUIBox.emptyLine(width)).append("\n");

                sb.append(TUIBox.divider(width)).append("\n");
                sb.append(TUIBox.line("ACTION", width)).append("\n");
                sb.append(TUIBox.emptyLine(width)).append("\n");

                String a1 = "[1] Save Changes";
                String a2 = "[2] Cancel & Return";
                String act1 = (focusedField == 3 && actionIdx == 0) ? "▸ " + ConsoleTheme.highlight(a1) : "  " + a1;
                String act2 = (focusedField == 3 && actionIdx == 1) ? "▸ " + ConsoleTheme.highlight(a2) : "  " + a2;
                sb.append(TUIBox.line("  " + act1 + "                    " + act2, width)).append("\n");

                sb.append(TUIBox.divider(width)).append("\n");

                String safeStatus = statusMessage;
                if (safeStatus.length() > 68) {
                    safeStatus = safeStatus.substring(0, 65) + "...";
                }
                String statusLine = isErrorStatus ? ConsoleTheme.error(safeStatus) : safeStatus;
                sb.append(TUIBox.line("Status: " + statusLine, width)).append("\n");
                sb.append(TUIBox.bottom(width)).append("\n");
                sb.append(ConsoleTheme.keyGuide("[Tab/↓] Next Field  •  [Enter] Confirm  •  [1/2] Action  •  [Esc] Cancel")).append("\n");

                ScreenRenderer.render(sb.toString(), firstRender);
                firstRender = false;

                KeyEvent event = TUIFormHelper.readKey(reader);
                if (event.action() == KeyAction.ESCAPE) {
                    return null;
                } else if (event.action() == KeyAction.TAB || event.action() == KeyAction.DOWN) {
                    focusedField = (focusedField + 1) % 4;
                } else if (event.action() == KeyAction.SHIFT_TAB || event.action() == KeyAction.UP) {
                    focusedField = (focusedField - 1 + 4) % 4;
                } else if (focusedField == 3 && (event.action() == KeyAction.LEFT || event.action() == KeyAction.RIGHT)) {
                    actionIdx = (actionIdx == 0) ? 1 : 0;
                } else if (event.action() == KeyAction.BACKSPACE) {
                    if (focusedField == 0 && titleBuf.length() > 0) titleBuf.deleteCharAt(titleBuf.length() - 1);
                    else if (focusedField == 1 && targetBuf.length() > 0) targetBuf.deleteCharAt(targetBuf.length() - 1);
                    else if (focusedField == 2 && dateBuf.length() > 0) dateBuf.deleteCharAt(dateBuf.length() - 1);
                } else if (event.action() == KeyAction.ENTER) {
                    if (focusedField == 3) {
                        if (actionIdx == 0) {
                            SavingGoal updated = executeSave(goal.getGoalId(), titleBuf.toString(), targetAmt, deadline, user, savingGoalController);
                            if (updated != null) return updated;
                            statusMessage = "Validation failed: Check goal title and target amount.";
                            isErrorStatus = true;
                        } else {
                            return null;
                        }
                    } else {
                        focusedField = (focusedField + 1) % 4;
                    }
                } else if (event.action() == KeyAction.DIGIT || event.action() == KeyAction.CHAR) {
                    char c = event.ch();
                    if (focusedField == 0) {
                        if (titleBuf.length() < 30) titleBuf.append(c);
                    } else if (focusedField == 1) {
                        if ((c >= '0' && c <= '9') || (c == '.' && !targetBuf.toString().contains("."))) {
                            if (targetBuf.length() < 10) targetBuf.append(c);
                        }
                    } else if (focusedField == 2) {
                        if ((c >= '0' && c <= '9') || c == '-') {
                            if (dateBuf.length() < 10) dateBuf.append(c);
                        }
                    } else if (focusedField == 3) {
                        if (c == '1') {
                            SavingGoal updated = executeSave(goal.getGoalId(), titleBuf.toString(), targetAmt, deadline, user, savingGoalController);
                            if (updated != null) return updated;
                            statusMessage = "Validation failed: Check goal title and target amount.";
                            isErrorStatus = true;
                        } else if (c == '2') {
                            return null;
                        }
                    }
                }
            }
        } catch (Exception e) {
            logger.error("Error in EditGoalModal", e);
            return null;
        }
    }

    public static SavingGoal editGoal(Terminal terminal, Attributes origAttr, NonBlockingReader reader,
                                      SavingGoal goal, SavingGoalController savingGoalController, User user) {
        return editGoal(terminal, origAttr, reader, goal, savingGoalController, user, TUILayout.APP_WIDTH);
    }

    private static SavingGoal executeSave(Long goalId, String title, BigDecimal target, LocalDate deadline,
                                          User user, SavingGoalController controller) {
        if (title == null || title.trim().isEmpty()) return null;
        if (target == null || target.compareTo(BigDecimal.ZERO) <= 0) return null;
        try {
            if (goalId == null) {
                return controller.createGoal(user, title.trim(), target, deadline);
            }
            return controller.updateGoal(goalId, title, target, deadline, user);
        } catch (Exception e) {
            logger.error("Failed to update goal", e);
            return null;
        }
    }

    private static BigDecimal parseDecimal(String s, BigDecimal def) {
        try {
            return new BigDecimal(s.replace(",", "").replace("$", "").trim());
        } catch (Exception e) {
            return def;
        }
    }

    private static LocalDate parseDate(String s, LocalDate def) {
        try {
            return LocalDate.parse(s.trim());
        } catch (Exception e) {
            return def;
        }
    }
}
