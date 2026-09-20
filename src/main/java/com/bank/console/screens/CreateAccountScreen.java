package com.bank.console.screens;

import com.bank.console.ControllerFactory;
import com.bank.console.ScreenNavigator;
import com.bank.console.TUISession;
import com.bank.console.components.ConsoleFormatter;
import com.bank.console.components.ScreenRenderer;
import com.bank.console.components.TUIBox;
import com.bank.console.components.TUIFormHelper;
import com.bank.console.components.TUIFormHelper.KeyAction;
import com.bank.console.components.TUIFormHelper.KeyEvent;
import com.bank.console.components.TUILayout;
import com.bank.console.theme.ConsoleTheme;
import com.bank.controller.AccountController;
import com.bank.model.dto.AccountDTO;
import com.bank.model.dto.UserDTO;
import com.bank.model.entity.User;
import com.bank.model.enums.AccountType;
import com.bank.model.enums.Currency;
import com.bank.security.SessionManager;
import org.jline.terminal.Attributes;
import org.jline.terminal.Terminal;
import org.jline.utils.NonBlockingReader;

import java.io.IOException;
import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.util.List;

/**
 * SCREEN: SELF-SERVICE ACCOUNT CREATION (82 Columns)
 * Interactive enclosed TUI form for opening and optionally funding a new bank account.
 * Features APY rates, dynamic currency symbols, interactive funding source modal,
 * and atomic database persistence.
 */
public class CreateAccountScreen implements Screen {

    private final CustomerDashboardScreen dashboardScreen;
    private final AccountController accountController;

    private static final AccountType[] TYPES = {
            AccountType.SAVINGS,
            AccountType.CHECKING,
            AccountType.FIXED_DEPOSIT
    };

    private static final String[] TYPE_LABELS = {
            "(1) SAVINGS (High Yield 3.50% APY)",
            "(2) CHECKING (Standard Checking 0.00% APY)",
            "(3) FIXED_DEPOSIT (Term Deposit 6.00% APY)"
    };

    public CreateAccountScreen() {
        this(null, ControllerFactory.getAccountController());
    }

    public CreateAccountScreen(CustomerDashboardScreen dashboardScreen) {
        this(dashboardScreen, ControllerFactory.getAccountController());
    }

    public CreateAccountScreen(CustomerDashboardScreen dashboardScreen, AccountController accountController) {
        this.dashboardScreen = dashboardScreen;
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
        Terminal terminal = session.getTerminal();
        Attributes origAttributes = terminal.enterRawMode();
        NonBlockingReader reader = terminal.reader();

        // 5 interactive focus targets:
        // 0: Account Type
        // 1: Currency Denomination
        // 2: Initial Deposit
        // 3: Funding Source
        // 4: Action Bar (Create & Fund / Cancel)
        int focusedField = 0;
        int actionIdx = 0; // 0: Create & Fund, 1: Cancel

        int typeIdx = 0; // SAVINGS
        int currencyIdx = 0; // USD
        StringBuilder depositBuf = new StringBuilder("50.00");

        List<AccountDTO> userAccounts;
        try {
            userAccounts = accountController.getAccountsForUser(userEntity);
        } catch (Exception e) {
            userAccounts = List.of();
        }

        int fundingAccountIdx = 0;
        AccountDTO selectedFundingAccount = (userAccounts != null && !userAccounts.isEmpty())
                ? userAccounts.get(0)
                : null;

        String statusMessage = "Select account parameters and confirm to open immediately.";
        boolean isErrorStatus = false;
        boolean firstRender = true;

        try {
            while (true) {
                renderScreen(width, focusedField, actionIdx, typeIdx, currencyIdx,
                        depositBuf, selectedFundingAccount, statusMessage, isErrorStatus, firstRender);
                firstRender = false;

                KeyEvent event = TUIFormHelper.readKey(reader);

                // ESC: Cancel & Return
                if (event.action() == KeyAction.ESCAPE) {
                    terminal.setAttributes(origAttributes);
                    navigator.pop();
                    return;
                }

                // TAB / DOWN: Cycle to next field
                if (event.action() == KeyAction.TAB || event.action() == KeyAction.DOWN) {
                    focusedField = (focusedField + 1) % 5;
                    continue;
                }

                // SHIFT_TAB / UP: Cycle to previous field
                if (event.action() == KeyAction.SHIFT_TAB || event.action() == KeyAction.UP) {
                    focusedField = (focusedField - 1 + 5) % 5;
                    continue;
                }

                // LEFT / RIGHT Arrow Navigation
                if (event.action() == KeyAction.LEFT) {
                    if (focusedField == 0) {
                        typeIdx = (typeIdx - 1 + TYPES.length) % TYPES.length;
                    } else if (focusedField == 1) {
                        currencyIdx = (currencyIdx == 0) ? 1 : 0;
                    } else if (focusedField == 3 && userAccounts != null && !userAccounts.isEmpty()) {
                        fundingAccountIdx = (fundingAccountIdx - 1 + userAccounts.size()) % userAccounts.size();
                        selectedFundingAccount = userAccounts.get(fundingAccountIdx);
                    } else if (focusedField == 4) {
                        actionIdx = 0;
                    }
                    continue;
                } else if (event.action() == KeyAction.RIGHT) {
                    if (focusedField == 0) {
                        typeIdx = (typeIdx + 1) % TYPES.length;
                    } else if (focusedField == 1) {
                        currencyIdx = (currencyIdx == 0) ? 1 : 0;
                    } else if (focusedField == 3 && userAccounts != null && !userAccounts.isEmpty()) {
                        fundingAccountIdx = (fundingAccountIdx + 1) % userAccounts.size();
                        selectedFundingAccount = userAccounts.get(fundingAccountIdx);
                    } else if (focusedField == 4) {
                        actionIdx = 1;
                    }
                    continue;
                }

                // SPACEBAR toggle
                if (event.action() == KeyAction.CHAR && event.ch() == ' ') {
                    if (focusedField == 0) {
                        typeIdx = (typeIdx + 1) % TYPES.length;
                        continue;
                    } else if (focusedField == 1) {
                        currencyIdx = (currencyIdx == 0) ? 1 : 0;
                        continue;
                    } else if (focusedField == 3 && userAccounts != null && !userAccounts.isEmpty()) {
                        // Open standard 82-column account selector modal
                        AccountDTO picked = AccountSelectorModal.showModal(
                                terminal, origAttributes, reader, userAccounts, selectedFundingAccount, width,
                                "DIGIBANK CORE > ACCOUNTS > SELECT FUNDING SOURCE",
                                "AVAILABLE FUNDING ACCOUNTS", "Bal:",
                                "Tip: Initial deposit will be deducted immediately upon creation.",
                                " [↑/↓] Navigate  •  [Enter] Select  •  [Esc] Keep Current"
                        );
                        if (picked != null) {
                            selectedFundingAccount = picked;
                            fundingAccountIdx = userAccounts.indexOf(picked);
                        }
                        firstRender = true;
                        continue;
                    }
                }

                // ENTER key
                if (event.action() == KeyAction.ENTER) {
                    if (focusedField == 0) {
                        typeIdx = (typeIdx + 1) % TYPES.length;
                    } else if (focusedField == 1) {
                        currencyIdx = (currencyIdx == 0) ? 1 : 0;
                    } else if (focusedField == 2) {
                        focusedField = 3;
                    } else if (focusedField == 3) {
                        if (userAccounts != null && !userAccounts.isEmpty()) {
                            AccountDTO picked = AccountSelectorModal.showModal(
                                    terminal, origAttributes, reader, userAccounts, selectedFundingAccount, width,
                                    "DIGIBANK CORE > ACCOUNTS > SELECT FUNDING SOURCE",
                                    "AVAILABLE FUNDING ACCOUNTS", "Bal:",
                                    "Tip: Initial deposit will be deducted immediately upon creation.",
                                    " [↑/↓] Navigate  •  [Enter] Select  •  [Esc] Keep Current"
                            );
                            if (picked != null) {
                                selectedFundingAccount = picked;
                                fundingAccountIdx = userAccounts.indexOf(picked);
                            }
                            firstRender = true;
                        } else {
                            focusedField = 4;
                        }
                    } else if (focusedField == 4) {
                        if (actionIdx == 0) {
                            boolean ok = submitAccount(terminal, origAttributes, navigator, session, userEntity,
                                    TYPES[typeIdx], (currencyIdx == 0 ? Currency.USD : Currency.KHR),
                                    depositBuf.toString(), selectedFundingAccount);
                            if (ok) return;
                        } else {
                            terminal.setAttributes(origAttributes);
                            navigator.pop();
                            return;
                        }
                    }
                    continue;
                }

                // Numeric hotkeys '1', '2', '3'
                if (event.action() == KeyAction.DIGIT || event.action() == KeyAction.CHAR) {
                    char ch = event.ch();
                    if (focusedField == 0 && (ch == '1' || ch == '2' || ch == '3')) {
                        typeIdx = ch - '1';
                        continue;
                    } else if (focusedField == 1 && (ch == '1' || ch == '2')) {
                        currencyIdx = ch - '1';
                        continue;
                    } else if (focusedField == 4 && (ch == '1' || ch == '2')) {
                        if (ch == '1') {
                            boolean ok = submitAccount(terminal, origAttributes, navigator, session, userEntity,
                                    TYPES[typeIdx], (currencyIdx == 0 ? Currency.USD : Currency.KHR),
                                    depositBuf.toString(), selectedFundingAccount);
                            if (ok) return;
                        } else {
                            terminal.setAttributes(origAttributes);
                            navigator.pop();
                            return;
                        }
                        continue;
                    }
                }

                // Backspace for deposit input
                if (event.action() == KeyAction.BACKSPACE) {
                    if (focusedField == 2 && depositBuf.length() > 0) {
                        depositBuf.deleteCharAt(depositBuf.length() - 1);
                    }
                    continue;
                }

                // Typing digits/period for deposit field
                if ((event.action() == KeyAction.CHAR || event.action() == KeyAction.DIGIT) && focusedField == 2) {
                    char c = event.ch();
                    if ((c >= '0' && c <= '9') || (c == '.' && !depositBuf.toString().contains("."))) {
                        if (depositBuf.length() < 12) {
                            depositBuf.append(c);
                        }
                    }
                }
            }
        } catch (IOException e) {
            terminal.setAttributes(origAttributes);
            navigator.pop();
        } finally {
            terminal.setAttributes(origAttributes);
        }
    }

    private static String truncate(String text, int max) {
        if (text == null) return "";
        return text.length() > max ? text.substring(0, max) : text;
    }

    private static String renderField(String label, String value, String hint, boolean isFocused, int width) {
        String prefix = isFocused ? "▸ " : "  ";
        // 2 (prefix) + 17 (label) + 3 (": [ ") + 32 (value) + 4 (" ]  ") = 58 chars
        String formattedInput = String.format("%s%-17s : [ %-32s ]", prefix, label, truncate(value, 32));

        // Add hint (15 chars) -> total 74 chars
        String fullLine = String.format("%-58s %-15s", formattedInput, hint != null ? hint : "");
        fullLine = String.format("%-74s", fullLine.length() > 74 ? fullLine.substring(0, 74) : fullLine);

        if (isFocused) {
            return TUIBox.line(" \033[7m" + fullLine + "\033[0m", width);
        } else {
            return TUIBox.line(" " + fullLine, width);
        }
    }

    private void renderScreen(int width, int focusedField, int actionIdx, int typeIdx, int currencyIdx,
                              StringBuilder depositBuf, AccountDTO fundingAccount,
                              String statusMessage, boolean isErrorStatus, boolean firstRender) {
        StringBuilder sb = new StringBuilder();
        DecimalFormat df = new DecimalFormat("#,##0.00");

        // 1. Header Box Compartment
        sb.append(TUIBox.top(width)).append("\n");
        sb.append(TUIBox.line(ConsoleTheme.primary("DIGIBANK CORE > ACCOUNTS > OPEN NEW BANK ACCOUNT"), width)).append("\n");
        sb.append(TUIBox.divider(width)).append("\n");

        // 2. Specifications Compartment
        sb.append(TUIBox.line("NEW ACCOUNT SPECIFICATIONS", width)).append("\n");
        sb.append(TUIBox.emptyLine(width)).append("\n");

        // Field 0: Account Type
        String typeVal = switch (TYPES[typeIdx]) {
            case SAVINGS -> "Savings (3.50% APY)";
            case CHECKING -> "Checking (0.00% APY)";
            case FIXED_DEPOSIT -> "Fixed Deposit (6.00% APY)";
            default -> TYPES[typeIdx].name();
        };
        sb.append(renderField("Account Type", typeVal, "[Space: Toggle]", focusedField == 0, width)).append("\n");

        // Field 1: Currency
        String currVal = (currencyIdx == 0) ? "USD ($)" : "KHR (៛)";
        sb.append(renderField("Currency", currVal, "[Space: Toggle]", focusedField == 1, width)).append("\n");

        // Field 2: Initial Deposit
        String depSymbol = (currencyIdx == 0) ? "$ " : "៛ ";
        String depVal = depSymbol + depositBuf.toString() + (focusedField == 2 ? "|" : "");
        sb.append(renderField("Initial Deposit", depVal, null, focusedField == 2, width)).append("\n");

        // Field 3: Funding Source
        String fundVal;
        if (fundingAccount != null) {
            fundVal = String.format("%s (Bal: $%s)",
                    fundingAccount.getAccountNumber(), df.format(fundingAccount.getBalance()));
        } else {
            fundVal = "None (Direct Opening - $0.00)";
        }
        sb.append(renderField("Funding Source", fundVal, "[Space: Toggle]", focusedField == 3, width)).append("\n");

        sb.append(TUIBox.emptyLine(width)).append("\n");
        sb.append(TUIBox.divider(width)).append("\n");

        // 3. Terms & Conditions Compartment
        sb.append(TUIBox.line("TERMS & CONDITIONS", width)).append("\n");
        sb.append(TUIBox.emptyLine(width)).append("\n");

        sb.append(TUIBox.line("   Generated Number : DGB-XXXXXXXXX (Auto-assigned)", width)).append("\n");
        sb.append(TUIBox.line("   Maintenance Fee  : Free ($0.00 / Month)", width)).append("\n");

        String interestText = switch (TYPES[typeIdx]) {
            case SAVINGS -> "Compounded monthly (3.50% APY)";
            case CHECKING -> "Standard checking (0.00% APR)";
            case FIXED_DEPOSIT -> "Maturity Term (6.00% APR)";
            default -> "Standard interest terms";
        };
        sb.append(TUIBox.line("   Interest Payout  : " + interestText, width)).append("\n");
        sb.append(TUIBox.emptyLine(width)).append("\n");

        sb.append(TUIBox.divider(width)).append("\n");

        // 4. Action Compartment
        sb.append(TUIBox.line("ACTION", width)).append("\n");
        sb.append(TUIBox.emptyLine(width)).append("\n");

        String btn1 = (focusedField == 4 && actionIdx == 0 ? "▸ " : "  ") + "[1] Create & Fund Account";
        String btn2 = (focusedField == 4 && actionIdx == 1 ? "▸ " : "  ") + "[2] Cancel & Return";
        String leftCol = String.format("%-36s", btn1);
        String rightCol = String.format("%-36s", btn2);
        if (focusedField == 4 && actionIdx == 0) leftCol = "\033[7m" + leftCol + "\033[0m";
        if (focusedField == 4 && actionIdx == 1) rightCol = "\033[7m" + rightCol + "\033[0m";
        sb.append(TUIBox.line("  " + leftCol + "  " + rightCol, width)).append("\n");
        sb.append(TUIBox.emptyLine(width)).append("\n");

        // 5. Status Compartment
        sb.append(TUIBox.divider(width)).append("\n");
        String status = (statusMessage != null) ? statusMessage : "Review parameters. Press [Enter] or [1] to open and fund account.";
        sb.append(TUIBox.line("Status: " + status, width)).append("\n");
        sb.append(TUIBox.bottom(width)).append("\n");

        // 6. Footer Navigation Guide
        sb.append(ConsoleTheme.keyGuide("[Tab/↓] Next Field  •  [Enter] Confirm  •  [Space] Toggle  •  [Esc] Back")).append("\n");

        ScreenRenderer.render(sb.toString(), firstRender);
    }

    private boolean submitAccount(Terminal terminal, Attributes origAttributes,
                                  ScreenNavigator navigator, TUISession session, User user,
                                  AccountType type, Currency currency, String depositRaw, AccountDTO fundingAccount) {
        BigDecimal depositAmount = BigDecimal.ZERO;
        String clean = depositRaw.trim();
        if (!clean.isEmpty()) {
            try {
                depositAmount = new BigDecimal(clean);
                if (depositAmount.compareTo(BigDecimal.ZERO) < 0) {
                    return false;
                }
            } catch (Exception e) {
                return false;
            }
        }

        Long fundingId = (fundingAccount != null) ? fundingAccount.getAccountId() : null;
        if (depositAmount.compareTo(BigDecimal.ZERO) > 0 && fundingId == null) {
            return false;
        }

        try {
            AccountDTO created = accountController.createAndFundAccount(
                    user, type, currency, depositAmount, fundingId);

            terminal.setAttributes(origAttributes);
            if (dashboardScreen != null) {
                dashboardScreen.setStatusMessage("Account " + created.getAccountNumber() + " created successfully!");
            }
            navigator.pop();
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
