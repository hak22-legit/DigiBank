package com.bank.console.components;

import com.bank.console.TUISession;
import com.bank.console.theme.ConsoleTheme;
import org.jline.reader.LineReader;

import java.math.BigDecimal;

public final class ConsolePrompt {
    private ConsolePrompt() {}

    private static LineReader getReader() {
        return TUISession.getInstance().getLineReader();
    }

    private static String indent() {
        return ScreenRenderer.getLeftPaddingSpaces();
    }

    public static String promptText(String label) {
        while (true) {
            System.out.print(indent() + ConsoleTheme.BRAND_GREEN + " > " + ConsoleTheme.RESET + label + ": ");
            String input = getReader().readLine().trim();
            if (!input.isEmpty()) {
                return input;
            }
            System.out.println(indent() + ConsoleTheme.error("   Input cannot be empty. Please try again."));
        }
    }

    public static String promptOptional(String label, String defaultValue) {
        System.out.print(indent() + ConsoleTheme.BRAND_GREEN + " > " + ConsoleTheme.RESET + label +
                (defaultValue != null ? ConsoleTheme.muted(" [" + defaultValue + "]") : "") + ": ");
        String input = getReader().readLine().trim();
        return input.isEmpty() ? defaultValue : input;
    }

    public static String promptPassword(String label) {
        while (true) {
            System.out.print(indent() + ConsoleTheme.BRAND_GREEN + " > " + ConsoleTheme.RESET + label + ": ");
            String input = getReader().readLine('*').trim();
            if (!input.isEmpty()) {
                return input;
            }
            System.out.println(indent() + ConsoleTheme.error("   Password cannot be empty."));
        }
    }

    public static BigDecimal promptAmount(String label) {
        while (true) {
            System.out.print(indent() + ConsoleTheme.BRAND_GREEN + " > " + ConsoleTheme.RESET + label + " ($): ");
            String input = getReader().readLine().trim().replace("$", "").replace(",", "");
            try {
                BigDecimal amount = new BigDecimal(input);
                if (amount.compareTo(BigDecimal.ZERO) <= 0) {
                    System.out.println(indent() + ConsoleTheme.error("   Amount must be greater than zero."));
                    continue;
                }
                return amount;
            } catch (NumberFormatException e) {
                System.out.println(indent() + ConsoleTheme.error("   Invalid numeric amount format. (e.g., 250.00)"));
            }
        }
    }

    public static BigDecimal promptAmountOptional(String label, BigDecimal defaultValue) {
        while (true) {
            System.out.print(indent() + ConsoleTheme.BRAND_GREEN + " > " + ConsoleTheme.RESET + label +
                    (defaultValue != null ? ConsoleTheme.muted(" [" + defaultValue.toPlainString() + "]") : "") + " ($): ");
            String input = getReader().readLine().trim().replace("$", "").replace(",", "");
            if (input.isEmpty()) {
                return defaultValue;
            }
            try {
                BigDecimal amount = new BigDecimal(input);
                if (amount.compareTo(BigDecimal.ZERO) < 0) {
                    System.out.println(indent() + ConsoleTheme.error("   Amount cannot be negative."));
                    continue;
                }
                return amount;
            } catch (NumberFormatException e) {
                System.out.println(indent() + ConsoleTheme.error("   Invalid numeric amount format. (e.g., 250.00)"));
            }
        }
    }

    public static boolean promptConfirmation(String message) {
        System.out.print(indent() + ConsoleTheme.warning(" ? " + message + " [y/N]: "));
        String input = getReader().readLine().trim().toLowerCase();
        return input.equals("y") || input.equals("yes");
    }

    public static String promptLine(String label) {
        System.out.print(indent() + ConsoleTheme.BRAND_GREEN + " > " + ConsoleTheme.RESET + label + ": ");
        return getReader().readLine().trim();
    }

    public static String promptPasswordRaw(String label) {
        System.out.print(indent() + ConsoleTheme.BRAND_GREEN + " > " + ConsoleTheme.RESET + label + ": ");
        return getReader().readLine('*').trim();
    }

    public static void pause() {
        pause("Press [Enter] to continue...");
    }

    public static void pause(String message) {
        System.out.println();
        System.out.print(indent() + ConsoleTheme.muted(" " + message));
        getReader().readLine();
    }
}