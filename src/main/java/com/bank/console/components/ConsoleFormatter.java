package com.bank.console.components;

import com.bank.console.theme.ConsoleTheme;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public final class ConsoleFormatter {
    private static final DecimalFormat MONEY_FORMAT = new DecimalFormat("$#,##0.00");
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private static final DecimalFormat DECIMAL_FORMAT = new DecimalFormat("#,##0.00");
    private static final DecimalFormat INTEGER_FORMAT = new DecimalFormat("#,##0");

    private ConsoleFormatter() {}

    public static String getCurrencySymbol(String currencyCode) {
        if (currencyCode == null) return "$";
        return switch (currencyCode.trim().toUpperCase()) {
            case "KHR" -> "៛";
            case "EUR" -> "€";
            case "JPY" -> "¥";
            case "USD" -> "$";
            default -> currencyCode.trim().isEmpty() ? "$" : currencyCode.trim();
        };
    }

    public static String getCurrencySymbol(com.bank.model.enums.Currency currency) {
        if (currency == null) return "$";
        return getCurrencySymbol(currency.name());
    }

    public static String formatAmount(BigDecimal amount, String currencyCode) {
        if (amount == null) amount = BigDecimal.ZERO;
        String code = currencyCode != null ? currencyCode.trim().toUpperCase() : "USD";
        if ("KHR".equals(code) || "JPY".equals(code)) {
            if (amount.remainder(BigDecimal.ONE).compareTo(BigDecimal.ZERO) == 0) {
                return INTEGER_FORMAT.format(amount);
            }
        }
        return DECIMAL_FORMAT.format(amount);
    }

    public static String formatAmount(BigDecimal amount, com.bank.model.enums.Currency currency) {
        return formatAmount(amount, currency != null ? currency.name() : "USD");
    }

    public static String formatAccountBalance(BigDecimal amount, String currencyCode) {
        if (amount == null) amount = BigDecimal.ZERO;
        String code = currencyCode != null ? currencyCode.trim().toUpperCase() : "USD";
        String symbol = getCurrencySymbol(code);
        String formattedAmt = formatAmount(amount, code);
        return String.format("%s %s %s", symbol, formattedAmt, code);
    }

    public static String formatAccountBalance(BigDecimal amount, com.bank.model.enums.Currency currency) {
        return formatAccountBalance(amount, currency != null ? currency.name() : "USD");
    }

    public static String formatAlignedBalance(BigDecimal amount, String currencyCode) {
        if (amount == null) amount = BigDecimal.ZERO;
        String code = currencyCode != null ? currencyCode.trim().toUpperCase() : "USD";
        String symbol = getCurrencySymbol(code);
        String formattedAmt = formatAmount(amount, code);
        return String.format("%s %9s %s", symbol, formattedAmt, code);
    }

    public static String formatAlignedBalance(BigDecimal amount, com.bank.model.enums.Currency currency) {
        return formatAlignedBalance(amount, currency != null ? currency.name() : "USD");
    }

    public static String formatCurrency(BigDecimal amount) {
        if (amount == null) return "$0.00";
        return MONEY_FORMAT.format(amount);
    }

    public static String formatSignedCurrency(BigDecimal amount) {
        if (amount == null) return "$0.00";
        if (amount.compareTo(BigDecimal.ZERO) > 0) {
            return ConsoleTheme.success("+" + MONEY_FORMAT.format(amount));
        } else if (amount.compareTo(BigDecimal.ZERO) < 0) {
            return ConsoleTheme.error(MONEY_FORMAT.format(amount));
        }
        return MONEY_FORMAT.format(amount);
    }

    public static String formatDateTime(LocalDateTime dateTime) {
        return dateTime == null ? "N/A" : dateTime.format(DATE_TIME_FORMATTER);
    }

    public static String formatDate(LocalDate date) {
        return date == null ? "N/A" : date.format(DATE_FORMATTER);
    }

    public static String maskAccountNumber(String accountNumber) {
        if (accountNumber == null || accountNumber.length() < 4) return "****";
        return "****" + accountNumber.substring(accountNumber.length() - 4);
    }

    public static String progressBar(BigDecimal current, BigDecimal target, int barWidth) {
        if (target == null || target.compareTo(BigDecimal.ZERO) <= 0 || current == null) {
            return "░".repeat(barWidth) + " 0.0%";
        }

        BigDecimal ratio = current.divide(target, 4, RoundingMode.HALF_UP);
        double percentage = ratio.doubleValue() * 100.0;
        int filled = (int) Math.round((percentage / 100.0) * barWidth);
        filled = Math.max(0, Math.min(barWidth, filled));
        int empty = barWidth - filled;

        String bar = "█".repeat(filled) + "░".repeat(empty);
        String pctStr = String.format(" %.1f%%", percentage);

        if (percentage >= 100.0) {
            return ConsoleTheme.success(bar) + ConsoleTheme.success(pctStr);
        } else if (percentage >= 80.0) {
            return ConsoleTheme.warning(bar) + ConsoleTheme.warning(pctStr);
        } else {
            return ConsoleTheme.info(bar) + ConsoleTheme.muted(pctStr);
        }
    }
}