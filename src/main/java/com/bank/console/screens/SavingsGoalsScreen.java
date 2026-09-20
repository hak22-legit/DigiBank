package com.bank.console.screens;

import com.bank.console.ControllerFactory;
import com.bank.console.ScreenNavigator;
import com.bank.console.TUISession;
import com.bank.console.components.ScreenRenderer;
import com.bank.console.components.TUIBox;
import com.bank.console.components.TUILayout;
import com.bank.console.theme.ConsoleTheme;
import com.bank.controller.AccountController;
import com.bank.controller.SavingGoalController;
import com.bank.model.dto.AccountDTO;
import com.bank.model.dto.UserDTO;
import com.bank.model.entity.SavingGoal;
import com.bank.model.entity.User;
import com.bank.model.enums.GoalStatus;
import com.bank.security.SessionManager;
import com.bank.ui.Ansi;
import org.jline.terminal.Attributes;
import org.jline.terminal.Terminal;
import org.jline.utils.NonBlockingReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * DEDICATED SCREEN: SAVINGS GOALS & VAULTS (82 Columns)
 * Asset accumulation targets, deadlines, direct deposits, and portfolio progress.
 */
public class SavingsGoalsScreen implements Screen {
    private static final Logger logger = LoggerFactory.getLogger(SavingsGoalsScreen.class);
    private static final int PAGE_SIZE = 5;

    private final SavingGoalController savingGoalController;
    private final AccountController accountController;

    private String transientStatus = null;
    private boolean isErrorStatus = false;

    public SavingsGoalsScreen() {
        this(ControllerFactory.getSavingGoalController(),
             ControllerFactory.getAccountController());
    }

    public SavingsGoalsScreen(SavingGoalController savingGoalController) {
        this(savingGoalController, ControllerFactory.getAccountController());
    }

    public SavingsGoalsScreen(SavingGoalController savingGoalController, AccountController accountController) {
        this.savingGoalController = savingGoalController;
        this.accountController = accountController;
    }

    @Override
    public void render(ScreenNavigator navigator, TUISession session) {
        UserDTO userDto = session.getCurrentUser();
        User userEntity = SessionManager.getCurrentUser();
        if (userDto == null || userEntity == null) {
            navigator.pop();
            return;
        }

        int width = TUILayout.APP_WIDTH;
        DecimalFormat df = new DecimalFormat("#,##0.00");
        DateTimeFormatter dfDate = DateTimeFormatter.ofPattern("yyyy-MM-dd");

        Terminal terminal = session.getTerminal();
        Attributes origAttributes = terminal.enterRawMode();
        NonBlockingReader reader = terminal.reader();

        int selectedIndex = 0;
        boolean firstRender = true;
        boolean reloadNeeded = true;
        List<SavingGoal> goals = List.of();

        try {
            while (true) {
                if (reloadNeeded) {
                    try {
                        goals = savingGoalController.getGoalsForUser(userEntity);
                        if (goals == null) goals = List.of();
                    } catch (Exception e) {
                        logger.error("Error loading savings goals", e);
                        goals = List.of();
                    }
                    reloadNeeded = false;
                }

                List<SavingGoal> displayGoals = goals.isEmpty() ? createDefaultGoals(userEntity) : goals;

                // Portfolio Metrics
                BigDecimal totalFundsSaved = BigDecimal.ZERO;
                BigDecimal portfolioTarget = BigDecimal.ZERO;
                int completedGoals = 0;
                int totalGoals = displayGoals.size();

                for (SavingGoal g : displayGoals) {
                    BigDecimal target = g.getTargetAmount() != null ? g.getTargetAmount() : BigDecimal.ZERO;
                    BigDecimal saved = g.getCurrentAmount() != null ? g.getCurrentAmount() : BigDecimal.ZERO;
                    portfolioTarget = portfolioTarget.add(target);
                    totalFundsSaved = totalFundsSaved.add(saved);
                    if (g.getStatus() == GoalStatus.COMPLETED || (target.compareTo(BigDecimal.ZERO) > 0 && saved.compareTo(target) >= 0)) {
                        completedGoals++;
                    }
                }

                double aggregateFunding = (portfolioTarget.compareTo(BigDecimal.ZERO) > 0)
                        ? (totalFundsSaved.doubleValue() / portfolioTarget.doubleValue()) * 100.0
                        : 0.0;

                int maxIdx = Math.max(0, displayGoals.size() - 1);
                selectedIndex = Math.max(0, Math.min(selectedIndex, maxIdx));
                SavingGoal currentSelectedGoal = (!displayGoals.isEmpty() && selectedIndex < displayGoals.size())
                        ? displayGoals.get(selectedIndex) : null;

                // Frame Building
                StringBuilder sb = new StringBuilder();
                sb.append(TUIBox.top(width)).append("\n");
                sb.append(TUIBox.line(ConsoleTheme.primary("DIGIBANK CORE > WEALTH MANAGEMENT > SAVINGS GOALS & VAULTS"), width)).append("\n");
                sb.append(TUIBox.divider(width)).append("\n");

                sb.append(TUIBox.line("TARGET ASSET ACCUMULATION", width)).append("\n");
                sb.append(TUIBox.emptyLine(width)).append("\n");

                // Table Header (78 chars inside box)
                String header = "  GOAL NAME           TARGET         SAVED   DEADLINE         PROGRESS        ";
                sb.append(TUIBox.line(header, width)).append("\n");
                sb.append(TUIBox.line("  " + "─".repeat(76), width)).append("\n");

                for (int i = 0; i < displayGoals.size(); i++) {
                    SavingGoal g = displayGoals.get(i);
                    boolean isSelected = (i == selectedIndex);
                    String prefix = isSelected ? "▸" : " ";

                    String name = g.getName() != null ? g.getName() : "Unnamed";
                    if (name.length() > 15) name = name.substring(0, 15);

                    BigDecimal target = g.getTargetAmount() != null ? g.getTargetAmount() : BigDecimal.ONE;
                    BigDecimal saved = g.getCurrentAmount() != null ? g.getCurrentAmount() : BigDecimal.ZERO;

                    String targetStr = "$ " + String.format("%10s", df.format(target));
                    String savedStr = "$ " + String.format("%10s", df.format(saved));

                    String deadlineStr = g.getDeadline() != null ? g.getDeadline().format(dfDate) : "2026-12-31";

                    int percentage = (target.compareTo(BigDecimal.ZERO) > 0)
                            ? (int) Math.round((saved.doubleValue() / target.doubleValue()) * 100)
                            : 0;
                    percentage = Math.max(0, percentage);

                    // 14-slot progress bar
                    int filledSlots = Math.min(14, (percentage * 14) / 100);
                    filledSlots = Math.max(0, filledSlots);
                    int emptySlots = Math.max(0, 14 - filledSlots);
                    String progressBar = "█".repeat(filledSlots) + "░".repeat(emptySlots);

                    String progStr = String.format("[%s] %3d%%", progressBar, percentage);

                    String row = String.format("%s %-15s %12s  %12s  %-10s  %s",
                            prefix, name, targetStr, savedStr, deadlineStr, progStr);

                    if (row.length() > 78) {
                        row = row.substring(0, 78);
                    } else {
                        row = String.format("%-78s", row);
                    }

                    if (isSelected) {
                        sb.append(TUIBox.line(ConsoleTheme.inlineHighlight(row), width)).append("\n");
                    } else {
                        String coloredBar = (percentage >= 100) ? Ansi.green(progressBar)
                                : (percentage >= 75) ? Ansi.yellow(progressBar)
                                : progressBar;
                        String coloredRow = row.replace("[" + progressBar + "]", "[" + coloredBar + "]");
                        sb.append(TUIBox.line(coloredRow, width)).append("\n");
                    }
                }

                for (int i = displayGoals.size(); i < PAGE_SIZE; i++) {
                    sb.append(TUIBox.emptyLine(width)).append("\n");
                }

                sb.append(TUIBox.divider(width)).append("\n");
                sb.append(TUIBox.line("PORTFOLIO OVERVIEW", width)).append("\n");
                sb.append(TUIBox.emptyLine(width)).append("\n");

                String p1 = String.format("  Total Funds Saved  : $ %s USD", df.format(totalFundsSaved));
                String p2 = String.format("Completed Goals   : %d / %d", completedGoals, totalGoals);
                int sp1 = Math.max(2, 78 - p1.length() - p2.length());
                sb.append(TUIBox.line(p1 + " ".repeat(sp1) + p2, width)).append("\n");

                String p3 = String.format("  Portfolio Target   : $ %s USD", df.format(portfolioTarget));
                String p4 = String.format("Aggregate Funding : %.1f%%", aggregateFunding);
                int sp2 = Math.max(2, 78 - p3.length() - p4.length());
                sb.append(TUIBox.line(p3 + " ".repeat(sp2) + p4, width)).append("\n");

                sb.append(TUIBox.emptyLine(width)).append("\n");
                sb.append(TUIBox.divider(width)).append("\n");

                String currentStatus = (transientStatus != null) ? transientStatus
                        : "Highlight a goal and press [Enter] to allocate funds from checking.";
                String statusDisplay = isErrorStatus ? ConsoleTheme.error(currentStatus) : currentStatus;
                sb.append(TUIBox.line("Status: " + statusDisplay, width)).append("\n");
                sb.append(TUIBox.bottom(width)).append("\n");

                sb.append(Ansi.keyGuide("[↑/↓] Select  •  [Enter] Deposit Funds  •  [E] Edit  •  [N] New Goal  •  [Esc] Back")).append("\n");

                if (firstRender) {
                    System.out.print("\033[H\033[2J");
                    System.out.flush();
                }
                ScreenRenderer.render(sb.toString(), firstRender);
                firstRender = false;

                int ch = reader.read();
                if (ch == -1) break;

                if (ch == 27) { // ESC / Arrow
                    int next1 = reader.read(25);
                    if (next1 == '[' || next1 == 'O') {
                        int next2 = reader.read(25);
                        if (next2 == 'A') { // Up
                            selectedIndex = Math.max(0, selectedIndex - 1);
                            transientStatus = null;
                        } else if (next2 == 'B') { // Down
                            int count = Math.max(1, displayGoals.size());
                            selectedIndex = Math.min(count - 1, selectedIndex + 1);
                            transientStatus = null;
                        }
                    } else if (next1 == -2 || next1 == -1) { // Pure ESC (timeout or EOF)
                        terminal.setAttributes(origAttributes);
                        navigator.pop();
                        return;
                    }
                    continue;
                }

                if (ch == 'w' || ch == 'W' || ch == 'k' || ch == 'K') {
                    selectedIndex = Math.max(0, selectedIndex - 1);
                    transientStatus = null;
                } else if (ch == 's' || ch == 'S' || ch == 'j' || ch == 'J') {
                    int count = Math.max(1, displayGoals.size());
                    selectedIndex = Math.min(count - 1, selectedIndex + 1);
                    transientStatus = null;
                } else if (ch == '\r' || ch == '\n' || ch == 'd' || ch == 'D') { // Enter or D: Deposit
                    if (currentSelectedGoal != null) {
                        SavingGoal targetToFund = currentSelectedGoal;
                        if (targetToFund.getGoalId() == null) {
                            try {
                                SavingGoal created = savingGoalController.createGoal(
                                        userEntity, targetToFund.getName(), targetToFund.getTargetAmount(), targetToFund.getDeadline());
                                if (targetToFund.getCurrentAmount() != null && targetToFund.getCurrentAmount().compareTo(BigDecimal.ZERO) > 0) {
                                    savingGoalController.contribute(created.getGoalId(), targetToFund.getCurrentAmount(), userEntity);
                                }
                                targetToFund = created;
                            } catch (Exception ignored) {}
                        }
                        List<AccountDTO> accounts = List.of();
                        try {
                            accounts = accountController.getAccountsForUser(userEntity);
                        } catch (Exception ignored) {}
                        boolean ok = DepositGoalModal.depositToGoal(
                                terminal, origAttributes, reader, targetToFund, accounts,
                                savingGoalController, accountController, userEntity, width);
                        if (ok) {
                            transientStatus = "Deposited funds to '" + targetToFund.getName() + "' successfully.";
                            isErrorStatus = false;
                            reloadNeeded = true;
                        }
                        firstRender = true;
                    }
                } else if (ch == 'e' || ch == 'E') { // Edit
                    if (currentSelectedGoal != null) {
                        SavingGoal updated = EditGoalModal.editGoal(
                                terminal, origAttributes, reader, currentSelectedGoal,
                                savingGoalController, userEntity, width);
                        if (updated != null) {
                            transientStatus = "Updated goal '" + updated.getName() + "' successfully.";
                            isErrorStatus = false;
                            reloadNeeded = true;
                        }
                        firstRender = true;
                    }
                } else if (ch == 'n' || ch == 'N' || ch == 'c' || ch == 'C') { // New Goal
                    List<AccountDTO> accounts = List.of();
                    try {
                        accounts = accountController.getAccountsForUser(userEntity);
                    } catch (Exception ignored) {}
                    SavingGoal created = CreateGoalModal.createGoal(
                            terminal, origAttributes, reader, accounts,
                            savingGoalController, accountController, userEntity, width);
                    if (created != null) {
                        transientStatus = "Created new goal '" + created.getName() + "' successfully.";
                        isErrorStatus = false;
                        reloadNeeded = true;
                    }
                    firstRender = true;
                } else if (ch == 'b' || ch == 'B') {
                    terminal.setAttributes(origAttributes);
                    navigator.pop();
                    return;
                }
            }
        } catch (IOException e) {
            logger.error("Error reading key on SavingsGoalsScreen", e);
        } finally {
            terminal.setAttributes(origAttributes);
        }
    }

    private List<SavingGoal> createDefaultGoals(User userEntity) {
        List<SavingGoal> defs = new ArrayList<>();
        defs.add(SavingGoal.builder()
                .goalId(1L)
                .userId(userEntity.getUserId())
                .name("GTA6")
                .targetAmount(new BigDecimal("150.00"))
                .currentAmount(new BigDecimal("155.00"))
                .deadline(LocalDate.of(2026, 9, 22))
                .status(GoalStatus.COMPLETED)
                .build());
        defs.add(SavingGoal.builder()
                .goalId(2L)
                .userId(userEntity.getUserId())
                .name("New Laptop")
                .targetAmount(new BigDecimal("1500.00"))
                .currentAmount(BigDecimal.ZERO)
                .deadline(LocalDate.of(2026, 12, 31))
                .status(GoalStatus.ACTIVE)
                .build());
        defs.add(SavingGoal.builder()
                .goalId(3L)
                .userId(userEntity.getUserId())
                .name("iPhone 18")
                .targetAmount(new BigDecimal("2000.00"))
                .currentAmount(new BigDecimal("2000.00"))
                .deadline(LocalDate.of(2026, 11, 30))
                .status(GoalStatus.COMPLETED)
                .build());
        defs.add(SavingGoal.builder()
                .goalId(4L)
                .userId(userEntity.getUserId())
                .name("iPhone Duo")
                .targetAmount(new BigDecimal("2500.00"))
                .currentAmount(new BigDecimal("2000.00"))
                .deadline(LocalDate.of(2026, 12, 1))
                .status(GoalStatus.ACTIVE)
                .build());
        defs.add(SavingGoal.builder()
                .goalId(5L)
                .userId(userEntity.getUserId())
                .name("New Book")
                .targetAmount(new BigDecimal("30.00"))
                .currentAmount(BigDecimal.ZERO)
                .deadline(LocalDate.of(2026, 12, 31))
                .status(GoalStatus.ACTIVE)
                .build());
        return defs;
    }
}
