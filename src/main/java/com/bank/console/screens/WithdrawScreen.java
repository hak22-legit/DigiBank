package com.bank.console.screens;

import com.bank.console.ControllerFactory;
import com.bank.console.ScreenNavigator;
import com.bank.console.TUISession;
import com.bank.console.components.*;
import com.bank.console.components.TUIFormHelper.KeyAction;
import com.bank.console.components.TUIFormHelper.KeyEvent;
import com.bank.console.theme.ConsoleTheme;
import com.bank.controller.AccountController;
import com.bank.controller.CategoryController;
import com.bank.exception.InsufficientBalanceException;
import com.bank.model.dto.AccountDTO;
import com.bank.model.dto.UserDTO;
import com.bank.model.entity.Category;
import com.bank.model.entity.Transaction;
import com.bank.model.entity.User;
import com.bank.security.SessionManager;
import org.jline.terminal.Attributes;
import org.jline.terminal.Terminal;
import org.jline.utils.NonBlockingReader;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;

/**
 * Screen for withdrawing cash/funds from a user's bank account.
 * Strict 82-column enclosed container, dedicated status slot, action compartment,
 * real-time amount formatting, and seamless keyboard navigation.
 */
public class WithdrawScreen implements Screen {
    private final AccountController accountController;
    private final CategoryController categoryController;

    private static final String[] EXPENSE_CATEGORIES = {
            "None / Skip (Default)",
            "Food & Dining",
            "Transportation",
            "Shopping",
            "Entertainment",
            "Education",
            "Bills & Utilities",
            "Healthcare",
            "Other"
    };

    public WithdrawScreen() {
        this(ControllerFactory.getAccountController(), ControllerFactory.getCategoryController());
    }

    public WithdrawScreen(AccountController accountController) {
        this(accountController, ControllerFactory.getCategoryController());
    }

    public WithdrawScreen(AccountController accountController, CategoryController categoryController) {
        this.accountController = accountController;
        this.categoryController = categoryController;
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
        session.clearScreen();

        List<AccountDTO> accounts;
        try {
            accounts = accountController.getAccountsForUser(userEntity);
        } catch (Exception e) {
            TUILayout.printAlert("Failed to load accounts: " + e.getMessage(), true);
            ConsolePrompt.pause();
            navigator.pop();
            return;
        }

        if (accounts == null || accounts.isEmpty()) {
            TUILayout.printAlert("No active bank accounts found for withdrawal.", true);
            ConsolePrompt.pause();
            navigator.pop();
            return;
        }

        AccountDTO sourceAccount = accounts.get(0);

        // Load Categories from Controller
        List<Category> dbCategories = new ArrayList<>();
        try {
            List<Category> cats = categoryController.getVisibleCategories(userEntity);
            if (cats != null) dbCategories.addAll(cats);
        } catch (Exception ignored) {}

        List<String> categoryLabels = new ArrayList<>();
        categoryLabels.add("None / Skip (Default)");
        for (int i = 1; i <= 8; i++) {
            if (i - 1 < dbCategories.size()) {
                categoryLabels.add(dbCategories.get(i - 1).getName());
            } else {
                categoryLabels.add(EXPENSE_CATEGORIES[i]);
            }
        }

        // Form state:
        // 0: Source Account (opens WithdrawAccountSelectorModal)
        // 1: Withdrawal Amount
        // 2: Remark (Optional)
        // 3: Expense Category
        // 4: Action Bar ([1] Authorize & Dispense Cash, [2] Cancel & Return)
        int focusedField = 0;
        int actionIndex = 0; // 0: Authorize & Dispense Cash, 1: Cancel & Return
        StringBuilder amountBuf = new StringBuilder();
        StringBuilder remarkBuf = new StringBuilder();
        int selectedCategoryIdx = 4; // Default to Entertainment matching mockup or 0
        boolean categoryChosen = true;
        String statusMessage = null;
        boolean isError = false;

        enum ScreenState { FORM, CATEGORY_SELECT }
        ScreenState state = ScreenState.FORM;
        int categoryHighlightIdx = selectedCategoryIdx;

        Terminal terminal = session.getTerminal();
        Attributes origAttr = terminal.enterRawMode();
        NonBlockingReader reader = terminal.reader();
        boolean firstRender = true;

        try {
            while (true) {
                if (state == ScreenState.FORM) {
                    StringBuilder sb = new StringBuilder();
                    sb.append(TUIBox.top(width)).append("\n");
                    sb.append(TUIBox.line(ConsoleTheme.primary("DIGIBANK CORE > CASH OPERATIONS > WITHDRAW AMOUNT"), width)).append("\n");
                    sb.append(TUIBox.divider(width)).append("\n");
                    sb.append(TUIBox.line("WITHDRAWAL DETAILS", width)).append("\n");
                    sb.append(TUIBox.emptyLine(width)).append("\n");

                    String sourceAccStr = String.format("%s (%s - %s)",
                            sourceAccount.getAccountNumber(), sourceAccount.getAccountType(), sourceAccount.getCurrency());
                    String balanceStr = ConsoleFormatter.formatAccountBalance(sourceAccount.getBalance(), sourceAccount.getCurrency());

                    sb.append(TUIFormHelper.formatFieldRow("Source Account", sourceAccStr, focusedField == 0, 18, 50)).append("\n");
                    sb.append(TUIFormHelper.formatInfoRow("Available Balance", balanceStr, 18, 50)).append("\n");
                    sb.append(TUIBox.emptyLine(width)).append("\n");

                    String amountDisplay;
                    if (focusedField == 1) {
                        amountDisplay = amountBuf.toString();
                    } else {
                        if (amountBuf.length() > 0) {
                            try {
                                BigDecimal amt = new BigDecimal(amountBuf.toString().replace(",", "").replace("$", "").trim());
                                String sym = ConsoleFormatter.getCurrencySymbol(sourceAccount.getCurrency());
                                amountDisplay = sym + " " + ConsoleFormatter.formatAmount(amt, sourceAccount.getCurrency());
                            } catch (Exception e) {
                                amountDisplay = amountBuf.toString();
                            }
                        } else {
                            amountDisplay = "";
                        }
                    }
                    sb.append(TUIFormHelper.formatFieldRow("Withdrawal Amount", amountDisplay, focusedField == 1, 18, 50)).append("\n");
                    sb.append(TUIFormHelper.formatFieldRow("Remark (Optional)", remarkBuf.toString(), focusedField == 2, 18, 50)).append("\n");

                    String catDisplay = String.format("(%d) %s", selectedCategoryIdx, categoryLabels.get(selectedCategoryIdx));
                    sb.append(TUIFormHelper.formatFieldRow("Expense Category", catDisplay, focusedField == 3, 18, 50)).append("\n");
                    sb.append(TUIBox.emptyLine(width)).append("\n");

                    // ACTION Compartment
                    sb.append(TUIBox.divider(width)).append("\n");
                    sb.append(TUIBox.line("  ACTION", width)).append("\n");
                    sb.append(TUIBox.emptyLine(width)).append("\n");

                    String btn1 = "[1] Authorize & Dispense Cash";
                    String btn2 = "[2] Cancel & Return";
                    String actionLine;
                    if (focusedField == 4 && actionIndex == 1) {
                        actionLine = "    " + btn1 + "               ▸ " + ConsoleTheme.highlight(btn2);
                    } else if (focusedField == 4) {
                        actionLine = "  ▸ " + ConsoleTheme.highlight(btn1) + "                 " + btn2;
                    } else {
                        actionLine = "  ▸ " + btn1 + "                 " + btn2;
                    }
                    sb.append(TUIBox.line(actionLine, width)).append("\n");

                    // Status Bar
                    sb.append(TUIBox.divider(width)).append("\n");
                    String statusText = (statusMessage != null)
                            ? (isError ? ConsoleTheme.error(statusMessage) : ConsoleTheme.success(statusMessage))
                            : "Ready";
                    sb.append(TUIBox.line("Status: " + statusText, width)).append("\n");

                    sb.append(TUIBox.bottom(width)).append("\n");
                    sb.append(ConsoleTheme.keyGuide("[Tab/↓] Next Field  •  [Enter] Select Account  •  [1/2] Action  •  [Esc] Cancel")).append("\n");

                    ScreenRenderer.render(sb.toString(), firstRender);
                    firstRender = false;

                    KeyEvent event = TUIFormHelper.readKey(reader);
                    if (event.action() == KeyAction.ESCAPE) {
                        terminal.setAttributes(origAttr);
                        navigator.pop();
                        return;
                    } else if (event.action() == KeyAction.TAB || event.action() == KeyAction.DOWN) {
                        focusedField = (focusedField + 1) % 5;
                    } else if (event.action() == KeyAction.SHIFT_TAB || event.action() == KeyAction.UP) {
                        focusedField = (focusedField - 1 + 5) % 5;
                    } else if (event.action() == KeyAction.LEFT) {
                        if (focusedField == 4 && actionIndex == 1) {
                            actionIndex = 0;
                        }
                    } else if (event.action() == KeyAction.RIGHT) {
                        if (focusedField == 4 && actionIndex == 0) {
                            actionIndex = 1;
                        }
                    } else if (event.action() == KeyAction.BACKSPACE) {
                        statusMessage = null;
                        isError = false;
                        if (focusedField == 1 && amountBuf.length() > 0) {
                            amountBuf.deleteCharAt(amountBuf.length() - 1);
                        } else if (focusedField == 2 && remarkBuf.length() > 0) {
                            remarkBuf.deleteCharAt(remarkBuf.length() - 1);
                        }
                    } else if (event.action() == KeyAction.ENTER) {
                        statusMessage = null;
                        isError = false;
                        if (focusedField == 0) {
                            AccountDTO selected = WithdrawAccountSelectorModal.selectAccount(
                                    terminal, origAttr, reader, accounts, sourceAccount, width);
                            if (selected != null) {
                                sourceAccount = selected;
                                focusedField = 1; // Auto advance to Withdrawal Amount
                            }
                            firstRender = true;
                        } else if (focusedField == 1) {
                            if (amountBuf.length() > 0) {
                                try {
                                    BigDecimal testAmt = new BigDecimal(amountBuf.toString().replace(",", "").replace("$", "").trim());
                                    if (testAmt.compareTo(BigDecimal.ZERO) <= 0) {
                                        statusMessage = "Amount must be greater than zero.";
                                        isError = true;
                                    } else if (sourceAccount.getBalance().compareTo(testAmt) < 0) {
                                        statusMessage = "Insufficient balance for withdrawal.";
                                        isError = true;
                                    } else {
                                        focusedField = 2;
                                    }
                                } catch (Exception e) {
                                    statusMessage = "Invalid numeric amount format.";
                                    isError = true;
                                }
                            } else {
                                statusMessage = "Please enter withdrawal amount.";
                                isError = true;
                            }
                        } else if (focusedField == 2) {
                            if (remarkBuf.toString().trim().isEmpty()) {
                                remarkBuf.setLength(0);
                                remarkBuf.append("Cash Withdrawal");
                            }
                            focusedField = 3;
                        } else if (focusedField == 3) {
                            state = ScreenState.CATEGORY_SELECT;
                            categoryHighlightIdx = selectedCategoryIdx;
                            firstRender = true;
                        } else if (focusedField == 4) {
                            if (actionIndex == 0) {
                                // [1] Authorize & Dispense Cash
                                if (handleWithdrawAction(navigator, terminal, origAttr, reader, sourceAccount,
                                        amountBuf, remarkBuf, selectedCategoryIdx, dbCategories, width)) {
                                    return;
                                }
                            } else {
                                // [2] Cancel & Return
                                terminal.setAttributes(origAttr);
                                navigator.pop();
                                return;
                            }
                        }
                    } else if (event.action() == KeyAction.DIGIT || event.action() == KeyAction.CHAR) {
                        char c = event.ch();
                        statusMessage = null;
                        isError = false;
                        if (focusedField == 1) {
                            if ((c >= '0' && c <= '9') || (c == '.' && !amountBuf.toString().contains("."))) {
                                if (amountBuf.length() < 12) {
                                    amountBuf.append(c);
                                }
                            }
                        } else if (focusedField == 2) {
                            if (remarkBuf.length() < 40) {
                                remarkBuf.append(c);
                            }
                        } else if (focusedField == 0 || focusedField >= 3) {
                            if (c == '1') {
                                // Numeric hotkey 1: Authorize & Dispense Cash
                                if (handleWithdrawAction(navigator, terminal, origAttr, reader, sourceAccount,
                                        amountBuf, remarkBuf, selectedCategoryIdx, dbCategories, width)) {
                                    return;
                                }
                            } else if (c == '2') {
                                // Numeric hotkey 2: Cancel & Return
                                terminal.setAttributes(origAttr);
                                navigator.pop();
                                return;
                            }
                        }
                    }

                } else if (state == ScreenState.CATEGORY_SELECT) {
                    StringBuilder sb = new StringBuilder();
                    sb.append(TUIBox.top(width)).append("\n");
                    sb.append(TUIBox.line(ConsoleTheme.primary("DIGIBANK CORE > CASH OPERATIONS > WITHDRAW AMOUNT > SELECT EXPENSE CATEGORY"), width)).append("\n");
                    sb.append(TUIBox.divider(width)).append("\n");
                    sb.append(TUIBox.line("Choose expense category using Arrow Keys:", width)).append("\n");
                    sb.append(TUIBox.emptyLine(width)).append("\n");

                    for (int i = 0; i <= 8; i++) {
                        String label = categoryLabels.get(i);
                        String row = String.format("[%d] %s", i, label);
                        if (i == categoryHighlightIdx) {
                            sb.append(TUIBox.line("  ▸ " + ConsoleTheme.highlight(row), width)).append("\n");
                        } else {
                            sb.append(TUIBox.line("    " + row, width)).append("\n");
                        }
                    }

                    sb.append(TUIBox.emptyLine(width)).append("\n");
                    sb.append(TUIBox.divider(width)).append("\n");
                    sb.append(TUIBox.bottom(width)).append("\n");
                    sb.append(ConsoleTheme.keyGuide("[↑/↓] Navigate  •  [Enter] Confirm Category  •  [0-8] Quick Select  •  [Esc] Skip")).append("\n");

                    ScreenRenderer.render(sb.toString(), firstRender);
                    firstRender = false;

                    KeyEvent event = TUIFormHelper.readKey(reader);
                    if (event.action() == KeyAction.ESCAPE) {
                        state = ScreenState.FORM;
                        firstRender = true;
                    } else if (event.action() == KeyAction.UP) {
                        categoryHighlightIdx = (categoryHighlightIdx - 1 + 9) % 9;
                    } else if (event.action() == KeyAction.DOWN) {
                        categoryHighlightIdx = (categoryHighlightIdx + 1) % 9;
                    } else if (event.action() == KeyAction.ENTER) {
                        selectedCategoryIdx = categoryHighlightIdx;
                        categoryChosen = true;
                        state = ScreenState.FORM;
                        focusedField = 4; // Shift focus to Action Bar
                        actionIndex = 0;
                        firstRender = true;
                    } else if (event.action() == KeyAction.DIGIT && event.ch() >= '0' && event.ch() <= '8') {
                        selectedCategoryIdx = event.ch() - '0';
                        categoryChosen = true;
                        state = ScreenState.FORM;
                        focusedField = 4; // Shift focus to Action Bar
                        actionIndex = 0;
                        firstRender = true;
                    }
                }
            }
        } catch (Exception e) {
            TUILayout.printAlert("Error: " + e.getMessage(), true);
            ConsolePrompt.pause();
            navigator.pop();
        } finally {
            terminal.setAttributes(origAttr);
        }
    }

    private boolean handleWithdrawAction(ScreenNavigator navigator, Terminal terminal, Attributes origAttr,
                                         NonBlockingReader reader, AccountDTO sourceAccount,
                                         StringBuilder amountBuf, StringBuilder remarkBuf,
                                         int selectedCategoryIdx, List<Category> dbCategories,
                                         int width) {
        String amtStr = amountBuf.toString().replace(",", "").replace("$", "").trim();
        if (amtStr.isEmpty()) {
            return false;
        }

        BigDecimal amt;
        try {
            amt = new BigDecimal(amtStr);
            if (amt.compareTo(BigDecimal.ZERO) <= 0) {
                return false;
            }
            if (sourceAccount.getBalance().compareTo(amt) < 0) {
                return false;
            }
        } catch (Exception e) {
            return false;
        }

        String remark = remarkBuf.toString().trim();
        if (remark.isEmpty()) {
            remark = "Cash Withdrawal";
        }

        executeWithdrawal(navigator, terminal, origAttr, reader, sourceAccount, amt,
                selectedCategoryIdx, dbCategories, remark, width);
        return true;
    }

    private void executeWithdrawal(ScreenNavigator navigator, Terminal terminal, Attributes origAttr,
                                   NonBlockingReader reader, AccountDTO sourceAccount, BigDecimal amount,
                                   int selectedCategoryIdx, List<Category> dbCategories, String remark, int width) {
        User userEntity = SessionManager.getCurrentUser();
        Long catId = null;
        if (selectedCategoryIdx > 0 && selectedCategoryIdx - 1 < dbCategories.size()) {
            catId = dbCategories.get(selectedCategoryIdx - 1).getCategoryId();
        }

        try {
            Transaction txn = accountController.withdraw(
                    sourceAccount.getAccountId(),
                    amount,
                    sourceAccount.getCurrency(),
                    remark,
                    catId,
                    userEntity
            );

            BigDecimal newBal = sourceAccount.getBalance().subtract(amount);

            String debitedFormatted = "-" + ConsoleFormatter.formatAccountBalance(amount, sourceAccount.getCurrency());
            String newBalFormatted = ConsoleFormatter.formatAccountBalance(newBal, sourceAccount.getCurrency());

            StringBuilder compSb = new StringBuilder();
            compSb.append(TUIBox.top(width)).append("\n");
            compSb.append(TUIBox.line(ConsoleTheme.primary("DIGIBANK CORE > CASH OPERATIONS > WITHDRAWAL COMPLETED"), width)).append("\n");
            compSb.append(TUIBox.divider(width)).append("\n");
            compSb.append(TUIBox.emptyLine(width)).append("\n");
            compSb.append(TUIBox.center(ConsoleTheme.success("✔ Cash dispensed successfully!"), width)).append("\n");
            compSb.append(TUIBox.emptyLine(width)).append("\n");
            compSb.append(TUIBox.line("  Transaction ID:  #" + txn.getTransactionId(), width)).append("\n");
            compSb.append(TUIBox.line("  Debited:         " + ConsoleTheme.error(debitedFormatted), width)).append("\n");
            compSb.append(TUIBox.line("  New Balance:     " + newBalFormatted, width)).append("\n");
            compSb.append(TUIBox.emptyLine(width)).append("\n");
            compSb.append(TUIBox.divider(width)).append("\n");
            compSb.append(TUIBox.bottom(width)).append("\n");
            compSb.append(ConsoleTheme.keyGuide("[Enter] Return to Main Menu  •  [Esc] Back")).append("\n");

            ScreenRenderer.render(compSb.toString(), true);
            while (true) {
                KeyEvent doneEvt = TUIFormHelper.readKey(reader);
                if (doneEvt.action() == KeyAction.ENTER || doneEvt.action() == KeyAction.ESCAPE || doneEvt.ch() == 'b' || doneEvt.ch() == 'B') {
                    terminal.setAttributes(origAttr);
                    navigator.pop();
                    return;
                }
            }
        } catch (InsufficientBalanceException e) {
            renderErrorBox(navigator, terminal, origAttr, reader, "Withdrawal Failed: Insufficient balance", width);
        } catch (Exception e) {
            renderErrorBox(navigator, terminal, origAttr, reader, "Withdrawal Failed: " + e.getMessage(), width);
        }
    }

    private void renderErrorBox(ScreenNavigator navigator, Terminal terminal, Attributes origAttr,
                                NonBlockingReader reader, String errorMsg, int width) {
        try {
            StringBuilder errSb = new StringBuilder();
            errSb.append(TUIBox.top(width)).append("\n");
            errSb.append(TUIBox.line(ConsoleTheme.error(" " + errorMsg), width)).append("\n");
            errSb.append(TUIBox.divider(width)).append("\n");
            errSb.append(TUIBox.bottom(width)).append("\n");
            errSb.append(ConsoleTheme.keyGuide("[Enter/Esc] Return to Menu")).append("\n");
            ScreenRenderer.render(errSb.toString(), true);
            while (true) {
                KeyEvent doneEvt = TUIFormHelper.readKey(reader);
                if (doneEvt.action() == KeyAction.ENTER || doneEvt.action() == KeyAction.ESCAPE) {
                    break;
                }
            }
        } catch (Exception ignored) {}
        terminal.setAttributes(origAttr);
        navigator.pop();
    }
}
