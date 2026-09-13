package com.bank.util;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * High-precision financial currency conversion utility.
 * Enforces strict BigDecimal arithmetic (no double/float), scale-safe rounding,
 * and USD base exchange rate calculations.
 */
public final class CurrencyConverter {

    public static final Map<String, BigDecimal> FALLBACK_RATES;

    static {
        Map<String, BigDecimal> map = new LinkedHashMap<>();
        map.put("USD", new BigDecimal("1.0000"));
        map.put("KHR", new BigDecimal("4100.0000"));
        map.put("EUR", new BigDecimal("0.9200"));
        map.put("JPY", new BigDecimal("152.4000"));
        FALLBACK_RATES = Collections.unmodifiableMap(map);
    }

    private CurrencyConverter() {}

    /**
     * Converts a monetary amount from a source currency to a destination currency using the provided rate map.
     * All rates in the map are assumed to be USD-base rates (1 USD = X Currency).
     *
     * @param amount       amount in source currency
     * @param fromCurrency ISO currency code of source account (e.g., "USD", "KHR")
     * @param toCurrency   ISO currency code of target account (e.g., "KHR", "USD")
     * @param rates        map of currency code to rate relative to 1 USD
     * @return converted amount scaled to 2 decimal places with HALF_UP rounding
     */
    public static BigDecimal convert(BigDecimal amount, String fromCurrency, String toCurrency, Map<String, BigDecimal> rates) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Amount to convert must be positive and non-null");
        }
        if (fromCurrency == null || toCurrency == null) {
            throw new IllegalArgumentException("Currency codes must not be null");
        }

        String from = fromCurrency.trim().toUpperCase();
        String to = toCurrency.trim().toUpperCase();

        if (from.equals(to)) {
            return amount.setScale(2, RoundingMode.HALF_UP);
        }

        BigDecimal fromRate = resolveRate(from, rates);
        BigDecimal toRate = resolveRate(to, rates);

        if (from.equals("USD")) {
            return amount.multiply(toRate).setScale(2, RoundingMode.HALF_UP);
        } else if (to.equals("USD")) {
            return amount.divide(fromRate, 4, RoundingMode.HALF_UP).setScale(2, RoundingMode.HALF_UP);
        } else {
            // Cross-rate calculation via USD base
            BigDecimal amountInUsd = amount.divide(fromRate, 8, RoundingMode.HALF_UP);
            return amountInUsd.multiply(toRate).setScale(2, RoundingMode.HALF_UP);
        }
    }

    /**
     * Resolves the exchange rate between two currencies (1 unit of fromCurrency = X units of toCurrency).
     *
     * @param fromCurrency source currency
     * @param toCurrency   target currency
     * @param rates        USD-base rate map
     * @return unit exchange rate scaled to 4 decimal places
     */
    public static BigDecimal getExchangeRate(String fromCurrency, String toCurrency, Map<String, BigDecimal> rates) {
        if (fromCurrency == null || toCurrency == null) {
            throw new IllegalArgumentException("Currency codes must not be null");
        }

        String from = fromCurrency.trim().toUpperCase();
        String to = toCurrency.trim().toUpperCase();

        if (from.equals(to)) {
            return BigDecimal.ONE.setScale(4, RoundingMode.HALF_UP);
        }

        BigDecimal fromRate = resolveRate(from, rates);
        BigDecimal toRate = resolveRate(to, rates);

        if (from.equals("USD")) {
            return toRate.setScale(4, RoundingMode.HALF_UP);
        } else if (to.equals("USD")) {
            return BigDecimal.ONE.divide(fromRate, 4, RoundingMode.HALF_UP);
        } else {
            return toRate.divide(fromRate, 4, RoundingMode.HALF_UP);
        }
    }

    /**
     * Retrieves the rate for a currency from the provided map, falling back to static defaults if missing.
     */
    private static BigDecimal resolveRate(String currency, Map<String, BigDecimal> rates) {
        if (rates != null && rates.containsKey(currency) && rates.get(currency) != null) {
            BigDecimal r = rates.get(currency);
            if (r.compareTo(BigDecimal.ZERO) > 0) {
                return r;
            }
        }
        if (FALLBACK_RATES.containsKey(currency)) {
            return FALLBACK_RATES.get(currency);
        }
        throw new IllegalArgumentException("Unsupported currency code for exchange: " + currency);
    }

    public static Map<String, BigDecimal> getFallbackRates() {
        return FALLBACK_RATES;
    }
}
