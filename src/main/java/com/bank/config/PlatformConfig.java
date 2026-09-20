package com.bank.config;

import java.math.BigDecimal;

/**
 * Enterprise Platform Configuration for transaction fees, velocity thresholds, and FX spreads.
 */
public class PlatformConfig {
    private static final Object LOCK = new Object();

    // Default configuration constants
    private static final BigDecimal DEFAULT_DOMESTIC_FEE = new BigDecimal("0.00");
    private static final BigDecimal DEFAULT_WIRE_FEE_PERCENT = new BigDecimal("1.50");
    private static final BigDecimal DEFAULT_DAILY_VELOCITY = new BigDecimal("10000.00");
    private static final BigDecimal DEFAULT_HIGH_VALUE_THRESHOLD = new BigDecimal("5000.00");

    private static BigDecimal domesticP2pFee = DEFAULT_DOMESTIC_FEE;
    private static BigDecimal internationalWireFeePercent = DEFAULT_WIRE_FEE_PERCENT;
    private static BigDecimal dailyVelocityLimit = DEFAULT_DAILY_VELOCITY;
    private static BigDecimal highValueFlagThreshold = DEFAULT_HIGH_VALUE_THRESHOLD;

    public static BigDecimal getDomesticP2pFee() {
        synchronized (LOCK) {
            return domesticP2pFee;
        }
    }

    public static void setDomesticP2pFee(BigDecimal fee) {
        synchronized (LOCK) {
            domesticP2pFee = fee;
        }
    }

    public static BigDecimal getInternationalWireFeePercent() {
        synchronized (LOCK) {
            return internationalWireFeePercent;
        }
    }

    public static void setInternationalWireFeePercent(BigDecimal percent) {
        synchronized (LOCK) {
            internationalWireFeePercent = percent;
        }
    }

    public static BigDecimal getDailyVelocityLimit() {
        synchronized (LOCK) {
            return dailyVelocityLimit;
        }
    }

    public static void setDailyVelocityLimit(BigDecimal limit) {
        synchronized (LOCK) {
            dailyVelocityLimit = limit;
        }
    }

    public static BigDecimal getHighValueFlagThreshold() {
        synchronized (LOCK) {
            return highValueFlagThreshold;
        }
    }

    public static void setHighValueFlagThreshold(BigDecimal threshold) {
        synchronized (LOCK) {
            highValueFlagThreshold = threshold;
        }
    }

    public static void restoreDefaults() {
        synchronized (LOCK) {
            domesticP2pFee = DEFAULT_DOMESTIC_FEE;
            internationalWireFeePercent = DEFAULT_WIRE_FEE_PERCENT;
            dailyVelocityLimit = DEFAULT_DAILY_VELOCITY;
            highValueFlagThreshold = DEFAULT_HIGH_VALUE_THRESHOLD;
        }
    }
}
