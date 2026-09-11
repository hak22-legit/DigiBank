package com.bank.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class CurrencyConverterTest {

    @Test
    @DisplayName("Convert USD to KHR with custom rate map")
    void testConvertUsdToKhr() {
        Map<String, BigDecimal> rates = new HashMap<>();
        rates.put("USD", new BigDecimal("1.0000"));
        rates.put("KHR", new BigDecimal("4100.0000"));

        BigDecimal amount = new BigDecimal("40.00");
        BigDecimal converted = CurrencyConverter.convert(amount, "USD", "KHR", rates);

        assertEquals(new BigDecimal("164000.00"), converted);
    }

    @Test
    @DisplayName("Convert KHR to USD with division precision and HALF_UP rounding")
    void testConvertKhrToUsd() {
        Map<String, BigDecimal> rates = new HashMap<>();
        rates.put("USD", new BigDecimal("1.0000"));
        rates.put("KHR", new BigDecimal("4100.0000"));

        BigDecimal amount = new BigDecimal("164000.00");
        BigDecimal converted = CurrencyConverter.convert(amount, "KHR", "USD", rates);

        assertEquals(new BigDecimal("40.00"), converted);
    }

    @Test
    @DisplayName("Convert between identical currencies returns exact amount scaled to 2 decimal places")
    void testConvertSameCurrency() {
        Map<String, BigDecimal> rates = CurrencyConverter.getFallbackRates();

        BigDecimal amount = new BigDecimal("123.456");
        BigDecimal converted = CurrencyConverter.convert(amount, "USD", "USD", rates);

        assertEquals(new BigDecimal("123.46"), converted);
    }

    @Test
    @DisplayName("Cross-currency conversion (EUR to KHR via USD base)")
    void testCrossRateConversion() {
        Map<String, BigDecimal> rates = new HashMap<>();
        rates.put("USD", new BigDecimal("1.0000"));
        rates.put("EUR", new BigDecimal("0.9200"));
        rates.put("KHR", new BigDecimal("4100.0000"));

        BigDecimal amountEur = new BigDecimal("100.00");
        BigDecimal converted = CurrencyConverter.convert(amountEur, "EUR", "KHR", rates);

        // 100 / 0.92 = 108.69565217 USD; 108.69565217 * 4100 = 445652.17 KHR
        assertNotNull(converted);
        assertTrue(converted.compareTo(new BigDecimal("440000.00")) > 0);
        assertEquals(2, converted.scale());
    }

    @Test
    @DisplayName("Fallback rates used when rates map does not contain the currency")
    void testFallbackRatesUsage() {
        BigDecimal amount = new BigDecimal("50.00");
        BigDecimal converted = CurrencyConverter.convert(amount, "USD", "KHR", null);

        // Fallback KHR is 4100: 50 * 4100 = 205000.00
        assertEquals(new BigDecimal("205000.00"), converted);
    }

    @Test
    @DisplayName("Exchange rate helper returns correct unit rates")
    void testGetExchangeRate() {
        Map<String, BigDecimal> rates = new HashMap<>();
        rates.put("USD", new BigDecimal("1.0000"));
        rates.put("KHR", new BigDecimal("4100.0000"));

        BigDecimal rateUsdKhr = CurrencyConverter.getExchangeRate("USD", "KHR", rates);
        assertEquals(new BigDecimal("4100.0000"), rateUsdKhr);

        BigDecimal rateKhrUsd = CurrencyConverter.getExchangeRate("KHR", "USD", rates);
        // 1 / 4100 = 0.0002439... -> 0.0002
        assertEquals(new BigDecimal("0.0002"), rateKhrUsd);

        BigDecimal sameRate = CurrencyConverter.getExchangeRate("KHR", "KHR", rates);
        assertEquals(new BigDecimal("1.0000"), sameRate);
    }

    @Test
    @DisplayName("Invalid amount or null throws IllegalArgumentException")
    void testInvalidAmountThrows() {
        assertThrows(IllegalArgumentException.class, () ->
                CurrencyConverter.convert(null, "USD", "KHR", null));

        assertThrows(IllegalArgumentException.class, () ->
                CurrencyConverter.convert(BigDecimal.ZERO, "USD", "KHR", null));

        assertThrows(IllegalArgumentException.class, () ->
                CurrencyConverter.convert(new BigDecimal("-10.00"), "USD", "KHR", null));
    }
}
