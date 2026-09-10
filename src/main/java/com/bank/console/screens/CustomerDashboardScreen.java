package com.bank.console.screens;

import com.bank.console.ControllerFactory;
import com.bank.console.ScreenNavigator;
import com.bank.console.TUISession;
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
import com.bank.model.enums.GoalStatus;
import com.bank.model.enums.LoanStatus;
import com.bank.security.SessionManager;
import org.jline.terminal.Attributes;
import org.jline.terminal.Terminal;
import org.jline.utils.NonBlockingReader;

import java.io.IOException;
import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.util.List;

/**
 * SCREEN 5: CUSTOMER DASHBOARD (82 Columns)
 * In-place keyboard navigation, in-box sub-menus, and zero dual-prompts.
 */
public class CustomerDashboardScreen implements Screen {
    private final AccountController accountController;
    private final FinancialController financialController;
    private final BudgetController budgetController;
    private final SavingGoalController savingGoalController;
    private final LoanController loanController;

    private static final String[] NAV_ITEMS = {
            "[1] Instant Transfer (P2P / Wire)",
            "[2] Cash Operations (Deposit / Withdraw)",
            "[3] Account Statements & Transaction History",
            "[4] Loan Management & Installments",
            "[5] Budgets & Saving Goals",
            "[6] Log Out"
    };

    private static final String[] CASH_SUB_ITEMS = {
            "[1] Cash Deposit (Deposit funds to checking/savings)",
            "[2] Cash Withdrawal (Withdraw funds from account)",
            "[0] Back to Main Navigation"
    };

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

        boolean firstRender = true;
        try {
            while (true) {
                renderScreen(session, userDto, userEntity, selectedIndex, inCashSubMenu, cashSubIndex, firstRender);
                firstRender = false;
                int ch = reader.read();

                if (ch == 27) { // Escape
                    int next = reader.read(60);
                    if (next == -2 || next == -1) {
                        // Bare ESC
                        if (inCashSubMenu) {
                            inCashSubMenu = false;
                            continue;
                        } else {
                            // Pop or Logout
                            terminal.setAttributes(origAttributes);
                            navigator.pop();
                            return;
                        }
                    }
                    if (next == '[' || next == 'O') {
                        int code = reader.read();
                        if (code == 'A') { // Up
                            if (inCashSubMenu) {
                                cashSubIndex = (cashSubIndex - 1 + CASH_SUB_ITEMS.length) % CASH_SUB_ITEMS.length;
                            } else {
                                selectedIndex = (selectedIndex - 1 + NAV_ITEMS.length) % NAV_ITEMS.length;
                            }
                        } else if (code == 'B') { // Down
                            if (inCashSubMenu) {
                                cashSubIndex = (cashSubIndex + 1) % CASH_SUB_ITEMS.length;
                            } else {
                                selectedIndex = (selectedIndex + 1) % NAV_ITEMS.length;
                            }
                        }
                    }
                } else if (ch == '\r' || ch == '\n') { // Enter
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
                        } else {
                            terminal.setAttributes(origAttributes);
                            executeAction(selectedIndex + 1, navigator, session);
                            return;
                        }
                    }
                } else if (!inCashSubMenu && ch >= '1' && ch <= '6') { // Direct numbers on main menu
                    terminal.setAttributes(origAttributes);
                    if (ch == '2') {
                        inCashSubMenu = true;
                        cashSubIndex = 0;
                        origAttributes = terminal.enterRawMode();
                    } else {
                        executeAction(ch - '0', navigator, session);
                        return;
                    }
                } else if (inCashSubMenu && (ch == '1' || ch == '2' || ch == '0')) { // Submenu shortcuts
                    terminal.setAttributes(origAttributes);
                    if (ch == '1') {
                        navigator.push(new DepositScreen());
                        return;
                    } else if (ch == '2') {
                        navigator.push(new WithdrawScreen());
                        return;
                    } else {
                        inCashSubMenu = false;
                        origAttributes = terminal.enterRawMode();
                    }
                } else if (ch == 3) { // Ctrl+C
                    session.clearScreen();
                    System.exit(0);
                }
            }
        } catch (IOException e) {
            executeAction(selectedIndex + 1, navigator, session);
        } finally {
            terminal.setAttributes(origAttributes);
        }
    }

    private void renderScreen(TUISession session, UserDTO userDto, User userEntity,
                             int selectedIndex, boolean inCashSubMenu, int cashSubIndex, boolean firstRender) {
        StringBuilder sb = new StringBuilder();
        if (firstRender) {
            sb.append(ConsoleTheme.CLEAR_SCREEN);
        } else {
            sb.append("\u001B[H"); // Cursor Home without wiping screen
        }
        int width = TUILayout.APP_WIDTH;
        DecimalFormat df = new DecimalFormat("#,##0.00");

        // Header Line
        String headerTitle = String.format("DIGIBANK CORE │ USER: %s (#USR-%d) │ STATUS: %s",
                userDto.getFullName().toUpperCase(), userDto.getUserId(), "ACTIVE");

        sb.append(TUIBox.top(width)).append("\n");
        sb.append(TUIBox.line(ConsoleTheme.primary(headerTitle), width)).append("\n");
        sb.append(TUIBox.divider(width)).append("\n");

        // 1. MY ACCOUNTS
        sb.append(TUIBox.line("MY ACCOUNTS", width)).append("\n");
        sb.append(TUIBox.emptyLine(width)).append("\n");
        sb.append(TUIBox.line("  Account Number    Type         Currency     Balance            Status", width)).append("\n");
        sb.append(TUIBox.line("  ────────────────  ───────────  ──────────   ─────────────────  ─────────────", width)).append("\n");

        try {
            List<AccountDTO> accounts = accountController.getAccountsForUser(userEntity);
            if (accounts.isEmpty()) {
                sb.append(TUIBox.line("  No active accounts registered.", width)).append("\n");
            } else {
                for (AccountDTO acc : accounts) {
                    String balStr = (acc.getCurrency() != null && acc.getCurrency().name().equalsIgnoreCase("KHR"))
                            ? String.format("%14s ៛", df.format(acc.getBalance()))
                            : String.format("$ %14s", df.format(acc.getBalance()));
                    String row = String.format("  %-16s  %-11s  %-10s   %17s  %-13s",
                            acc.getAccountNumber(),
                            acc.getAccountType(),
                            acc.getCurrency(),
                            balStr,
                            acc.getStatus());
                    sb.append(TUIBox.line(row, width)).append("\n");
                }
            }
        } catch (Exception e) {
            sb.append(TUIBox.line("  Error loading accounts: " + e.getMessage(), width)).append("\n");
        }

        sb.append(TUIBox.divider(width)).append("\n");

        // 2. FINANCIAL OVERVIEW
        sb.append(TUIBox.line("FINANCIAL OVERVIEW", width)).append("\n");
        sb.append(TUIBox.emptyLine(width)).append("\n");

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
        sb.append(TUIBox.line("  Active Loans   : " + loansOverview, width)).append("\n");

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
        sb.append(TUIBox.line("  Savings Goals  : " + goalsOverview, width)).append("\n");

        // Budget Overview
        String budgetOverview = "$0.00 spent of $0.00 limit";
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
        sb.append(TUIBox.line("  Monthly Budget : " + budgetOverview, width)).append("\n");

        sb.append(TUIBox.divider(width)).append("\n");

        // 3. NAVIGATION (OR IN-PLACE CASH SUBMENU)
        if (inCashSubMenu) {
            sb.append(TUIBox.line("CASH OPERATIONS (Select action or [0] to return)", width)).append("\n");
            sb.append(TUIBox.emptyLine(width)).append("\n");
            for (int i = 0; i < CASH_SUB_ITEMS.length; i++) {
                if (i == cashSubIndex) {
                    sb.append(TUIBox.line("   ► " + ConsoleTheme.highlight(CASH_SUB_ITEMS[i]), width)).append("\n");
                } else {
                    sb.append(TUIBox.line("     " + CASH_SUB_ITEMS[i], width)).append("\n");
                }
            }
        } else {
            sb.append(TUIBox.line("NAVIGATION", width)).append("\n");
            sb.append(TUIBox.emptyLine(width)).append("\n");
            for (int i = 0; i < NAV_ITEMS.length; i++) {
                if (i == selectedIndex) {
                    sb.append(TUIBox.line("   ► " + ConsoleTheme.highlight(NAV_ITEMS[i]), width)).append("\n");
                } else {
                    sb.append(TUIBox.line("     " + NAV_ITEMS[i], width)).append("\n");
                }
            }
        }

        sb.append(TUIBox.bottom(width)).append("\n");
        if (inCashSubMenu) {
            sb.append(ConsoleTheme.muted("  [↑/↓] Navigate  •  [Enter] Select  •  [1-2] Cash Action  •  [Esc] Back")).append("\n");
        } else {
            sb.append(ConsoleTheme.muted("  [↑/↓] Navigate  •  [Enter] Select  •  [1-6] Quick Select  •  [Esc] Logout")).append("\n");
        }

        System.out.print(sb.toString());
        System.out.flush();
    }

    private void executeAction(int choice, ScreenNavigator navigator, TUISession session) {
        switch (choice) {
            case 1 -> navigator.push(new TransferScreen());
            case 2 -> {
                // Handled in-place in raw loop
            }
            case 3 -> navigator.push(new TransactionHistoryScreen());
            case 4 -> navigator.push(new LoanScreen());
            case 5 -> navigator.push(new BudgetScreen());
            case 6 -> {
                ControllerFactory.getAuthController().logoutUser();
                session.logout();
                navigator.clearAndPush(new WelcomeScreen());
            }
        }
    }
}
