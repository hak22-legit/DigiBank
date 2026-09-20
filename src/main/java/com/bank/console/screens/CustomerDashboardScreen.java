package com.bank.console.screens;

import com.bank.console.ControllerFactory;
import com.bank.console.ScreenNavigator;
import com.bank.console.TUISession;
import com.bank.console.components.ConsoleFormatter;
import com.bank.console.components.ScreenRenderer;
import com.bank.console.components.TUIBox;
import com.bank.console.components.TUILayout;
import com.bank.console.theme.ConsoleTheme;
import com.bank.controller.AccountController;
import com.bank.controller.BudgetController;
import com.bank.controller.FinancialController;
import com.bank.controller.LoanController;
import com.bank.controller.SavingGoalController;
import com.bank.model.BudgetView;
import com.bank.model.dto.AccountDTO;
import com.bank.model.dto.LoanDTO;
import com.bank.model.dto.UserDTO;
import com.bank.model.entity.SavingGoal;
import com.bank.model.entity.User;
import com.bank.model.enums.Currency;
import com.bank.model.enums.GoalStatus;
import com.bank.model.enums.LoanStatus;
import com.bank.security.SessionManager;
import com.bank.ui.Ansi;
import com.bank.console.components.TUIFormHelper;
import com.bank.console.components.TUIFormHelper.KeyAction;
import com.bank.console.components.TUIFormHelper.KeyEvent;
import org.jline.terminal.Attributes;
import org.jline.terminal.Terminal;
import org.jline.utils.NonBlockingReader;

import java.io.IOException;
import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.util.List;

/**
 * SCREEN 5: CUSTOMER DASHBOARD (82 Columns)
 * In-place keyboard navigation, 2-column balanced navigation layout,
 * dedicated account creation integration, KHR currency alignment fix,
 * and cached data model for instant reflection.
 */
public class CustomerDashboardScreen implements Screen {
    private final AccountController accountController;
    private final FinancialController financialController;
    private final BudgetController budgetController;
    private final SavingGoalController savingGoalController;
    private final LoanController loanController;

    private String statusMessage = null;

    private static final String[] NAV_ITEMS = {
            "[1] Instant Transfer (P2P/Wire)",
            "[2] Cash Deposit / Withdrawal",
            "[3] Currency Exchange & FX Rates",
            "[4] Statements & Ledger History",
            "[5] Loan Management & Installments",
            "[6] Budgets & Saving Goals",
            "[7] Open New Bank Account",
            "[8] Log Out (Session Exit)"
    };

    private static final String[] CASH_SUB_ITEMS = {
            "[1] Cash Deposit (Deposit funds to checking/savings)",
            "[2] Cash Withdrawal (Withdraw funds from account)",
            "[0] Back to Main Navigation"
    };

    private record DashboardData(
            List<AccountDTO> accounts,
            String loansOverview,
            String goalsOverview,
            String budgetOverview
    ) {}

    public CustomerDashboardScreen() {
        this(ControllerFactory.getAccountController(),
             ControllerFactory.getFinancialController(),
             ControllerFactory.getBudgetController(),
             ControllerFactory.getSavingGoalController(),
             ControllerFactory.getLoanController());
    }

    public CustomerDashboardScreen(AccountController accountController,
                                   FinancialController financialController,
                                   BudgetController budgetController,
                                   SavingGoalController savingGoalController,
                                   LoanController loanController) {
        this.accountController = accountController;
        this.financialController = financialController;
        this.budgetController = budgetController;
        this.savingGoalController = savingGoalController;
        this.loanController = loanController;
    }

    public void setStatusMessage(String statusMessage) {
        this.statusMessage = statusMessage;
    }

    private DashboardData loadDashboardData(User userEntity) {
        List<AccountDTO> accounts;
        try {
            accounts = accountController.getAccountsForUser(userEntity);
        } catch (Exception e) {
            accounts = List.of();
        }

        DecimalFormat df = new DecimalFormat("#,##0.00");

        // Loans Overview
        String loansOverview = "0 Loans ($0.00 Outstanding)";
        try {
            List<LoanDTO> loans = loanController.getUserLoans(userEntity);
            List<LoanDTO> activeLoans = loans.stream()
                    .filter(l -> l.getStatus() == LoanStatus.ACTIVE && l.getOutstandingBalance() != null && l.getOutstandingBalance().compareTo(BigDecimal.ZERO) > 0)
                    .toList();
            if (!activeLoans.isEmpty()) {
                LoanDTO first = activeLoans.get(0);
                loansOverview = String.format("%d Loan%s ($%s Outstanding | Next Due: 2026-09-25)",
                        activeLoans.size(), activeLoans.size() > 1 ? "s" : "", df.format(first.getOutstandingBalance()));
            }
        } catch (Exception ignored) {}

        // Goals Overview
        String goalsOverview = "0 Goals ($0.00 / $0.00 Target)";
        try {
            List<SavingGoal> goals = savingGoalController.getGoalsForUser(userEntity);
            List<SavingGoal> activeGoals = goals.stream().filter(g -> g.getStatus() == GoalStatus.ACTIVE).toList();
            if (!activeGoals.isEmpty()) {
                BigDecimal totalCurrent = activeGoals.stream()
                        .map(g -> g.getCurrentAmount() != null ? g.getCurrentAmount() : BigDecimal.ZERO)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
                BigDecimal totalTarget = activeGoals.stream()
                        .map(g -> g.getTargetAmount() != null ? g.getTargetAmount() : BigDecimal.ZERO)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
                goalsOverview = String.format("%d Goal%s ($%s / $%s Target)",
                        activeGoals.size(), activeGoals.size() > 1 ? "s" : "", df.format(totalCurrent), df.format(totalTarget));
            }
        } catch (Exception ignored) {}

        // Budget Overview
        String budgetOverview = "$0.00 spent of $200.00 limit";
        try {
            List<BudgetView> budgets = budgetController.getBudgetsWithUsage(userEntity);
            if (budgets != null && !budgets.isEmpty()) {
                BigDecimal totalSpent = budgets.stream()
                        .map(b -> b.getActualSpending() != null ? b.getActualSpending() : BigDecimal.ZERO)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
                BigDecimal totalLimit = budgets.stream()
                        .map(b -> b.getBudget().getAmountLimit() != null ? b.getBudget().getAmountLimit() : BigDecimal.ZERO)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
                budgetOverview = String.format("$%s spent of $%s limit", df.format(totalSpent), df.format(totalLimit));
            }
        } catch (Exception ignored) {}

        return new DashboardData(accounts, loansOverview, goalsOverview, budgetOverview);
    }

    @Override
    public void render(ScreenNavigator navigator, TUISession session) {
        UserDTO userDto = session.getCurrentUser();
        User userEntity = SessionManager.getCurrentUser();

        if (userDto == null || userEntity == null) {
            navigator.clearAndPush(new WelcomeScreen());
            return;
        }

        int selectedIndex = 0;
        boolean inCashSubMenu = false;
        int cashSubIndex = 0;

        Terminal terminal = session.getTerminal();
        Attributes origAttributes = terminal.enterRawMode();
        NonBlockingReader reader = terminal.reader();

        DashboardData data = loadDashboardData(userEntity);
        boolean firstRender = true;

        try {
            while (true) {
                renderScreen(session, userDto, data, selectedIndex, inCashSubMenu, cashSubIndex, firstRender);
                firstRender = false;

                KeyEvent event = TUIFormHelper.readKey(reader);
                if (event.action() == KeyAction.ESCAPE) {
                    if (inCashSubMenu) {
                        inCashSubMenu = false;
                        continue;
                    } else {
                        terminal.setAttributes(origAttributes);
                        executeAction(0, navigator, session);
                        return;
                    }
                } else if (event.action() == KeyAction.UP || (event.action() == KeyAction.CHAR && (event.ch() == 'k' || event.ch() == 'K'))) {
                    if (inCashSubMenu) {
                        cashSubIndex = (cashSubIndex - 1 + 3) % 3;
                    } else {
                        if (selectedIndex == 0 || selectedIndex == 4) selectedIndex = 8;
                        else if (selectedIndex == 8) selectedIndex = 3;
                        else selectedIndex--;
                    }
                } else if (event.action() == KeyAction.DOWN || (event.action() == KeyAction.CHAR && (event.ch() == 'j' || event.ch() == 'J'))) {
                    if (inCashSubMenu) {
                        cashSubIndex = (cashSubIndex + 1) % 3;
                    } else {
                        if (selectedIndex == 3 || selectedIndex == 7) selectedIndex = 8;
                        else if (selectedIndex == 8) selectedIndex = 0;
                        else selectedIndex++;
                    }
                } else if (event.action() == KeyAction.LEFT || (event.action() == KeyAction.CHAR && (event.ch() == 'h' || event.ch() == 'H'))) {
                    if (!inCashSubMenu && selectedIndex >= 4 && selectedIndex < 8) {
                        selectedIndex -= 4;
                    }
                } else if (event.action() == KeyAction.RIGHT || (event.action() == KeyAction.CHAR && (event.ch() == 'l' || event.ch() == 'L'))) {
                    if (!inCashSubMenu && selectedIndex < 4) {
                        selectedIndex += 4;
                    }
                } else if (event.action() == KeyAction.TAB) {
                    if (inCashSubMenu) {
                        cashSubIndex = (cashSubIndex + 1) % 3;
                    } else {
                        selectedIndex = (selectedIndex + 1) % 9;
                    }
                } else if (event.action() == KeyAction.SHIFT_TAB) {
                    if (inCashSubMenu) {
                        cashSubIndex = (cashSubIndex - 1 + 3) % 3;
                    } else {
                        selectedIndex = (selectedIndex - 1 + 9) % 9;
                    }
                } else if (event.action() == KeyAction.ENTER) {
                    if (inCashSubMenu) {
                        terminal.setAttributes(origAttributes);
                        if (cashSubIndex == 0) {
                            navigator.push(new DepositScreen());
                        } else if (cashSubIndex == 1) {
                            navigator.push(new WithdrawScreen());
                        } else {
                            inCashSubMenu = false;
                            origAttributes = terminal.enterRawMode();
                            continue;
                        }
                        return;
                    } else {
                        if (selectedIndex == 1) {
                            // Toggle in-place Cash Submenu
                            inCashSubMenu = true;
                            cashSubIndex = 0;
                        } else if (selectedIndex == 8) {
                            terminal.setAttributes(origAttributes);
                            executeAction(0, navigator, session);
                            return;
                        } else {
                            terminal.setAttributes(origAttributes);
                            executeAction(selectedIndex + 1, navigator, session);
                            return;
                        }
                    }
                } else if (!inCashSubMenu && event.action() == KeyAction.DIGIT && event.ch() >= '0' && event.ch() <= '8') {
                    if (event.ch() == '0') {
                        terminal.setAttributes(origAttributes);
                        executeAction(0, navigator, session);
                        return;
                    } else if (event.ch() == '2') {
                        inCashSubMenu = true;
                        cashSubIndex = 0;
                    } else {
                        terminal.setAttributes(origAttributes);
                        executeAction(event.ch() - '0', navigator, session);
                        return;
                    }
                } else if (inCashSubMenu && event.action() == KeyAction.DIGIT && (event.ch() == '1' || event.ch() == '2' || event.ch() == '0')) {
                    terminal.setAttributes(origAttributes);
                    if (event.ch() == '1') {
                        navigator.push(new DepositScreen());
                        return;
                    } else if (event.ch() == '2') {
                        navigator.push(new WithdrawScreen());
                        return;
                    } else {
                        inCashSubMenu = false;
                        origAttributes = terminal.enterRawMode();
                    }
                } else if (event.action() == KeyAction.CHAR && (event.ch() == 'r' || event.ch() == 'R')) {
                    // Manual refresh hotkey
                    data = loadDashboardData(userEntity);
                } else if (!inCashSubMenu && event.action() == KeyAction.CHAR && (event.ch() == 'q' || event.ch() == 'Q')) {
                    terminal.setAttributes(origAttributes);
                    executeAction(0, navigator, session);
                    return;
                }
            }
        } catch (IOException e) {
            executeAction(selectedIndex + 1, navigator, session);
        } finally {
            terminal.setAttributes(origAttributes);
        }
    }

    private static String renderGridItem(int key, String label, boolean isSelected) {
        String prefix = isSelected ? "▸ " : "  ";
        String cellText = String.format("%s[%d] %s", prefix, key, label);
        // Pad cell to exact 36 characters
        cellText = String.format("%-36s", cellText);

        if (isSelected) {
            return "\033[7m" + cellText + "\033[0m";
        } else {
            return cellText.replace("[" + key + "]", Ansi.yellow("[" + key + "]"));
        }
    }

    private static String renderCashOption(int key, String label, boolean isSelected, int width) {
        String prefix = isSelected ? "▸ " : "  ";
        String content = String.format("%s[%d] %s", prefix, key, label);
        content = String.format("%-74s", content);

        if (isSelected) {
            return TUIBox.line(" \033[7m" + content + "\033[0m", width);
        } else {
            return TUIBox.line(" " + content.replace("[" + key + "]", Ansi.yellow("[" + key + "]")), width);
        }
    }

    private static String renderCenteredOption(int key, String label, boolean isSelected, int width) {
        String prefix = isSelected ? "▸ " : "  ";
        String content = String.format("%s[%d] %s", prefix, key, label);
        int padTotal = Math.max(0, 74 - content.length());
        int padLeft = padTotal / 2;
        int padRight = padTotal - padLeft;
        String line = " ".repeat(padLeft) + content + " ".repeat(padRight);

        if (isSelected) {
            return TUIBox.line(" \033[7m" + line + "\033[0m", width);
        } else {
            return TUIBox.line(" " + line.replace("[" + key + "]", Ansi.yellow("[" + key + "]")), width);
        }
    }

    private void renderScreen(TUISession session, UserDTO userDto, DashboardData data,
                              int selectedIndex, boolean inCashSubMenu, int cashSubIndex, boolean firstRender) {
        StringBuilder sb = new StringBuilder();
        int width = TUILayout.APP_WIDTH;
        DecimalFormat df = new DecimalFormat("#,##0.00");
        DecimalFormat intFormat = new DecimalFormat("#,##0");

        // Header Line
        String rawHeaderTitle = String.format("DIGIBANK CORE │ USER: %s (#USR-%d) │ STATUS: %s",
                userDto.getFullName().toUpperCase(), userDto.getUserId(), "ACTIVE");
        String headerTitle = rawHeaderTitle.replace("ACTIVE", Ansi.green("ACTIVE"));

        sb.append(TUIBox.top(width)).append("\n");
        sb.append(TUIBox.line(ConsoleTheme.primary(headerTitle), width)).append("\n");
        sb.append(TUIBox.divider(width)).append("\n");

        // 1. MY ACCOUNTS
        sb.append(TUIBox.twoColumns("MY ACCOUNTS", "[+] OPEN ACCOUNT", width)).append("\n");
        sb.append(TUIBox.emptyLine(width)).append("\n");
        sb.append(TUIBox.line("  ACCOUNT NUMBER   TYPE       CURRENCY         BALANCE   STATUS", width)).append("\n");
        sb.append(TUIBox.line(" " + "─".repeat(77), width)).append("\n");

        List<AccountDTO> accounts = data.accounts();
        if (accounts == null || accounts.isEmpty()) {
            sb.append(TUIBox.line("  No active accounts registered.", width)).append("\n");
        } else {
            for (int i = 0; i < accounts.size(); i++) {
                AccountDTO acc = accounts.get(i);
                boolean isPrimary = (i == 0);
                boolean isKhr = (acc.getCurrency() != null && acc.getCurrency().name().equalsIgnoreCase("KHR"));
                String symbol = isKhr ? "៛" : "$";
                String formattedAmt = isKhr ? intFormat.format(acc.getBalance()) : df.format(acc.getBalance());
                String balStr = String.format("%s %10s   ", symbol, formattedAmt);

                String accNum = isPrimary ? (acc.getAccountNumber() + " ★") : acc.getAccountNumber();
                String rawRow = String.format("  %-17s%-11s%-12s%s%-8s",
                        accNum,
                        acc.getAccountType(),
                        acc.getCurrency(),
                        balStr,
                        acc.getStatus());

                String renderedRow = rawRow;
                if (isPrimary) {
                    renderedRow = renderedRow.replace("★", Ansi.yellow("★"));
                }
                if (acc.getStatus() != null && "ACTIVE".equalsIgnoreCase(acc.getStatus().name())) {
                    renderedRow = renderedRow.replace("ACTIVE", Ansi.green("ACTIVE"));
                }
                renderedRow = renderedRow.replace(balStr, Ansi.cyan(balStr));

                sb.append(TUIBox.line(renderedRow, width)).append("\n");
            }
        }

        sb.append(TUIBox.divider(width)).append("\n");

        // 2. FINANCIAL OVERVIEW
        sb.append(TUIBox.line("FINANCIAL OVERVIEW", width)).append("\n");
        sb.append(TUIBox.emptyLine(width)).append("\n");
        sb.append(TUIBox.line("  Active Loans  : " + Ansi.cyan(data.loansOverview()), width)).append("\n");
        sb.append(TUIBox.line("  Savings Goals : " + Ansi.cyan(data.goalsOverview()), width)).append("\n");
        sb.append(TUIBox.line("  Monthly Budget: " + Ansi.cyan(data.budgetOverview()), width)).append("\n");

        sb.append(TUIBox.divider(width)).append("\n");

        // 3. SERVICES & NAVIGATION (OR IN-PLACE CASH SUBMENU)
        if (inCashSubMenu) {
            sb.append(TUIBox.line("CASH OPERATIONS", width)).append("\n");
            sb.append(TUIBox.emptyLine(width)).append("\n");
            sb.append(renderCashOption(1, "Cash Deposit", cashSubIndex == 0, width)).append("\n");
            sb.append(renderCashOption(2, "Cash Withdrawal", cashSubIndex == 1, width)).append("\n");
            sb.append(TUIBox.emptyLine(width)).append("\n");
            sb.append(renderCenteredOption(0, "Back to Main Navigation", cashSubIndex == 2, width)).append("\n");
        } else {
            sb.append(TUIBox.line("SERVICES & NAVIGATION", width)).append("\n");
            sb.append(TUIBox.emptyLine(width)).append("\n");

            // Two-Column Grid: Col 1 (36 chars) + 2 spaces + Col 2 (36 chars) = 74 printable chars
            String row0 = renderGridItem(1, "Transfer Funds", selectedIndex == 0) + "  " + renderGridItem(5, "Loan Facilities", selectedIndex == 4);
            sb.append(TUIBox.line("  " + row0, width)).append("\n");

            String row1 = renderGridItem(2, "Deposit & Withdrawal", selectedIndex == 1) + "  " + renderGridItem(6, "Monthly Expense Budgets", selectedIndex == 5);
            sb.append(TUIBox.line("  " + row1, width)).append("\n");

            String row2 = renderGridItem(3, "Exchange Rates (FX)", selectedIndex == 2) + "  " + renderGridItem(7, "Wealth & Savings Goals", selectedIndex == 6);
            sb.append(TUIBox.line("  " + row2, width)).append("\n");

            String row3 = renderGridItem(4, "Statements & History", selectedIndex == 3) + "  " + renderGridItem(8, "Open New Account", selectedIndex == 7);
            sb.append(TUIBox.line("  " + row3, width)).append("\n");

            sb.append(TUIBox.emptyLine(width)).append("\n");
            sb.append(renderCenteredOption(0, "Sign Out & Terminate Session", selectedIndex == 8, width)).append("\n");
        }

        sb.append(TUIBox.divider(width)).append("\n");

        // Status Line
        String currentStatus;
        if (inCashSubMenu) {
            currentStatus = (statusMessage != null) ? statusMessage : "Select cash operation. Press [1] for deposit or [2] for withdrawal.";
        } else {
            currentStatus = (statusMessage != null) ? statusMessage : "Ready. All accounts operational.";
        }
        sb.append(TUIBox.line("Status: " + currentStatus, width)).append("\n");

        sb.append(TUIBox.bottom(width)).append("\n");
        if (inCashSubMenu) {
            sb.append(ConsoleTheme.keyGuide("[↑/↓] Navigate  •  [Enter] Select  •  [1-2, 0] Action  •  [Esc] Back")).append("\n");
        } else {
            sb.append(ConsoleTheme.keyGuide("[↑/↓/←/→] Navigate  •  [1-8, 0] Quick Hotkey  •  [Enter] Select  •  [Esc] Logout")).append("\n");
        }

        ScreenRenderer.render(sb.toString(), firstRender);
    }

    public void executeAction(int choice, ScreenNavigator navigator, TUISession session) {
        switch (choice) {
            case 1 -> navigator.push(new TransferScreen());
            case 2 -> {
                // Handled in-place in raw loop
            }
            case 3 -> navigator.push(new ExchangeScreen());
            case 4 -> navigator.push(new TransactionHistoryScreen());
            case 5 -> navigator.push(new LoanScreen());
            case 6 -> navigator.push(new BudgetManagementScreen());
            case 7 -> navigator.push(new SavingsGoalsScreen());
            case 8 -> navigator.push(new CreateAccountScreen(this));
            case 0 -> {
                ControllerFactory.getAuthController().logoutUser();
                session.logout();
                navigator.clearAndPush(new WelcomeScreen());
            }
        }
    }
}
