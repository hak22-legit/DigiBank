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
 * Screen for depositing funds into a user's bank account.
 * Strict 82-column enclosed container, in-place editing, and zero trailing prompts.
 */
public class DepositScreen implements Screen {
    private final AccountController accountController;
    private final CategoryController categoryController;

    private static final String[] DEFAULT_CATEGORIES = {
            "None / Skip (Default)",
            "Salary / Income",
            "Food & Dining",
            "Transportation",
            "Shopping",
            "Entertainment",
            "Bills & Utilities",
            "Healthcare",
            "Other"
    };

    public DepositScreen() {
        this(ControllerFactory.getAccountController(), ControllerFactory.getCategoryController());
    }

    public DepositScreen(AccountController accountController) {
        this(accountController, ControllerFactory.getCategoryController());
    }

    public DepositScreen(AccountController accountController, CategoryController categoryController) {
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
            TUILayout.printAlert("No active bank accounts found for deposit.", true);
            ConsolePrompt.pause();
            navigator.pop();
            return;
        }

        // 1. Select Account if multiple accounts
        AccountDTO targetAccount = accounts.get(0);
        if (accounts.size() > 1) {
            int selectedAccIdx = 0;
            Terminal terminal = session.getTerminal();
            Attributes origAttr = terminal.enterRawMode();
            NonBlockingReader reader = terminal.reader();
            boolean firstRender = true;
            try {
                while (true) {
                    StringBuilder sb = new StringBuilder();
                    sb.append(TUIBox.top(width)).append("\n");
                    sb.append(TUIBox.line(ConsoleTheme.primary("DIGIBANK CORE > CASH OPERATIONS > DEPOSIT ACCOUNT"), width)).append("\n");
                    sb.append(TUIBox.divider(width)).append("\n");
                    sb.append(TUIBox.emptyLine(width)).append("\n");

                    for (int i = 0; i < accounts.size(); i++) {
                        AccountDTO acc = accounts.get(i);
                        String row = String.format("[%d] %s (%s) — Balance: %s %s",
                                i + 1, acc.getAccountNumber(), acc.getAccountType(),
                                ConsoleFormatter.formatCurrency(acc.getBalance()), acc.getCurrency());
                        if (i == selectedAccIdx) {
                            sb.append(TUIBox.line("  ► " + ConsoleTheme.highlight(row), width)).append("\n");
                        } else {
                            sb.append(TUIBox.line("    " + row, width)).append("\n");
                        }
                    }

                    sb.append(TUIBox.emptyLine(width)).append("\n");
                    sb.append(TUIBox.bottom(width)).append("\n");
                    String hotkeyRange = "1-" + Math.min(accounts.size(), 9);
                    sb.append(ConsoleTheme.muted(String.format(" [↑/↓] Navigate  •  [Enter] Select  •  [%s] Hotkey  •  [Esc] Back", hotkeyRange))).append("\n");

                    ScreenRenderer.render(sb.toString(), firstRender);
                    firstRender = false;

                    KeyEvent event = TUIFormHelper.readKey(reader);
                    if (event.action() == KeyAction.ESCAPE) {
                        terminal.setAttributes(origAttr);
                        navigator.pop();
                        return;
                    } else if (event.action() == KeyAction.UP) {
                        selectedAccIdx = (selectedAccIdx - 1 + accounts.size()) % accounts.size();
                    } else if (event.action() == KeyAction.DOWN) {
                        selectedAccIdx = (selectedAccIdx + 1) % accounts.size();
                    } else if (event.action() == KeyAction.ENTER) {
                        targetAccount = accounts.get(selectedAccIdx);
                        break;
                    } else if (event.action() == KeyAction.DIGIT && event.ch() >= '1' && event.ch() <= '0' + Math.min(accounts.size(), 9)) {
                        targetAccount = accounts.get(event.ch() - '1');
                        break;
                    } else if (event.ch() == 'b' || event.ch() == 'B') {
                        terminal.setAttributes(origAttr);
                        navigator.pop();
                        return;
                    }
                }
            } catch (Exception e) {
                targetAccount = accounts.get(0);
            } finally {
                terminal.setAttributes(origAttr);
            }
        }

        // Load Categories from Controller
        List<Category> dbCategories = new ArrayList<>();
        try {
            List<Category> cats = categoryController.getVisibleCategories(userEntity);
            if (cats != null) dbCategories.addAll(cats);
        } catch (Exception ignored) {}

        List<String> categoryLabels = new ArrayList<>();
        categoryLabels.add("None / Skip (Default)");
        for (int i = 0; i < 8; i++) {
            if (i < dbCategories.size()) {
                categoryLabels.add(dbCategories.get(i).getName());
            } else {
                categoryLabels.add(DEFAULT_CATEGORIES[i + 1]);
            }
        }

        DecimalFormat df = new DecimalFormat("#,##0.00");

        // Form state
        int focusedField = 0; // 0: Amount, 1: Remark, 2: Category, 3: Submit Button
        StringBuilder amountBuf = new StringBuilder();
        StringBuilder remarkBuf = new StringBuilder();
        int selectedCategoryIdx = 0;
        boolean categoryChosen = false;
        String statusMessage = null;

        enum ScreenState { FORM, CATEGORY_SELECT, CONFIRMATION, COMPLETED }
        ScreenState state = ScreenState.FORM;
        int categoryHighlightIdx = 0;
        int confirmActionIdx = 0; // 0: Confirm, 1: Cancel

        Terminal terminal = session.getTerminal();
        Attributes origAttr = terminal.enterRawMode();
        NonBlockingReader reader = terminal.reader();
        boolean firstRender = true;

        try {
            while (true) {
                if (state == ScreenState.FORM) {
                    StringBuilder sb = new StringBuilder();
                    sb.append(TUIBox.top(width)).append("\n");
                    sb.append(TUIBox.line(ConsoleTheme.primary("DIGIBANK CORE > CASH OPERATIONS > CASH DEPOSIT"), width)).append("\n");
                    sb.append(TUIBox.divider(width)).append("\n");
                    sb.append(TUIBox.line("DEPOSIT DETAILS", width)).append("\n");
                    sb.append(TUIBox.emptyLine(width)).append("\n");

                    String targetAccStr = String.format("%s (%s - %s)",
                            targetAccount.getAccountNumber(), targetAccount.getAccountType(), targetAccount.getCurrency());
                    String balanceStr = String.format("$ %s %s", df.format(targetAccount.getBalance()), targetAccount.getCurrency());

                    sb.append(TUIFormHelper.formatFieldRow("Target Account", targetAccStr, false, 18, 50)).append("\n");
                    sb.append(TUIFormHelper.formatInfoRow("Current Balance", balanceStr, 18, 50)).append("\n");
                    sb.append(TUIBox.emptyLine(width)).append("\n");

                    sb.append(TUIFormHelper.formatFieldRow("Deposit Amount", amountBuf.toString(), focusedField == 0, 18, 50)).append("\n");
                    sb.append(TUIFormHelper.formatFieldRow("Remark (Optional)", remarkBuf.toString(), focusedField == 1, 18, 50)).append("\n");

                    String catDisplay = categoryChosen
                            ? String.format("(%d) %s", selectedCategoryIdx, categoryLabels.get(selectedCategoryIdx))
                            : "Press [Enter] to choose category";
                    sb.append(TUIFormHelper.formatFieldRow("Category", catDisplay, focusedField == 2, 18, 50)).append("\n");
                    sb.append(TUIBox.emptyLine(width)).append("\n");

                    if (statusMessage != null) {
                        sb.append(TUIBox.line(ConsoleTheme.error(" Status: " + statusMessage), width)).append("\n");
                    }

                    sb.append(TUIBox.divider(width)).append("\n");
                    sb.append(TUIBox.bottom(width)).append("\n");
                    sb.append(ConsoleTheme.muted(" [Tab/↓] Next Field  •  [Enter] Edit / Select  •  [Esc] Cancel")).append("\n");

                    ScreenRenderer.render(sb.toString(), firstRender);
                    firstRender = false;

                    KeyEvent event = TUIFormHelper.readKey(reader);
                    if (event.action() == KeyAction.ESCAPE) {
                        terminal.setAttributes(origAttr);
                        navigator.pop();
                        return;
                    } else if (event.action() == KeyAction.TAB || event.action() == KeyAction.DOWN) {
                        focusedField = (focusedField + 1) % 3;
                    } else if (event.action() == KeyAction.SHIFT_TAB || event.action() == KeyAction.UP) {
                        focusedField = (focusedField - 1 + 3) % 3;
                    } else if (event.action() == KeyAction.BACKSPACE) {
                        if (focusedField == 0 && amountBuf.length() > 0) {
                            amountBuf.deleteCharAt(amountBuf.length() - 1);
                        } else if (focusedField == 1 && remarkBuf.length() > 0) {
                            remarkBuf.deleteCharAt(remarkBuf.length() - 1);
                        }
                    } else if (event.action() == KeyAction.ENTER) {
                        if (focusedField == 0) {
                            if (amountBuf.length() > 0) {
                                focusedField = 1;
                            } else {
                                statusMessage = "Please enter deposit amount";
                            }
                        } else if (focusedField == 1) {
                            if (remarkBuf.toString().trim().isEmpty()) {
                                remarkBuf.setLength(0);
                                remarkBuf.append("Cash Deposit");
                            }
                            focusedField = 2;
                        } else if (focusedField == 2) {
                            state = ScreenState.CATEGORY_SELECT;
                            categoryHighlightIdx = selectedCategoryIdx;
                            firstRender = true;
                        }
                    } else if (event.action() == KeyAction.DIGIT || event.action() == KeyAction.CHAR) {
                        char c = event.ch();
                        statusMessage = null;
                        if (focusedField == 0) {
                            if ((c >= '0' && c <= '9') || (c == '.' && !amountBuf.toString().contains("."))) {
                                if (amountBuf.length() < 12) {
                                    amountBuf.append(c);
                                }
                            }
                        } else if (focusedField == 1) {
                            if (remarkBuf.length() < 40) {
                                remarkBuf.append(c);
                            }
                        }
                    }

                    // If user has filled amount and category, or presses enter on Category when chosen
                    if (focusedField == 2 && categoryChosen && event.action() == KeyAction.ENTER) {
                        String amtStr = amountBuf.toString().trim();
                        try {
                            BigDecimal amt = new BigDecimal(amtStr);
                            if (amt.compareTo(BigDecimal.ZERO) <= 0) {
                                statusMessage = "Amount must be greater than zero.";
                            } else {
                                if (remarkBuf.toString().trim().isEmpty()) {
                                    remarkBuf.setLength(0);
                                    remarkBuf.append("Cash Deposit");
                                }
                                state = ScreenState.CONFIRMATION;
                                confirmActionIdx = 0;
                                firstRender = true;
                            }
                        } catch (Exception e) {
                            statusMessage = "Invalid numeric deposit amount";
                        }
                    }

                } else if (state == ScreenState.CATEGORY_SELECT) {
                    StringBuilder sb = new StringBuilder();
                    sb.append(TUIBox.top(width)).append("\n");
                    sb.append(TUIBox.line(ConsoleTheme.primary("DIGIBANK CORE > CASH OPERATIONS > CASH DEPOSIT > SELECT CATEGORY"), width)).append("\n");
                    sb.append(TUIBox.divider(width)).append("\n");
                    sb.append(TUIBox.line("Choose transaction category using Arrow Keys:", width)).append("\n");
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
                    sb.append(ConsoleTheme.muted(" [↑/↓] Navigate  •  [Enter] Confirm Category  •  [0-8] Quick Select  •  [Esc] Skip")).append("\n");

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
                        firstRender = true;
                    } else if (event.action() == KeyAction.DIGIT && event.ch() >= '0' && event.ch() <= '8') {
                        selectedCategoryIdx = event.ch() - '0';
                        categoryChosen = true;
                        state = ScreenState.FORM;
                        firstRender = true;
                    }

                } else if (state == ScreenState.CONFIRMATION) {
                    BigDecimal amount = new BigDecimal(amountBuf.toString().trim());
                    BigDecimal newBal = targetAccount.getBalance().add(amount);

                    StringBuilder sb = new StringBuilder();
                    sb.append(TUIBox.top(width)).append("\n");
                    sb.append(TUIBox.line(ConsoleTheme.primary("DIGIBANK CORE > CASH OPERATIONS > CONFIRM DEPOSIT"), width)).append("\n");
                    sb.append(TUIBox.divider(width)).append("\n");
                    sb.append(TUIBox.line(String.format("  Target Account    : %s (%s - %s)", targetAccount.getAccountNumber(), targetAccount.getAccountType(), targetAccount.getCurrency()), width)).append("\n");
                    sb.append(TUIBox.line(String.format("  Current Balance   : $ %s %s", df.format(targetAccount.getBalance()), targetAccount.getCurrency()), width)).append("\n");
                    sb.append(TUIBox.line(String.format("  Deposit Amount    : $ %s %s", df.format(amount), targetAccount.getCurrency()), width)).append("\n");
                    sb.append(TUIBox.line(String.format("  New Balance       : $ %s %s", df.format(newBal), targetAccount.getCurrency()), width)).append("\n");
                    String catName = categoryLabels.get(selectedCategoryIdx);
                    sb.append(TUIBox.line(String.format("  Category / Memo   : %s / \"%s\"", catName, remarkBuf.toString()), width)).append("\n");
                    sb.append(TUIBox.divider(width)).append("\n");
                    sb.append(TUIBox.line("  Select Action:", width)).append("\n");

                    String a1 = confirmActionIdx == 0 ? "  ▸ " + ConsoleTheme.highlight("[1] Confirm & Execute Deposit") : "    [1] Confirm & Execute Deposit";
                    String a2 = confirmActionIdx == 1 ? "  ▸ " + ConsoleTheme.highlight("[2] Cancel and Return") : "    [2] Cancel and Return";
                    sb.append(TUIBox.line(a1, width)).append("\n");
                    sb.append(TUIBox.line(a2, width)).append("\n");

                    sb.append(TUIBox.divider(width)).append("\n");
                    sb.append(TUIBox.bottom(width)).append("\n");
                    sb.append(ConsoleTheme.muted(" [↑/↓] Move Highlight  •  [Enter] Confirm  •  [1/2] Instant Action  •  [Esc] Back")).append("\n");

                    ScreenRenderer.render(sb.toString(), firstRender);
                    firstRender = false;

                    KeyEvent event = TUIFormHelper.readKey(reader);
                    if (event.action() == KeyAction.ESCAPE) {
                        state = ScreenState.FORM;
                        firstRender = true;
                    } else if (event.action() == KeyAction.UP || event.action() == KeyAction.DOWN) {
                        confirmActionIdx = (confirmActionIdx == 0) ? 1 : 0;
                    } else if (event.action() == KeyAction.ENTER) {
                        if (confirmActionIdx == 0) {
                            // Execute Deposit
                            Long catId = null;
                            if (selectedCategoryIdx > 0 && selectedCategoryIdx - 1 < dbCategories.size()) {
                                catId = dbCategories.get(selectedCategoryIdx - 1).getCategoryId();
                            }
                            try {
                                Transaction txn = accountController.deposit(
                                        targetAccount.getAccountId(),
                                        amount,
                                        targetAccount.getCurrency(),
                                        remarkBuf.toString(),
                                        catId,
                                        userEntity
                                );
                                state = ScreenState.COMPLETED;
                                firstRender = true;

                                StringBuilder succSb = new StringBuilder();
                                succSb.append(TUIBox.top(width)).append("\n");
                                succSb.append(TUIBox.line(ConsoleTheme.primary("DIGIBANK CORE > CASH OPERATIONS > DEPOSIT COMPLETED"), width)).append("\n");
                                succSb.append(TUIBox.divider(width)).append("\n");
                                succSb.append(TUIBox.emptyLine(width)).append("\n");
                                succSb.append(TUIBox.center(ConsoleTheme.success("✔ Deposit completed successfully!"), width)).append("\n");
                                succSb.append(TUIBox.emptyLine(width)).append("\n");
                                succSb.append(TUIBox.line("  Transaction ID:  #" + txn.getTransactionId(), width)).append("\n");
                                succSb.append(TUIBox.line("  Credited:        " + ConsoleTheme.success("+" + ConsoleFormatter.formatCurrency(amount) + " " + targetAccount.getCurrency()), width)).append("\n");
                                succSb.append(TUIBox.emptyLine(width)).append("\n");
                                succSb.append(TUIBox.divider(width)).append("\n");
                                succSb.append(TUIBox.bottom(width)).append("\n");
                                succSb.append(ConsoleTheme.muted(" [Enter] Return to Main Menu  •  [Esc] Back")).append("\n");

                                ScreenRenderer.render(succSb.toString(), true);
                                while (true) {
                                    KeyEvent doneEvt = TUIFormHelper.readKey(reader);
                                    if (doneEvt.action() == KeyAction.ENTER || doneEvt.action() == KeyAction.ESCAPE || doneEvt.ch() == 'b' || doneEvt.ch() == 'B') {
                                        terminal.setAttributes(origAttr);
                                        navigator.pop();
                                        return;
                                    }
                                }
                            } catch (Exception e) {
                                statusMessage = "Deposit failed: " + e.getMessage();
                                state = ScreenState.FORM;
                                firstRender = true;
                            }
                        } else {
                            state = ScreenState.FORM;
                            firstRender = true;
                        }
                    } else if (event.ch() == '1') {
                        confirmActionIdx = 0;
                        // Trigger confirm immediately
                        Long catId = null;
                        if (selectedCategoryIdx > 0 && selectedCategoryIdx - 1 < dbCategories.size()) {
                            catId = dbCategories.get(selectedCategoryIdx - 1).getCategoryId();
                        }
                        try {
                            Transaction txn = accountController.deposit(
                                    targetAccount.getAccountId(),
                                    amount,
                                    targetAccount.getCurrency(),
                                    remarkBuf.toString(),
                                    catId,
                                    userEntity
                            );
                            StringBuilder succSb = new StringBuilder();
                            succSb.append(TUIBox.top(width)).append("\n");
                            succSb.append(TUIBox.line(ConsoleTheme.primary("DIGIBANK CORE > CASH OPERATIONS > DEPOSIT COMPLETED"), width)).append("\n");
                            succSb.append(TUIBox.divider(width)).append("\n");
                            succSb.append(TUIBox.emptyLine(width)).append("\n");
                            succSb.append(TUIBox.center(ConsoleTheme.success("✔ Deposit completed successfully!"), width)).append("\n");
                            succSb.append(TUIBox.emptyLine(width)).append("\n");
                            succSb.append(TUIBox.line("  Transaction ID:  #" + txn.getTransactionId(), width)).append("\n");
                            succSb.append(TUIBox.line("  Credited:        " + ConsoleTheme.success("+" + ConsoleFormatter.formatCurrency(amount) + " " + targetAccount.getCurrency()), width)).append("\n");
                            succSb.append(TUIBox.emptyLine(width)).append("\n");
                            succSb.append(TUIBox.divider(width)).append("\n");
                            succSb.append(TUIBox.bottom(width)).append("\n");
                            succSb.append(ConsoleTheme.muted(" [Enter] Return to Main Menu  •  [Esc] Back")).append("\n");

                            ScreenRenderer.render(succSb.toString(), true);
                            while (true) {
                                KeyEvent doneEvt = TUIFormHelper.readKey(reader);
                                if (doneEvt.action() == KeyAction.ENTER || doneEvt.action() == KeyAction.ESCAPE || doneEvt.ch() == 'b' || doneEvt.ch() == 'B') {
                                    terminal.setAttributes(origAttr);
                                    navigator.pop();
                                    return;
                                }
                            }
                        } catch (Exception e) {
                            statusMessage = "Deposit failed: " + e.getMessage();
                            state = ScreenState.FORM;
                            firstRender = true;
                        }
                    } else if (event.ch() == '2') {
                        state = ScreenState.FORM;
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
}
