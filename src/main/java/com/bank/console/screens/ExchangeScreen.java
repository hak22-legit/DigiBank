package com.bank.console.screens;

import com.bank.console.ControllerFactory;
import com.bank.console.ScreenNavigator;
import com.bank.console.TUISession;
import com.bank.console.components.ConsolePrompt;
import com.bank.console.components.ScreenRenderer;
import com.bank.console.components.TUIBox;
import com.bank.console.components.TUILayout;
import com.bank.console.theme.ConsoleTheme;
import com.bank.controller.AccountController;
import com.bank.service.LiveCurrencyService;
import com.bank.util.CurrencyConverter;
import org.jline.terminal.Attributes;
import org.jline.terminal.Terminal;
import org.jline.utils.NonBlockingReader;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.util.Map;

/**
 * SCREEN 4: CURRENCY EXCHANGE BOARD & CALCULATOR (82 Columns)
 * Integrated with LiveCurrencyService REST client and pure arrow/hotkey controls.
 */
public class ExchangeScreen implements Screen {
    private final AccountController accountController;
    private final LiveCurrencyService liveCurrencyService;

    private String sourceCurrency = "USD";
    private String targetCurrency = "KHR";
    private BigDecimal amount = new BigDecimal("250.00");
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
        DecimalFormat df4 = new DecimalFormat("#,##0.0000");

        Terminal terminal = session.getTerminal();
        Attributes origAttributes = terminal.enterRawMode();
        NonBlockingReader reader = terminal.reader();
        boolean firstRender = true;

        try {
            while (true) {

                // 1. Fetch live rates (cached TTL 1-hour or fallback)
                Map<String, BigDecimal> rates = liveCurrencyService.getRates();
                BigDecimal khrRate = CurrencyConverter.getExchangeRate("USD", "KHR", rates);
                BigDecimal eurRate = CurrencyConverter.getExchangeRate("USD", "EUR", rates);
                BigDecimal thbRate = CurrencyConverter.getExchangeRate("USD", "THB", rates);

                // 2. Calculate conversion
                BigDecimal estimatedReturn = BigDecimal.ZERO;
                String unitSymbol = "$";
                if (amount != null && amount.compareTo(BigDecimal.ZERO) > 0) {
                    estimatedReturn = CurrencyConverter.convert(amount, sourceCurrency, targetCurrency, rates);
                }

                if ("KHR".equalsIgnoreCase(targetCurrency)) {
                    unitSymbol = "៛";
                } else if ("EUR".equalsIgnoreCase(targetCurrency)) {
                    unitSymbol = "€";
                } else if ("THB".equalsIgnoreCase(targetCurrency)) {
                    unitSymbol = "฿";
                }

                String returnStr = String.format("%s %s %s", df.format(estimatedReturn), unitSymbol, targetCurrency.toUpperCase());
                String amountStr = "$ " + df.format(amount != null ? amount : BigDecimal.ZERO);

                // 3. Render Box Frame (82 columns)
                StringBuilder sb = new StringBuilder();
                sb.append(TUIBox.top(width)).append("\n");
                sb.append(TUIBox.line(ConsoleTheme.primary("DIGIBANK CORE > CURRENCY EXCHANGE & CONVERSION CALCULATOR"), width)).append("\n");
                sb.append(TUIBox.divider(width)).append("\n");
                sb.append(TUIBox.line("LIVE SPOT RATES (BASE: USD) • SOURCE: open.er-api.com", width)).append("\n");
                sb.append(TUIBox.emptyLine(width)).append("\n");
                sb.append(TUIBox.line("  Currency Code     Name                 Spot Rate (1 USD)", width)).append("\n");
                sb.append(TUIBox.line("  ─────────────     ────────────────     ─────────────────────────────────", width)).append("\n");
                sb.append(TUIBox.line(String.format("  KHR               Cambodian Riel        %12s ៛", df.format(khrRate)), width)).append("\n");
                sb.append(TUIBox.line(String.format("  EUR               Euro                  %12s €", df4.format(eurRate)), width)).append("\n");
                sb.append(TUIBox.line(String.format("  THB               Thai Baht             %12s ฿", df.format(thbRate)), width)).append("\n");
                sb.append(TUIBox.divider(width)).append("\n");
                sb.append(TUIBox.line("INSTANT EXCHANGE SIMULATOR", width)).append("\n");
                sb.append(TUIBox.emptyLine(width)).append("\n");
                sb.append(TUIBox.line(String.format("  Source Currency    : [ %-51s ]", sourceCurrency), width)).append("\n");
                sb.append(TUIBox.line(String.format("  Target Currency    : [ %-51s ]", targetCurrency + " (" + unitSymbol + ")"), width)).append("\n");
                sb.append(TUIBox.line(String.format("  Amount to Convert  : [ %-51s ]", amountStr), width)).append("\n");
                sb.append(TUIBox.line("  ──────────────────────────────────────────────────────────────────────────", width)).append("\n");
                sb.append(TUIBox.line(String.format("  Estimated Return   :   %s", ConsoleTheme.highlight(returnStr)), width)).append("\n");
                sb.append(TUIBox.line("  Applied Spread Fee :   $ 0.00 (Standard Tier)", width)).append("\n");
                sb.append(TUIBox.emptyLine(width)).append("\n");
                sb.append(TUIBox.line("  ► " + ConsoleTheme.highlight("[R] Refresh Rates") +
                        "   " + ConsoleTheme.highlight("[T] Switch Currency") +
                        "   " + ConsoleTheme.highlight("[A] Set Amount") +
                        "   " + ConsoleTheme.muted("[B] Back"), width)).append("\n");
                sb.append(TUIBox.bottom(width)).append("\n");

                if (statusFeedback != null) {
                    sb.append(" ").append(statusFeedback).append("\n");
                    statusFeedback = null;
                }
                sb.append(ConsoleTheme.muted(" [R] Refresh  •  [T] Switch Currency  •  [A] Set Amount  •  [C] Clear  •  [Esc/B] Back")).append("\n");

                ScreenRenderer.render(sb.toString(), firstRender);
                firstRender = false;

                // 4. Raw key capture without blocking CLI prompts
                int ch = reader.read();

                if (ch == 27) { // ESC
                    int next = reader.read(60);
                    if (next == -2 || next == -1) {
                        navigator.pop();
                        return;
                    }
                } else if (ch == 'b' || ch == 'B') {
                    navigator.pop();
                    return;
                } else if (ch == 'r' || ch == 'R') {
                    liveCurrencyService.refreshRates();
                    this.statusFeedback = ConsoleTheme.success("✔ Live spot rates refreshed from open.er-api.com.");
                } else if (ch == 't' || ch == 'T') {
                    // Cycle target currency: KHR -> EUR -> THB -> KHR
                    this.targetCurrency = switch (targetCurrency) {
                        case "KHR" -> "EUR";
                        case "EUR" -> "THB";
                        default -> "KHR";
                    };
                    this.statusFeedback = ConsoleTheme.info("Switched target currency to " + targetCurrency + ".");
                } else if (ch == 'c' || ch == 'C') {
                    this.amount = BigDecimal.ZERO;
                    this.statusFeedback = ConsoleTheme.info("Input amount cleared to $0.00.");
                } else if (ch == 'a' || ch == 'A' || ch == '\r' || ch == '\n') {
                    terminal.setAttributes(origAttributes);
                    BigDecimal newAmt = ConsolePrompt.promptAmountOptional("Enter Amount to Convert ($)", this.amount);
                    if (newAmt != null) {
                        this.amount = newAmt;
                    }
                    origAttributes = terminal.enterRawMode();
                } else if (ch == 3) {
                    System.exit(0);
                }
            }
        } catch (Exception e) {
            navigator.pop();
        } finally {
            terminal.setAttributes(origAttributes);
        }
    }
}
