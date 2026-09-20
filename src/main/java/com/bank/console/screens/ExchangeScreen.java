package com.bank.console.screens;

import com.bank.console.ControllerFactory;
import com.bank.console.ScreenNavigator;
import com.bank.console.TUISession;
import com.bank.console.components.ScreenRenderer;
import com.bank.console.components.TUIBox;
import com.bank.console.components.TUIFormHelper;
import com.bank.console.components.TUIFormHelper.KeyAction;
import com.bank.console.components.TUIFormHelper.KeyEvent;
import com.bank.console.components.TUILayout;
import com.bank.console.theme.ConsoleTheme;
import com.bank.controller.AccountController;
import com.bank.service.LiveCurrencyService;
import com.bank.ui.Ansi;
import com.bank.util.CurrencyConverter;
import org.jline.terminal.Attributes;
import org.jline.terminal.Terminal;
import org.jline.utils.NonBlockingReader;

import java.io.IOException;
import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.util.Map;

/**
 * SCREEN 4: CURRENCY EXCHANGE BOARD & CALCULATOR (82 Columns)
 * Integrated with LiveCurrencyService REST client, pure in-place keyboard navigation,
 * real-time conversion calculations, and zero dangling CLI prompts.
 */
public class ExchangeScreen implements Screen {
    private static final String[] CURRENCIES = {"USD", "KHR", "EUR", "JPY"};

    private final AccountController accountController;
    private final LiveCurrencyService liveCurrencyService;

    private int sourceCurIndex = 0; // USD
    private int targetCurIndex = 1; // KHR
    private final StringBuilder amountBuf = new StringBuilder("500.00");
    private String statusFeedback = null;

    public ExchangeScreen() {
        this(ControllerFactory.getAccountController(), ControllerFactory.getLiveCurrencyService());
    }

    public ExchangeScreen(AccountController accountController) {
        this(accountController, ControllerFactory.getLiveCurrencyService());
    }

    public ExchangeScreen(AccountController accountController, LiveCurrencyService liveCurrencyService) {
        this.accountController = accountController;
        this.liveCurrencyService = (liveCurrencyService != null) ? liveCurrencyService : new LiveCurrencyService();
    }

    @Override
    public void render(ScreenNavigator navigator, TUISession session) {
        int width = TUILayout.APP_WIDTH;
        DecimalFormat df = new DecimalFormat("#,##0.00");

        Terminal terminal = session.getTerminal();
        Attributes origAttributes = terminal.enterRawMode();
        NonBlockingReader reader = terminal.reader();

        // 4 fields:
        // 0: Source Currency Radio
        // 1: Target Currency Radio
        // 2: Amount to Convert Field
        // 3: Actions Bar (0: Refresh, 1: Swap, 2: Reset)
        int focusedField = 2; // Default to Amount field as shown in mockup
        int actionIdx = 0;
        boolean firstRender = true;

        try {
            while (true) {
                // Fetch rates (cached TTL 1-hour or local fallback)
                Map<String, BigDecimal> rates = liveCurrencyService.getRates();
                BigDecimal khrRate = CurrencyConverter.getExchangeRate("USD", "KHR", rates);
                BigDecimal eurRate = CurrencyConverter.getExchangeRate("USD", "EUR", rates);
                BigDecimal jpyRate = CurrencyConverter.getExchangeRate("USD", "JPY", rates);

                // Calculate conversion
                BigDecimal amountVal = BigDecimal.ZERO;
                try {
                    String clean = amountBuf.toString().trim();
                    if (!clean.isEmpty()) {
                        amountVal = new BigDecimal(clean);
                    }
                } catch (Exception ignored) {}

                String sourceCurrency = CURRENCIES[sourceCurIndex];
                String targetCurrency = CURRENCIES[targetCurIndex];
                BigDecimal estimatedReturn = BigDecimal.ZERO;
                if (amountVal.compareTo(BigDecimal.ZERO) > 0) {
                    estimatedReturn = CurrencyConverter.convert(amountVal, sourceCurrency, targetCurrency, rates);
                }

                // Render Box Frame
                StringBuilder sb = new StringBuilder();
                sb.append(TUIBox.top(width)).append("\n");
                sb.append(TUIBox.line(ConsoleTheme.primary("DIGIBANK CORE > CURRENCY EXCHANGE & CONVERSION CALCULATOR"), width)).append("\n");
                sb.append(TUIBox.divider(width)).append("\n");
                sb.append(TUIBox.line("LIVE SPOT RATES (BASE: USD) • SOURCE: open.er-api.com", width)).append("\n");
                sb.append(TUIBox.emptyLine(width)).append("\n");

                // Table Header & Divider
                String tableHeader = String.format("  %-15s %-22s %22s", "Currency Code", "Name", "Spot Rate (1 USD)");
                sb.append(TUIBox.line(Ansi.cyan(tableHeader), width)).append("\n");
                sb.append(TUIBox.line("  " + "─".repeat(76), width)).append("\n");

                // Table Rows
                String khrVal = "៛ " + df.format(khrRate);
                String eurVal = "€ " + df.format(eurRate);
                String jpyVal = "¥ " + df.format(jpyRate);

                String khrLine = String.format("  %-15s %-22s %22s", "KHR", "Cambodian Riel", khrVal);
                String eurLine = String.format("  %-15s %-22s %22s", "EUR", "Euro", eurVal);
                String jpyLine = String.format("  %-15s %-22s %22s", "JPY", "Japanese Yen", jpyVal);
                sb.append(TUIBox.line(khrLine.replace(khrVal, Ansi.cyan(khrVal)), width)).append("\n");
                sb.append(TUIBox.line(eurLine.replace(eurVal, Ansi.cyan(eurVal)), width)).append("\n");
                sb.append(TUIBox.line(jpyLine.replace(jpyVal, Ansi.cyan(jpyVal)), width)).append("\n");
                sb.append(TUIBox.emptyLine(width)).append("\n");
                sb.append(TUIBox.divider(width)).append("\n");

                // Simulator Section
                sb.append(TUIBox.line("INSTANT EXCHANGE SIMULATOR", width)).append("\n");
                sb.append(TUIBox.emptyLine(width)).append("\n");

                // Source Currency Radio Row
                sb.append(formatRadioRow("Source Currency", sourceCurIndex, focusedField == 0, width)).append("\n");

                // Target Currency Radio Row
                sb.append(formatRadioRow("Target Currency", targetCurIndex, focusedField == 1, width)).append("\n");

                // Amount to Convert Field
                String srcSymbol = getSymbol(sourceCurrency);
                String displayAmt = srcSymbol + " " + amountBuf.toString() + (focusedField == 2 ? "|" : "");
                String plainAmtRow = String.format("  %-17s: [ %-49s ]", "Amount to Convert", displayAmt);
                if (plainAmtRow.length() > 74) {
                    plainAmtRow = plainAmtRow.substring(0, 74);
                } else {
                    plainAmtRow = String.format("%-74s", plainAmtRow);
                }
                String amtRendered = (focusedField == 2) ? ("\033[7m" + plainAmtRow + "\033[0m") : plainAmtRow;
                sb.append(TUIBox.line(amtRendered, width)).append("\n");

                // Simulator Divider
                sb.append(TUIBox.line("  " + "─".repeat(76), width)).append("\n");

                // Estimated Return & Applied Fee
                String tgtSymbol = getSymbol(targetCurrency);
                String returnStr = String.format("%s %s %s", tgtSymbol, df.format(estimatedReturn), targetCurrency);
                String returnRow = String.format("  Estimated Return  :   %s", ConsoleTheme.highlight(returnStr));
                sb.append(TUIBox.line(returnRow, width)).append("\n");

                String feeRow = "  Applied Fee       :   " + Ansi.green("$ 0.00") + " (Standard Tier - Zero Fee)";
                sb.append(TUIBox.line(feeRow, width)).append("\n");
                sb.append(TUIBox.emptyLine(width)).append("\n");
                sb.append(TUIBox.divider(width)).append("\n");

                // Action Bar
                String b1 = (focusedField == 3 && actionIdx == 0)
                        ? ("▸ " + ConsoleTheme.highlight("[1] Refresh Rates"))
                        : ("  " + (focusedField == 3 ? "[1] Refresh Rates" : ConsoleTheme.muted("[1] Refresh Rates")));
                String b2 = (focusedField == 3 && actionIdx == 1)
                        ? ("▸ " + ConsoleTheme.highlight("[2] Swap Currencies"))
                        : ("  " + (focusedField == 3 ? "[2] Swap Currencies" : ConsoleTheme.muted("[2] Swap Currencies")));
                String b3 = (focusedField == 3 && actionIdx == 2)
                        ? ("▸ " + ConsoleTheme.highlight("[3] Clear / Reset"))
                        : ("  " + (focusedField == 3 ? "[3] Clear / Reset" : ConsoleTheme.muted("[3] Clear / Reset")));

                String actionLine = "  " + b1 + "       " + b2 + "       " + b3;
                sb.append(TUIBox.line(actionLine, width)).append("\n");
                sb.append(TUIBox.bottom(width)).append("\n");

                if (statusFeedback != null) {
                    sb.append(" ").append(statusFeedback).append("\n");
                    statusFeedback = null;
                }
                sb.append(ConsoleTheme.keyGuide("[Tab/↓] Next Field  •  [Space] Select Currency  •  [Enter] Action  •  [Esc] Back")).append("\n");

                ScreenRenderer.render(sb.toString(), firstRender);
                firstRender = false;

                // Handle Key Inputs in Raw Mode
                KeyEvent event = TUIFormHelper.readKey(reader);

                if (event.action() == KeyAction.ESCAPE) {
                    terminal.setAttributes(origAttributes);
                    navigator.pop();
                    return;
                }

                // Tab & Down arrow
                if (event.action() == KeyAction.TAB || event.action() == KeyAction.DOWN) {
                    focusedField = (focusedField + 1) % 4;
                    continue;
                }

                // Shift-Tab & Up arrow
                if (event.action() == KeyAction.SHIFT_TAB || event.action() == KeyAction.UP) {
                    focusedField = (focusedField - 1 + 4) % 4;
                    continue;
                }

                // Left Arrow
                if (event.action() == KeyAction.LEFT) {
                    if (focusedField == 0) {
                        sourceCurIndex = (sourceCurIndex - 1 + CURRENCIES.length) % CURRENCIES.length;
                    } else if (focusedField == 1) {
                        targetCurIndex = (targetCurIndex - 1 + CURRENCIES.length) % CURRENCIES.length;
                    } else if (focusedField == 3) {
                        actionIdx = (actionIdx - 1 + 3) % 3;
                    }
                    continue;
                }

                // Right Arrow
                if (event.action() == KeyAction.RIGHT) {
                    if (focusedField == 0) {
                        sourceCurIndex = (sourceCurIndex + 1) % CURRENCIES.length;
                    } else if (focusedField == 1) {
                        targetCurIndex = (targetCurIndex + 1) % CURRENCIES.length;
                    } else if (focusedField == 3) {
                        actionIdx = (actionIdx + 1) % 3;
                    }
                    continue;
                }

                // Spacebar toggling
                if (event.action() == KeyAction.CHAR && event.ch() == ' ') {
                    if (focusedField == 0) {
                        sourceCurIndex = (sourceCurIndex + 1) % CURRENCIES.length;
                        continue;
                    } else if (focusedField == 1) {
                        targetCurIndex = (targetCurIndex + 1) % CURRENCIES.length;
                        continue;
                    } else if (focusedField == 3) {
                        executeAction(actionIdx);
                        continue;
                    }
                }

                // Backspace on amount
                if (event.action() == KeyAction.BACKSPACE) {
                    if (focusedField == 2 && amountBuf.length() > 0) {
                        amountBuf.deleteCharAt(amountBuf.length() - 1);
                    }
                    continue;
                }

                // Enter Key
                if (event.action() == KeyAction.ENTER) {
                    if (focusedField == 0) {
                        focusedField = 1;
                    } else if (focusedField == 1) {
                        focusedField = 2;
                    } else if (focusedField == 2) {
                        focusedField = 3;
                    } else if (focusedField == 3) {
                        executeAction(actionIdx);
                    }
                    continue;
                }

                // Digit Hotkeys
                if (event.action() == KeyAction.DIGIT || event.action() == KeyAction.CHAR) {
                    char ch = event.ch();

                    if (focusedField == 0 && ch >= '1' && ch <= '4') {
                        sourceCurIndex = ch - '1';
                        continue;
                    } else if (focusedField == 1 && ch >= '1' && ch <= '4') {
                        targetCurIndex = ch - '1';
                        continue;
                    } else if (focusedField == 3 && ch >= '1' && ch <= '3') {
                        executeAction(ch - '1');
                        continue;
                    }

                    // Typing on Amount field
                    if (focusedField == 2) {
                        if ((ch >= '0' && ch <= '9') || (ch == '.' && !amountBuf.toString().contains("."))) {
                            if (amountBuf.length() < 12) {
                                amountBuf.append(ch);
                            }
                        }
                    } else {
                        // Quick actions from other fields if user presses 1, 2, 3
                        if (ch == '1') {
                            executeAction(0);
                        } else if (ch == '2') {
                            executeAction(1);
                        } else if (ch == '3') {
                            executeAction(2);
                        } else if (ch == 'b' || ch == 'B') {
                            terminal.setAttributes(origAttributes);
                            navigator.pop();
                            return;
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

    private void executeAction(int actionIndex) {
        switch (actionIndex) {
            case 0 -> {
                liveCurrencyService.refreshRates();
                statusFeedback = ConsoleTheme.success("✔ Live spot rates refreshed from open.er-api.com.");
            }
            case 1 -> {
                int temp = sourceCurIndex;
                sourceCurIndex = targetCurIndex;
                targetCurIndex = temp;
                statusFeedback = ConsoleTheme.info("Swapped: " + CURRENCIES[sourceCurIndex] + " ⇄ " + CURRENCIES[targetCurIndex]);
            }
            case 2 -> {
                amountBuf.setLength(0);
                amountBuf.append("0.00");
                statusFeedback = ConsoleTheme.info("Amount cleared to $0.00.");
            }
        }
    }

    private String formatRadioRow(String label, int selectedIndex, boolean isFocused, int width) {
        StringBuilder rsb = new StringBuilder();
        for (int i = 0; i < CURRENCIES.length; i++) {
            String opt = (i == selectedIndex ? "(•) " : "( ) ") + CURRENCIES[i];
            if (i < CURRENCIES.length - 1) {
                rsb.append(String.format("%-14s", opt));
            } else {
                rsb.append(opt);
            }
        }
        // rsb is exactly 49 chars: "( ) USD       (•) KHR       ( ) EUR       ( ) JPY"
        String plainContent = String.format("  %-17s: [ %-49s ]", label, rsb.toString());
        if (plainContent.length() > 74) {
            plainContent = plainContent.substring(0, 74);
        } else {
            plainContent = String.format("%-74s", plainContent);
        }
        String rendered = isFocused ? ("\033[7m" + plainContent + "\033[0m") : plainContent;
        return TUIBox.line(rendered, width);
    }

    private String getSymbol(String currency) {
        return switch (currency.toUpperCase()) {
            case "KHR" -> "៛";
            case "EUR" -> "€";
            case "JPY" -> "¥";
            default -> "$";
        };
    }
}
