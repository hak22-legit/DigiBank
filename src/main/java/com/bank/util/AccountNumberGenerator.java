package com.bank.util;

import java.security.SecureRandom;

public class AccountNumberGenerator {

    private static final String PREFIX = "DGB-";
    private static final SecureRandom RANDOM = new SecureRandom();

    public static String generate() {
        StringBuilder sb = new StringBuilder(PREFIX);
        for (int i = 0; i < 9; i++) {
            sb.append(RANDOM.nextInt(10));
        }
        return sb.toString();
    }
}