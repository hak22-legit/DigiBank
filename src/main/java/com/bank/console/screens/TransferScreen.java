package com.bank.console.screens;

import com.bank.console.ControllerFactory;
import com.bank.console.ScreenNavigator;
import com.bank.console.TUISession;
import com.bank.console.components.*;
import com.bank.console.components.TUIFormHelper.KeyAction;
import com.bank.console.components.TUIFormHelper.KeyEvent;
import com.bank.console.theme.ConsoleTheme;
import com.bank.controller.AccountController;
import com.bank.exception.InsufficientBalanceException;
import com.bank.model.dto.AccountDTO;
import com.bank.model.dto.UserDTO;
import com.bank.model.entity.Account;
import com.bank.model.entity.Transaction;
import com.bank.model.entity.User;
import com.bank.model.enums.Currency;
import com.bank.security.SessionManager;
import com.bank.service.LiveCurrencyService;
import com.bank.util.CurrencyConverter;
import org.jline.terminal.Attributes;
import org.jline.terminal.Terminal;
import org.jline.utils.NonBlockingReader;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * SCREEN 6: FUND TRANSFER (TRANSACTIONS) (82 Columns)
 * Enclosed form, dynamic beneficiary resolution, live cross-currency exchange math,
 * beneficiary account normalization, action buttons, and two-step confirmation.
 */
public class TransferScreen implements Screen {
    private final AccountController accountController;

    private static final String[] TRANSFER_CATEGORIES = {
            "General Transfer (Default)",
            "Family & Friends",
            "Food & Dining",
            "Rent & Living",
            "Shopping & Retail",
            "Business & Commercial",
            "Bills & Utilities",
            "Other"
    };

    public TransferScreen() {
        this(ControllerFactory.getAccountController());
    }

    public TransferScreen(AccountController accountController) {
        this.accountController = accountController;
    }

    public static String normalizeAccountNumber(String input) {
        if (input == null) return "";
        String trimmed = input.trim();
        if (trimmed.isEmpty()) return "";
        if (trimmed.toUpperCase().startsWith("DGB-")) {
            return trimmed.toUpperCase();
        }
        return "DGB-" + trimmed.toUpperCase();
    }

    private static String getCurrencySymbol(Currency currency) {
        if (currency == Currency.KHR) {
            return "៛";
        }
        return "$";
    }

    public String renderDestinationAccountField(String currentValue, boolean isFocused) {
        int fieldWidth = 55; // Inner bracket width
        if (currentValue == null || currentValue.isEmpty()) {
            String placeholder = "e.g. 788635551 or DGB-788635551";
            // ANSI 90 for dark gray placeholder text
            String content = "\033[90m" + placeholder + "\033[0m" + (isFocused ? "_" : "");
            int padding = fieldWidth - placeholder.length() - (isFocused ? 1 : 0);
            return "[ " + content + " ".repeat(Math.max(0, padding)) + " ]";
        } else {
            String content = currentValue + (isFocused ? "_" : "");
            int padding = fieldWidth - currentValue.length() - (isFocused ? 1 : 0);
            return "[ " + content + " ".repeat(Math.max(0, padding)) + " ]";
        }
    }

    public String renderDestinationAccountField(String currentValue, boolean isFocused, int fieldWidth) {
        if (currentValue == null || currentValue.isEmpty()) {
            String placeholder = "e.g. 788635551 or DGB-788635551";
            // ANSI 90 for dark gray placeholder text
            String content = "\033[90m" + placeholder + "\033[0m" + (isFocused ? "_" : "");
            int padding = fieldWidth - placeholder.length() - (isFocused ? 1 : 0);
            return "[ " + content + " ".repeat(Math.max(0, padding)) + " ]";
        } else {
            String content = currentValue + (isFocused ? "_" : "");
            int padding = fieldWidth - currentValue.length() - (isFocused ? 1 : 0);
            return "[ " + content + " ".repeat(Math.max(0, padding)) + " ]";
        }
    }

    private static String truncate(String text, int max) {
        if (text == null) return "";
        return text.length() > max ? text.substring(0, max) : text;
    }

    public static String renderInputRow(String label, String value, String hint, boolean isFocused, int width) {
        String prefix = isFocused ? "▸ " : "  ";
        String hintStr = (hint != null && !hint.isEmpty()) ? String.format("%13s", hint) : " ".repeat(13);
        if (hintStr.length() > 13) hintStr = hintStr.substring(0, 13);
        String formattedInput = String.format("%s%-17s : [ %-38s ] ", prefix, label, truncate(value, 38));
        String fullLine = formattedInput + hintStr;

        if (isFocused) {
            return TUIBox.line("\033[7m" + fullLine + "\033[0m", width);
        } else {
            return TUIBox.line(fullLine, width);
        }
    }

    public static String renderInfoRow(String label, String value, boolean isFocused, int width) {
        String prefix = isFocused ? "▸ " : "  ";
        String fullLine = String.format("%s%-17s : %-56s", prefix, label, truncate(value, 56));
        if (fullLine.length() > 78) fullLine = fullLine.substring(0, 78);
        if (isFocused) {
            return TUIBox.line("\033[7m" + fullLine + "\033[0m", width);
        } else {
            return TUIBox.line(fullLine, width);
        }
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
            TUILayout.printAlert("No active bank accounts found.", true);
            ConsolePrompt.pause();
            navigator.pop();
            return;
        }

        AccountDTO sourceAccount = accounts.get(0);
        int focusedField = 1; // Default to Destination Acc
        int actionIndex = 0;  // 0: Review & Confirm, 1: Cancel

        DecimalFormat df = new DecimalFormat("#,##0.00");

        // Form state:
        // 0: Source Account (opens AccountSelectorModal)
        // 1: Destination Acc
        // 2: Transfer Amount
        // 3: Remark (Optional)
        // 4: Category
        // 5: Action Bar ([1] Review & Submit Transfer, [2] Cancel & Return)
        StringBuilder destAccBuf = new StringBuilder();
        StringBuilder amountBuf = new StringBuilder();
        StringBuilder remarkBuf = new StringBuilder();
        int selectedCategoryIdx = 6; // Default to Bills & Utilities
        String statusMessage = null;

        Account resolvedDestAcc = null;
        String resolvedBeneficiaryName = null;

        enum ScreenState { FORM, CATEGORY_SELECT, CONFIRMATION }
        ScreenState state = ScreenState.FORM;
        int categoryHighlightIdx = selectedCategoryIdx;
        int confirmActionIdx = 0; // 0: Authorize & Send Transfer, 1: Back to Edit Details

        Terminal terminal = session.getTerminal();
        Attributes origAttr = terminal.enterRawMode();
        NonBlockingReader reader = terminal.reader();
        boolean firstRender = true;

        try {
            while (true) {
                if (state == ScreenState.FORM) {
                    StringBuilder sb = new StringBuilder();
                    sb.append(TUIBox.top(width)).append("\n");
                    sb.append(TUIBox.line(ConsoleTheme.primary("DIGIBANK CORE > MONEY MOVEMENT > TRANSFER"), width)).append("\n");
                    sb.append(TUIBox.divider(width)).append("\n");
                    sb.append(TUIBox.line("TRANSFER DETAILS", width)).append("\n");
                    sb.append(TUIBox.emptyLine(width)).append("\n");

                    String balDisplay = (sourceAccount.getCurrency() == Currency.KHR)
                            ? "៛ " + ConsoleFormatter.formatAmount(sourceAccount.getBalance(), "KHR")
                            : "$ " + df.format(sourceAccount.getBalance());
                    String sourceAccStr = String.format("%s (%s - %s)",
                            sourceAccount.getAccountNumber(), sourceAccount.getAccountType(), balDisplay);
                    if (sourceAccStr.length() > 38) {
                        sourceAccStr = String.format("%s (%s)", sourceAccount.getAccountNumber(), balDisplay);
                    }

                    sb.append(renderInputRow("Source Account", sourceAccStr, "[Space: Swap]", focusedField == 0, width)).append("\n");
                    sb.append(renderInputRow("Destination Acc", destAccBuf.toString() + (focusedField == 1 ? "|" : ""), null, focusedField == 1, width)).append("\n");

                    String benDisplay;
                    if (resolvedDestAcc != null && resolvedBeneficiaryName != null) {
                        benDisplay = resolvedBeneficiaryName;
                    } else if (destAccBuf.length() > 0) {
                        benDisplay = "Lookup in progress / Account not found";
                    } else {
                        benDisplay = "Awaiting destination account...";
                    }
                    sb.append(renderInfoRow("Beneficiary Name", benDisplay, false, width)).append("\n");

                    String amountDisplay;
                    if (focusedField == 2) {
                        amountDisplay = amountBuf.toString().isEmpty() ? "" : (amountBuf.toString() + " " + sourceAccount.getCurrency());
                    } else {
                        if (amountBuf.length() > 0) {
                            try {
                                BigDecimal amt = new BigDecimal(amountBuf.toString().replace(",", "").replace("$", "").trim());
                                amountDisplay = ConsoleFormatter.formatAmount(amt, sourceAccount.getCurrency().name()) + " " + sourceAccount.getCurrency();
                            } catch (Exception e) {
                                amountDisplay = amountBuf.toString() + " " + sourceAccount.getCurrency();
                            }
                        } else {
                            amountDisplay = "";
                        }
                    }
                    sb.append(renderInputRow("Transfer Amount", amountDisplay + (focusedField == 2 ? "|" : ""), null, focusedField == 2, width)).append("\n");
                    sb.append(renderInputRow("Remark (Optional)", remarkBuf.toString() + (focusedField == 3 ? "|" : ""), null, focusedField == 3, width)).append("\n");

                    String catDisplay = String.format("(%d) %s", selectedCategoryIdx, TRANSFER_CATEGORIES[selectedCategoryIdx]);
                    sb.append(renderInputRow("Category", catDisplay, "[Space: Swap]", focusedField == 4, width)).append("\n");
                    sb.append(TUIBox.emptyLine(width)).append("\n");

                    // TRANSACTION ACTIONS Compartment
                    sb.append(TUIBox.divider(width)).append("\n");
                    sb.append(TUIBox.line("TRANSACTION ACTIONS", width)).append("\n");
                    sb.append(TUIBox.emptyLine(width)).append("\n");

                    String btn1 = (focusedField == 5 && actionIndex == 0 ? "▸ " : "  ") + "[1] Review & Confirm Transfer";
                    String btn2 = (focusedField == 5 && actionIndex == 1 ? "▸ " : "  ") + "[2] Cancel & Return";
                    String leftCol = String.format("%-36s", btn1);
                    String rightCol = String.format("%-36s", btn2);
                    if (focusedField == 5 && actionIndex == 0) leftCol = "\033[7m" + leftCol + "\033[0m";
                    if (focusedField == 5 && actionIndex == 1) rightCol = "\033[7m" + rightCol + "\033[0m";
                    sb.append(TUIBox.line("  " + leftCol + "  " + rightCol, width)).append("\n");
                    sb.append(TUIBox.emptyLine(width)).append("\n");

                    sb.append(TUIBox.divider(width)).append("\n");
                    String statusText;
                    if (statusMessage != null) {
                        statusText = statusMessage;
                    } else if (resolvedDestAcc != null && resolvedBeneficiaryName != null) {
                        statusText = "Beneficiary verified: " + resolvedBeneficiaryName + ". Enter transfer amount and press [Tab].";
                    } else if (focusedField == 1) {
                        statusText = "Enter destination account number (9-digit number or DGB- prefix).";
                    } else {
                        statusText = "Enter transfer parameters. Press [Tab] to navigate, [Space] to toggle.";
                    }
                    String statusLine = "Status: " + statusText;
                    if (statusLine.length() > width - 4) {
                        statusLine = statusLine.substring(0, width - 7) + "...";
                    }
                    sb.append(TUIBox.line(statusLine, width)).append("\n");

                    sb.append(TUIBox.bottom(width)).append("\n");
                    sb.append(ConsoleTheme.keyGuide("[Tab/↓] Next Field  •  [Enter] Confirm/Select  •  [Space] Toggle  •  [Esc] Back")).append("\n");

                    ScreenRenderer.render(sb.toString(), firstRender);
                    firstRender = false;

                    KeyEvent event = TUIFormHelper.readKey(reader);
                    if (event.action() == KeyAction.ESCAPE) {
                        terminal.setAttributes(origAttr);
                        navigator.pop();
                        return;
                    } else if (event.action() == KeyAction.TAB || event.action() == KeyAction.DOWN) {
                        focusedField = (focusedField + 1) % 6;
                    } else if (event.action() == KeyAction.SHIFT_TAB || event.action() == KeyAction.UP) {
                        focusedField = (focusedField - 1 + 6) % 6;
                    } else if (event.action() == KeyAction.LEFT) {
                        if (focusedField == 5 && actionIndex == 1) {
                            actionIndex = 0;
                        }
                    } else if (event.action() == KeyAction.RIGHT) {
                        if (focusedField == 5 && actionIndex == 0) {
                            actionIndex = 1;
                        }
                    } else if (event.action() == KeyAction.BACKSPACE) {
                        statusMessage = null;
                        if (focusedField == 1 && destAccBuf.length() > 0) {
                            destAccBuf.deleteCharAt(destAccBuf.length() - 1);
                            resolvedDestAcc = null;
                            resolvedBeneficiaryName = null;
                        } else if (focusedField == 2 && amountBuf.length() > 0) {
                            amountBuf.deleteCharAt(amountBuf.length() - 1);
                        } else if (focusedField == 3 && remarkBuf.length() > 0) {
                            remarkBuf.deleteCharAt(remarkBuf.length() - 1);
                        }
                    } else if (event.action() == KeyAction.ENTER) {
                        statusMessage = null;
                        if (focusedField == 0) {
                            AccountDTO chosen = AccountSelectorModal.selectSenderAccount(terminal, origAttr, reader, accounts, sourceAccount, width);
                            if (chosen != null) {
                                sourceAccount = chosen;
                                focusedField = 1; // Auto advance to Destination Acc
                            }
                            firstRender = true;
                        } else if (focusedField == 1) {
                            // Validate & resolve destination account with auto-normalization
                            String normalized = normalizeAccountNumber(destAccBuf.toString());
                            if (normalized.isEmpty()) {
                                statusMessage = "Please enter destination account number";
                            } else {
                                try {
                                    Account acc = accountController.findAccountByNumber(normalized);
                                    if (acc.getAccountId().equals(sourceAccount.getAccountId())) {
                                        statusMessage = "Cannot transfer to the same account";
                                        resolvedDestAcc = null;
                                        resolvedBeneficiaryName = null;
                                    } else {
                                        resolvedDestAcc = acc;
                                        destAccBuf.setLength(0);
                                        destAccBuf.append(acc.getAccountNumber());
                                        Optional<User> benUser = ControllerFactory.getUserRepository().findById(acc.getUserId());
                                        resolvedBeneficiaryName = benUser.map(User::getFullName).orElse("VERIFIED BENEFICIARY");
                                        focusedField = 2;
                                    }
                                } catch (Exception e) {
                                    statusMessage = "Destination account not found: " + normalized;
                                    resolvedDestAcc = null;
                                    resolvedBeneficiaryName = null;
                                }
                            }
                        } else if (focusedField == 2) {
                            if (amountBuf.toString().trim().isEmpty()) {
                                statusMessage = "Please enter transfer amount";
                            } else {
                                try {
                                    BigDecimal amt = new BigDecimal(amountBuf.toString().replace(",", "").replace("$", "").trim());
                                    if (amt.compareTo(BigDecimal.ZERO) <= 0) {
                                        statusMessage = "Amount must be greater than zero.";
                                    } else if (sourceAccount.getBalance().compareTo(amt) < 0) {
                                        statusMessage = "Insufficient balance in source account";
                                    } else {
                                        focusedField = 3;
                                    }
                                } catch (Exception e) {
                                    statusMessage = "Invalid transfer amount format";
                                }
                            }
                        } else if (focusedField == 3) {
                            if (remarkBuf.toString().trim().isEmpty()) {
                                remarkBuf.setLength(0);
                                remarkBuf.append("Fund Transfer");
                            }
                            focusedField = 4;
                        } else if (focusedField == 4) {
                            state = ScreenState.CATEGORY_SELECT;
                            categoryHighlightIdx = selectedCategoryIdx;
                            firstRender = true;
                        } else if (focusedField == 5) {
                            if (actionIndex == 1) {
                                terminal.setAttributes(origAttr);
                                navigator.pop();
                                return;
                            }
                            // [1] Review & Submit Transfer
                            String normalized = normalizeAccountNumber(destAccBuf.toString());
                            if (normalized.isEmpty()) {
                                statusMessage = "Please enter destination account number";
                                focusedField = 1;
                            } else if (resolvedDestAcc == null) {
                                try {
                                    Account acc = accountController.findAccountByNumber(normalized);
                                    if (acc.getAccountId().equals(sourceAccount.getAccountId())) {
                                        statusMessage = "Cannot transfer to the same account";
                                        focusedField = 1;
                                    } else {
                                        resolvedDestAcc = acc;
                                        destAccBuf.setLength(0);
                                        destAccBuf.append(acc.getAccountNumber());
                                        Optional<User> benUser = ControllerFactory.getUserRepository().findById(acc.getUserId());
                                        resolvedBeneficiaryName = benUser.map(User::getFullName).orElse("VERIFIED BENEFICIARY");
                                    }
                                } catch (Exception e) {
                                    statusMessage = "Destination account not found: " + normalized;
                                    focusedField = 1;
                                }
                            }

                            if (resolvedDestAcc != null) {
                                String amtStr = amountBuf.toString().replace(",", "").replace("$", "").trim();
                                if (amtStr.isEmpty()) {
                                    statusMessage = "Please enter transfer amount";
                                    focusedField = 1;
                                } else {
                                    try {
                                        BigDecimal amt = new BigDecimal(amtStr);
                                        if (amt.compareTo(BigDecimal.ZERO) <= 0) {
                                            statusMessage = "Amount must be greater than zero.";
                                            focusedField = 1;
                                        } else if (sourceAccount.getBalance().compareTo(amt) < 0) {
                                            statusMessage = "Insufficient balance in source account";
                                            focusedField = 1;
                                        } else {
                                            if (remarkBuf.toString().trim().isEmpty()) {
                                                remarkBuf.setLength(0);
                                                remarkBuf.append("Fund Transfer");
                                            }
                                            state = ScreenState.CONFIRMATION;
                                            confirmActionIdx = 0;
                                            firstRender = true;
                                        }
                                    } catch (Exception e) {
                                        statusMessage = "Invalid transfer amount format";
                                        focusedField = 1;
                                    }
                                }
                            }
                        } else if (focusedField == 5) {
                            // [2] Cancel & Return
                            terminal.setAttributes(origAttr);
                            navigator.pop();
                            return;
                        }
                    } else if (event.action() == KeyAction.DIGIT || event.action() == KeyAction.CHAR) {
                        char c = event.ch();
                        statusMessage = null;
                        if (focusedField == 1) {
                            if (destAccBuf.length() < 24) {
                                destAccBuf.append(c);
                                // Opportunistically look up normalized number
                                String norm = normalizeAccountNumber(destAccBuf.toString());
                                try {
                                    Account acc = accountController.findAccountByNumber(norm);
                                    if (!acc.getAccountId().equals(sourceAccount.getAccountId())) {
                                        resolvedDestAcc = acc;
                                        Optional<User> benUser = ControllerFactory.getUserRepository().findById(acc.getUserId());
                                        resolvedBeneficiaryName = benUser.map(User::getFullName).orElse("VERIFIED BENEFICIARY");
                                    }
                                } catch (Exception ignored) {}
                            }
                        } else if (focusedField == 2) {
                            if ((c >= '0' && c <= '9') || (c == '.' && !amountBuf.toString().contains("."))) {
                                if (amountBuf.length() < 12) {
                                    amountBuf.append(c);
                                }
                            }
                        } else if (focusedField == 3) {
                            if (remarkBuf.length() < 40) {
                                remarkBuf.append(c);
                            }
                        } else if (focusedField == 0) {
                            if (c == ' ') {
                                AccountDTO chosen = AccountSelectorModal.selectSenderAccount(terminal, origAttr, reader, accounts, sourceAccount, width);
                                if (chosen != null) {
                                    sourceAccount = chosen;
                                    focusedField = 1;
                                }
                                firstRender = true;
                                continue;
                            } else if (c >= '1' && c <= '0' + Math.min(accounts.size(), 9)) {
                                int chosenIdx = c - '1';
                                sourceAccount = accounts.get(chosenIdx);
                                focusedField = 1;
                                firstRender = true;
                            }
                        } else if (focusedField == 4) {
                            if (c == ' ') {
                                selectedCategoryIdx = (selectedCategoryIdx + 1) % TRANSFER_CATEGORIES.length;
                                continue;
                            }
                        } else if (focusedField >= 5) {
                            if (c == '1') {
                                actionIndex = 0;
                                // Trigger Review & Submit
                                String normalized = normalizeAccountNumber(destAccBuf.toString());
                                if (normalized.isEmpty()) {
                                    statusMessage = "Please enter destination account number";
                                    focusedField = 1;
                                } else if (resolvedDestAcc == null) {
                                    try {
                                        Account acc = accountController.findAccountByNumber(normalized);
                                        if (acc.getAccountId().equals(sourceAccount.getAccountId())) {
                                            statusMessage = "Cannot transfer to the same account";
                                            focusedField = 1;
                                        } else {
                                            resolvedDestAcc = acc;
                                            destAccBuf.setLength(0);
                                            destAccBuf.append(acc.getAccountNumber());
                                            Optional<User> benUser = ControllerFactory.getUserRepository().findById(acc.getUserId());
                                            resolvedBeneficiaryName = benUser.map(User::getFullName).orElse("VERIFIED BENEFICIARY");
                                        }
                                    } catch (Exception e) {
                                        statusMessage = "Destination account not found: " + normalized;
                                        focusedField = 1;
                                    }
                                }

                                if (resolvedDestAcc != null) {
                                    String amtStr = amountBuf.toString().replace(",", "").replace("$", "").trim();
                                    if (amtStr.isEmpty()) {
                                        statusMessage = "Please enter transfer amount";
                                        focusedField = 2;
                                    } else {
                                        try {
                                            BigDecimal amt = new BigDecimal(amtStr);
                                            if (amt.compareTo(BigDecimal.ZERO) <= 0) {
                                                statusMessage = "Amount must be greater than zero.";
                                                focusedField = 2;
                                            } else if (sourceAccount.getBalance().compareTo(amt) < 0) {
                                                statusMessage = "Insufficient balance in source account";
                                                focusedField = 2;
                                            } else {
                                                if (remarkBuf.toString().trim().isEmpty()) {
                                                    remarkBuf.setLength(0);
                                                    remarkBuf.append("Fund Transfer");
                                                }
                                                state = ScreenState.CONFIRMATION;
                                                confirmActionIdx = 0;
                                                firstRender = true;
                                            }
                                        } catch (Exception e) {
                                            statusMessage = "Invalid transfer amount format";
                                            focusedField = 2;
                                        }
                                    }
                                }
                            } else if (c == '2') {
                                terminal.setAttributes(origAttr);
                                navigator.pop();
                                return;
                            }
                        }
                    }

                } else if (state == ScreenState.CATEGORY_SELECT) {
                    StringBuilder sb = new StringBuilder();
                    sb.append(TUIBox.top(width)).append("\n");
                    sb.append(TUIBox.line(ConsoleTheme.primary("DIGIBANK CORE > MONEY MOVEMENT > TRANSFER > SELECT CATEGORY"), width)).append("\n");
                    sb.append(TUIBox.divider(width)).append("\n");
                    sb.append(TUIBox.line("Choose transaction category using Arrow Keys:", width)).append("\n");
                    sb.append(TUIBox.emptyLine(width)).append("\n");

                    for (int i = 0; i < TRANSFER_CATEGORIES.length; i++) {
                        String label = TRANSFER_CATEGORIES[i];
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
                    sb.append(ConsoleTheme.keyGuide("[↑/↓] Navigate  •  [Enter] Confirm Category  •  [0-7] Quick Select  •  [Esc] Skip")).append("\n");

                    ScreenRenderer.render(sb.toString(), firstRender);
                    firstRender = false;

                    KeyEvent event = TUIFormHelper.readKey(reader);
                    if (event.action() == KeyAction.ESCAPE) {
                        state = ScreenState.FORM;
                        firstRender = true;
                    } else if (event.action() == KeyAction.UP) {
                        categoryHighlightIdx = (categoryHighlightIdx - 1 + TRANSFER_CATEGORIES.length) % TRANSFER_CATEGORIES.length;
                    } else if (event.action() == KeyAction.DOWN) {
                        categoryHighlightIdx = (categoryHighlightIdx + 1) % TRANSFER_CATEGORIES.length;
                    } else if (event.action() == KeyAction.ENTER) {
                        selectedCategoryIdx = categoryHighlightIdx;
                        state = ScreenState.FORM;
                        focusedField = 5; // Shift focus to Action Bar
                        actionIndex = 0;
                        firstRender = true;
                    } else if (event.action() == KeyAction.DIGIT && event.ch() >= '0' && event.ch() < '0' + TRANSFER_CATEGORIES.length) {
                        selectedCategoryIdx = event.ch() - '0';
                        state = ScreenState.FORM;
                        focusedField = 5; // Shift focus to Action Bar
                        actionIndex = 0;
                        firstRender = true;
                    }

                } else if (state == ScreenState.CONFIRMATION) {
                    BigDecimal amount = new BigDecimal(amountBuf.toString().replace(",", "").replace("$", "").trim());
                    boolean isCrossCurrency = sourceAccount.getCurrency() != resolvedDestAcc.getCurrency();

                    LiveCurrencyService liveCurrencyService = ControllerFactory.getLiveCurrencyService();
                    Map<String, BigDecimal> rates = (liveCurrencyService != null)
                            ? liveCurrencyService.getRates()
                            : CurrencyConverter.getFallbackRates();

                    BigDecimal creditAmount = isCrossCurrency
                            ? CurrencyConverter.convert(amount, sourceAccount.getCurrency().name(), resolvedDestAcc.getCurrency().name(), rates)
                            : amount;
                    BigDecimal exchangeRate = isCrossCurrency
                            ? CurrencyConverter.getExchangeRate(sourceAccount.getCurrency().name(), resolvedDestAcc.getCurrency().name(), rates)
                            : BigDecimal.ONE;

                    BigDecimal remainingBalance = sourceAccount.getBalance().subtract(amount);

                    StringBuilder sb = new StringBuilder();
                    sb.append(TUIBox.top(width)).append("\n");
                    sb.append(TUIBox.line(ConsoleTheme.primary("DIGIBANK CORE > MONEY MOVEMENT > CONFIRM TRANSFER"), width)).append("\n");
                    sb.append(TUIBox.divider(width)).append("\n");
                    sb.append(TUIBox.line("TRANSACTION VERIFICATION", width)).append("\n");
                    sb.append(TUIBox.emptyLine(width)).append("\n");

                    sb.append(TUIBox.line(String.format("  Source Account    : %s (%s - %s)",
                            sourceAccount.getAccountNumber(), sourceAccount.getAccountType(), sourceAccount.getCurrency()), width)).append("\n");
                    sb.append(TUIBox.line(String.format("  Available Balance : %s",
                            ConsoleFormatter.formatAccountBalance(sourceAccount.getBalance(), sourceAccount.getCurrency())), width)).append("\n");
                    sb.append(TUIBox.emptyLine(width)).append("\n");

                    sb.append(TUIBox.line(String.format("  Destination Acc   : %s (%s - %s)",
                            resolvedDestAcc.getAccountNumber(), resolvedDestAcc.getAccountType(), resolvedDestAcc.getCurrency()), width)).append("\n");
                    sb.append(TUIBox.line(String.format("  Beneficiary Name  : %s", resolvedBeneficiaryName), width)).append("\n");
                    sb.append(TUIBox.emptyLine(width)).append("\n");

                    sb.append(TUIBox.line(String.format("  Transfer Amount   : %s",
                            ConsoleFormatter.formatAccountBalance(amount, sourceAccount.getCurrency())), width)).append("\n");
                    sb.append(TUIBox.line(String.format("  Transfer Fee      : %s %s %s (Internal DigiBank Transfer)",
                            ConsoleFormatter.getCurrencySymbol(sourceAccount.getCurrency()), "0.00", sourceAccount.getCurrency()), width)).append("\n");
                    sb.append(TUIBox.line(String.format("  Remaining Balance : %s",
                            ConsoleFormatter.formatAccountBalance(remainingBalance, sourceAccount.getCurrency())), width)).append("\n");

                    if (isCrossCurrency) {
                        String rateFormatted = String.format("1 %s = %s %s",
                                sourceAccount.getCurrency(),
                                df.format(exchangeRate),
                                resolvedDestAcc.getCurrency());
                        String receiveFormatted = ConsoleFormatter.formatAccountBalance(creditAmount, resolvedDestAcc.getCurrency());
                        sb.append(TUIBox.line(String.format("  Live Exchange Rate: %s", rateFormatted), width)).append("\n");
                        sb.append(TUIBox.line(String.format("  Recipient Receives: %s", receiveFormatted), width)).append("\n");
                    }

                    String catName = TRANSFER_CATEGORIES[selectedCategoryIdx];
                    String memoStr = remarkBuf.toString();
                    int maxMemoLen = width - 4 - 24 - catName.length() - 4;
                    if (maxMemoLen > 3 && memoStr.length() > maxMemoLen) {
                        memoStr = memoStr.substring(0, maxMemoLen - 3) + "...";
                    }
                    String catMemo = String.format("  Category / Memo   : %s / \"%s\"", catName, memoStr);
                    if (catMemo.length() > width - 4) {
                        catMemo = catMemo.substring(0, width - 4);
                    }
                    sb.append(TUIBox.line(catMemo, width)).append("\n");
                    sb.append(TUIBox.emptyLine(width)).append("\n");

                    sb.append(TUIBox.divider(width)).append("\n");
                    sb.append(TUIBox.line("  CONFIRM EXECUTION", width)).append("\n");
                    sb.append(TUIBox.emptyLine(width)).append("\n");

                    String a1 = "[1] Authorize & Send Transfer";
                    String a2 = "[2] Back to Edit Details";
                    String confirmLine;
                    if (confirmActionIdx == 0) {
                        confirmLine = "  ▸ " + ConsoleTheme.highlight(a1) + "                 " + a2;
                    } else {
                        confirmLine = "    " + a1 + "               ▸ " + ConsoleTheme.highlight(a2);
                    }
                    sb.append(TUIBox.line(confirmLine, width)).append("\n");

                    sb.append(TUIBox.bottom(width)).append("\n");
                    sb.append(ConsoleTheme.keyGuide("[Enter] Confirm Action  •  [1/2] Instant Action  •  [Esc] Back to Edit")).append("\n");

                    ScreenRenderer.render(sb.toString(), firstRender);
                    firstRender = false;

                    KeyEvent event = TUIFormHelper.readKey(reader);
                    if (event.action() == KeyAction.ESCAPE) {
                        state = ScreenState.FORM;
                        focusedField = 5;
                        firstRender = true;
                    } else if (event.action() == KeyAction.UP || event.action() == KeyAction.DOWN
                            || event.action() == KeyAction.LEFT || event.action() == KeyAction.RIGHT
                            || event.action() == KeyAction.TAB) {
                        confirmActionIdx = (confirmActionIdx == 0) ? 1 : 0;
                    } else if (event.action() == KeyAction.ENTER) {
                        if (confirmActionIdx == 0) {
                            executeTransfer(navigator, terminal, origAttr, reader, sourceAccount, resolvedDestAcc,
                                    amount, creditAmount, isCrossCurrency, exchangeRate, remarkBuf.toString(),
                                    resolvedBeneficiaryName, width);
                            return;
                        } else {
                            state = ScreenState.FORM;
                            focusedField = 5;
                            firstRender = true;
                        }
                    } else if (event.ch() == '1') {
                        executeTransfer(navigator, terminal, origAttr, reader, sourceAccount, resolvedDestAcc,
                                amount, creditAmount, isCrossCurrency, exchangeRate, remarkBuf.toString(),
                                resolvedBeneficiaryName, width);
                        return;
                    } else if (event.ch() == '2') {
                        state = ScreenState.FORM;
                        focusedField = 5;
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

    private void executeTransfer(ScreenNavigator navigator, Terminal terminal, Attributes origAttr,
                                 NonBlockingReader reader, AccountDTO sourceAccount, Account resolvedDestAcc,
                                 BigDecimal amount, BigDecimal creditAmount, boolean isCrossCurrency,
                                 BigDecimal exchangeRate, String memo, String beneficiaryName, int width) {
        DecimalFormat df = new DecimalFormat("#,##0.00");
        UUID idempotencyKey = UUID.randomUUID();
        User userEntity = SessionManager.getCurrentUser();

        try {
            Transaction txn = ControllerFactory.getAccountService().transfer(
                    sourceAccount.getAccountId(),
                    resolvedDestAcc.getAccountId(),
                    amount,
                    sourceAccount.getCurrency(),
                    memo,
                    idempotencyKey,
                    userEntity
            );

            StringBuilder succSb = new StringBuilder();
            succSb.append(TUIBox.top(width)).append("\n");
            succSb.append(TUIBox.line(ConsoleTheme.primary("DIGIBANK CORE > MONEY MOVEMENT > TRANSFER COMPLETED"), width)).append("\n");
            succSb.append(TUIBox.divider(width)).append("\n");
            succSb.append(TUIBox.emptyLine(width)).append("\n");
            succSb.append(TUIBox.center(ConsoleTheme.success("✔ Funds Transferred Successfully!"), width)).append("\n");
            succSb.append(TUIBox.emptyLine(width)).append("\n");
            succSb.append(TUIBox.line("  Transaction ID : #" + txn.getTransactionId(), width)).append("\n");
            succSb.append(TUIBox.line("  Beneficiary    : " + ConsoleTheme.highlight(beneficiaryName), width)).append("\n");
            succSb.append(TUIBox.line(String.format("  Account Debited: %s (%s)", sourceAccount.getAccountNumber(), sourceAccount.getCurrency()), width)).append("\n");
            String debitedStr = "-" + ConsoleFormatter.formatAccountBalance(amount, sourceAccount.getCurrency());
            succSb.append(TUIBox.line(String.format("  Amount Debited : %s", debitedStr), width)).append("\n");

            if (isCrossCurrency) {
                String creditedStr = "+" + ConsoleFormatter.formatAccountBalance(creditAmount, resolvedDestAcc.getCurrency());
                succSb.append(TUIBox.line(String.format("  Amount Credited: %s", creditedStr), width)).append("\n");
                succSb.append(TUIBox.line(String.format("  Exchange Rate  : 1 %s = %s %s",
                        sourceAccount.getCurrency(), df.format(exchangeRate), resolvedDestAcc.getCurrency()), width)).append("\n");
            }
            succSb.append(TUIBox.line("  Status         : " + ConsoleTheme.success("COMPLETED"), width)).append("\n");
            succSb.append(TUIBox.emptyLine(width)).append("\n");
            succSb.append(TUIBox.divider(width)).append("\n");
            succSb.append(TUIBox.bottom(width)).append("\n");
            succSb.append(ConsoleTheme.keyGuide("[Enter] Return to Dashboard  •  [Esc] Back")).append("\n");

            ScreenRenderer.render(succSb.toString(), true);
            while (true) {
                KeyEvent doneEvt = TUIFormHelper.readKey(reader);
                if (doneEvt.action() == KeyAction.ENTER || doneEvt.action() == KeyAction.ESCAPE || doneEvt.ch() == 'b' || doneEvt.ch() == 'B') {
                    terminal.setAttributes(origAttr);
                    navigator.pop();
                    return;
                }
            }
        } catch (InsufficientBalanceException e) {
            renderErrorBox(navigator, terminal, origAttr, reader, "Transfer Denied: Insufficient balance", width);
        } catch (Exception e) {
            renderErrorBox(navigator, terminal, origAttr, reader, "Transfer Error: " + e.getMessage(), width);
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
            errSb.append(ConsoleTheme.keyGuide("[Enter/Esc] Return to Dashboard")).append("\n");
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
