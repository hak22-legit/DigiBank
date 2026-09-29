package com.bank.util;

import com.bank.model.enums.Currency;

import java.math.BigDecimal;
import java.text.DecimalFormat;

/**
 * Enterprise currency formatting utility for DigiBank.
 * Formats monetary amounts cleanly with proper currency symbols and spacing.
 */
public final class CurrencyFormatter {
    private static final DecimalFormat DECIMAL_FORMAT = new DecimalFormat("#,##0.00");
    private static final DecimalFormat INTEGER_FORMAT = new DecimalFormat("#,##0");

    private CurrencyFormatter() {}

    public static String getCurrencySymbol(Currency currency) {
        if (currency == null) return "$";
        return switch (currency) {
            case KHR -> "៛";
            case USD -> "$";
        };
    }

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

    public static String formatAmount(BigDecimal amount, Currency currency) {
        if (amount == null) amount = BigDecimal.ZERO;
        if (currency == Currency.KHR) {
            if (amount.remainder(BigDecimal.ONE).compareTo(BigDecimal.ZERO) == 0) {
                return INTEGER_FORMAT.format(amount);
            }
        }
        return DECIMAL_FORMAT.format(amount);
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

    /**
     * Formats amount with currency symbol and a space, e.g. "$ 2.47" or "៛ 10,000".
     */
    public static String format(BigDecimal amount, Currency currency) {
        if (amount == null) amount = BigDecimal.ZERO;
        String sym = getCurrencySymbol(currency);
        return sym + " " + formatAmount(amount, currency);
    }

    public static String format(BigDecimal amount, String currencyCode) {
        if (amount == null) amount = BigDecimal.ZERO;
        String sym = getCurrencySymbol(currencyCode);
        return sym + " " + formatAmount(amount, currencyCode);
    }

    public static String format(double amount, Currency currency) {
        return format(BigDecimal.valueOf(amount), currency);
    }

    public static String format(double amount, String currencyCode) {
        return format(BigDecimal.valueOf(amount), currencyCode);
    }

    /**
     * Formats amount compact without space, e.g. "$2.47".
     */
    public static String formatCompact(BigDecimal amount, Currency currency) {
        if (amount == null) amount = BigDecimal.ZERO;
        String sym = getCurrencySymbol(currency);
        return sym + formatAmount(amount, currency);
    }

    public static String formatWithSign(BigDecimal amount, Currency currency, boolean isPositive) {
        if (amount == null) amount = BigDecimal.ZERO;
        String sign = isPositive ? "+" : "-";
        return sign + format(amount.abs(), currency);
    }

    public static String formatSigned(BigDecimal amount, Currency currency) {
        if (amount == null) amount = BigDecimal.ZERO;
        boolean isPositive = amount.compareTo(BigDecimal.ZERO) >= 0;
        return formatWithSign(amount, currency, isPositive);
    }

    public static String formatAmount(BigDecimal amount) {
        if (amount == null) return "0.00";
        return DECIMAL_FORMAT.format(amount);
    }
}
