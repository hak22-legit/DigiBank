package com.bank.console.screens;

import com.bank.console.ControllerFactory;
import com.bank.console.ScreenNavigator;
import com.bank.console.TUISession;
import com.bank.console.components.ConsolePrompt;
import com.bank.console.components.TUIBox;
import com.bank.console.components.TUILayout;
import com.bank.console.theme.ConsoleTheme;
import com.bank.controller.AccountController;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;

/**
 * SCREEN 4: CURRENCY EXCHANGE BOARD & CALCULATOR (82 Columns)
 */
public class ExchangeScreen implements Screen {
    private final AccountController accountController;

    private String sourceCurrency = "USD";
    private String targetCurrency = "KHR";
    private BigDecimal amount = new BigDecimal("250.00");
    private String statusFeedback = null;

    public ExchangeScreen() {
        this(ControllerFactory.getAccountController());
    }

    public ExchangeScreen(AccountController accountController) {
        this.accountController = accountController;
    }

    @Override
    public void render(ScreenNavigator navigator, TUISession session) {
        int width = TUILayout.APP_WIDTH;

        while (true) {
            session.clearScreen();

            // Rates definition
            double khrBuy = 4080.00;
            double khrSell = 4120.00;
            double eurBuy = 0.9250;
            double eurSell = 0.9310;
            double thbBuy = 35.40;
            double thbSell = 35.90;

            // Calculate estimated return
            BigDecimal estimatedReturn = BigDecimal.ZERO;
            String unitSymbol = "៛";
            if ("KHR".equalsIgnoreCase(targetCurrency)) {
                estimatedReturn = amount.multiply(BigDecimal.valueOf(khrBuy)).setScale(2, RoundingMode.HALF_UP);
                unitSymbol = "៛";
            } else if ("EUR".equalsIgnoreCase(targetCurrency)) {
                estimatedReturn = amount.multiply(BigDecimal.valueOf(eurBuy)).setScale(4, RoundingMode.HALF_UP);
                unitSymbol = "€";
            } else if ("THB".equalsIgnoreCase(targetCurrency)) {
                estimatedReturn = amount.multiply(BigDecimal.valueOf(thbBuy)).setScale(2, RoundingMode.HALF_UP);
                unitSymbol = "฿";
            }

            DecimalFormat df = new DecimalFormat("#,##0.00");
            String returnStr = String.format("%s %s %s", df.format(estimatedReturn), unitSymbol, targetCurrency.toUpperCase());
            String amountStr = "$ " + df.format(amount);

            // Render Box Frame
            System.out.println(TUIBox.top(width));
            System.out.println(TUIBox.line(ConsoleTheme.primary("DIGIBANK CORE > CURRENCY EXCHANGE & CONVERSION CALCULATOR"), width));
            System.out.println(TUIBox.divider(width));
            System.out.println(TUIBox.line("LIVE SPOT RATES (BASE: USD)", width));
            System.out.println(TUIBox.emptyLine(width));
            System.out.println(TUIBox.line("  Currency Code     Name                 Buy Rate          Sell Rate", width));
            System.out.println(TUIBox.line("  ─────────────     ────────────────     ────────────      ────────────", width));
            System.out.println(TUIBox.line("  KHR               Cambodian Riel        4,080.00 ៛        4,120.00 ៛", width));
            System.out.println(TUIBox.line("  EUR               Euro                  0.9250 €          0.9310 €", width));
            System.out.println(TUIBox.line("  THB               Thai Baht             35.40 ฿           35.90 ฿", width));
            System.out.println(TUIBox.divider(width));
            System.out.println(TUIBox.line("INSTANT EXCHANGE SIMULATOR", width));
            System.out.println(TUIBox.emptyLine(width));
            System.out.println(TUIBox.line(String.format("  Source Currency    : [ %-51s ]", sourceCurrency), width));
            System.out.println(TUIBox.line(String.format("  Target Currency    : [ %-51s ]", targetCurrency), width));
            System.out.println(TUIBox.line(String.format("  Amount to Convert  : [ %-51s ]", amountStr), width));
            System.out.println(TUIBox.line("  --------------------------------------------------------------------------", width));
            System.out.println(TUIBox.line(String.format("  Estimated Return   :   %s", ConsoleTheme.highlight(returnStr)), width));
            System.out.println(TUIBox.line("  Applied Spread Fee :   $ 0.00 (Standard Tier)", width));
            System.out.println(TUIBox.emptyLine(width));
            System.out.println(TUIBox.line("  ► " + ConsoleTheme.highlight("[R] Refresh Rates") + "      [C] Clear Input          " + ConsoleTheme.muted("[B] Back to Main Menu"), width));
            System.out.println(TUIBox.bottom(width));

            if (statusFeedback != null) {
                System.out.println(" " + statusFeedback);
                statusFeedback = null;
            }
            System.out.println(TUIBox.rule(width));

            String cmd = ConsolePrompt.promptLine("Command (or enter amount, 'T' for target currency)");
            String trimmed = cmd.trim().toLowerCase();

            if (trimmed.isEmpty() || "b".equals(trimmed) || "back".equals(trimmed) || "0".equals(trimmed)) {
                navigator.pop();
                return;
            } else if ("r".equals(trimmed) || "refresh".equals(trimmed)) {
                this.statusFeedback = ConsoleTheme.success("✔ Live spot rates refreshed successfully.");
            } else if ("c".equals(trimmed) || "clear".equals(trimmed)) {
                this.amount = BigDecimal.ZERO;
                this.statusFeedback = ConsoleTheme.info("Input amount cleared to $0.00.");
            } else if ("t".equals(trimmed) || "target".equals(trimmed)) {
                String newTarget = ConsolePrompt.promptOptional("Select Target Currency [1: KHR, 2: EUR, 3: THB]", "1");
                this.targetCurrency = switch (newTarget.trim()) {
                    case "2" -> "EUR";
                    case "3" -> "THB";
                    default -> "KHR";
                };
            } else {
                try {
                    String clean = trimmed.replace("$", "").replace(",", "");
                    BigDecimal parsed = new BigDecimal(clean);
                    if (parsed.compareTo(BigDecimal.ZERO) >= 0) {
                        this.amount = parsed;
                    }
                } catch (Exception e) {
                    this.statusFeedback = ConsoleTheme.error("Unrecognized command. Use R (refresh), C (clear), B (back), or enter amount.");
                }
            }
        }
    }
}

