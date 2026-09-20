package com.bank.security;

import com.bank.exception.InvalidPasswordException;

/**
 * Enterprise password complexity validator and real-time strength evaluation engine.
 * Complexity Rules:
 * 1. 8+ Chars: Minimum length of 8 characters
 * 2. Upper/Lower: Contains at least one uppercase and one lowercase letter
 * 3. Number: Contains at least one numeric digit (0-9)
 * 4. Sym: Contains at least one symbol / special character
 */
public final class PasswordValidator {

    public static final int MIN_LENGTH = 8;
    public static final int METER_WIDTH = 20;

    private PasswordValidator() {}

    public record PasswordEvaluation(
            boolean lengthMet,
            boolean upperLowerMet,
            boolean numberMet,
            boolean symbolMet,
            int score,
            String strengthLabel,
            String meterBar,
            boolean isValid
    ) {}

    public static PasswordEvaluation evaluate(String password) {
        if (password == null || password.isEmpty()) {
            return new PasswordEvaluation(
                    false, false, false, false, 0, "VERY WEAK", "░".repeat(METER_WIDTH), false
            );
        }

        boolean lengthMet = password.length() >= MIN_LENGTH;
        boolean hasUpper = password.chars().anyMatch(Character::isUpperCase);
        boolean hasLower = password.chars().anyMatch(Character::isLowerCase);
        boolean upperLowerMet = hasUpper && hasLower;
        boolean numberMet = password.chars().anyMatch(Character::isDigit);
        boolean symbolMet = password.chars().anyMatch(ch -> !Character.isLetterOrDigit(ch) && !Character.isWhitespace(ch));

        int score = 0;
        if (lengthMet) score++;
        if (upperLowerMet) score++;
        if (numberMet) score++;
        if (symbolMet) score++;

        int filled = (score * METER_WIDTH) / 4;
        int empty = METER_WIDTH - filled;
        String meterBar = "█".repeat(filled) + "░".repeat(empty);

        String label = switch (score) {
            case 4 -> "STRONG";
            case 3 -> "GOOD";
            case 2 -> "FAIR";
            case 1 -> "WEAK";
            default -> "VERY WEAK";
        };

        boolean isValid = (score == 4);

        return new PasswordEvaluation(
                lengthMet, upperLowerMet, numberMet, symbolMet, score, label, meterBar, isValid
        );
    }

    public static boolean isValid(String password) {
        return evaluate(password).isValid();
    }

    public static void validate(String password) {
        PasswordEvaluation eval = evaluate(password);
        if (!eval.isValid()) {
            StringBuilder sb = new StringBuilder("Password does not meet enterprise complexity requirements: ");
            if (!eval.lengthMet()) sb.append("Must be at least 8 characters; ");
            if (!eval.upperLowerMet()) sb.append("Must contain both uppercase and lowercase letters; ");
            if (!eval.numberMet()) sb.append("Must contain at least one digit; ");
            if (!eval.symbolMet()) sb.append("Must contain at least one special symbol; ");
            throw new InvalidPasswordException(sb.toString().trim());
        }
    }
}
