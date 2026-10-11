package com.sentinelvault.security;

import org.mindrot.jbcrypt.BCrypt;

/** Password hashing with BCrypt (one-way, salted, deliberately slow) plus a password policy. */
public class PasswordUtil {

    private static final int BCRYPT_COST = 12;   // each +1 doubles the work an attacker must do

    public static String hashPassword(String plainPassword) {
        return BCrypt.hashpw(plainPassword, BCrypt.gensalt(BCRYPT_COST));
    }

    public static boolean checkPassword(String plainPassword, String hashedPassword) {
        return BCrypt.checkpw(plainPassword, hashedPassword);
    }

    /** Returns a message describing the problem, or null if the password is acceptable. */
    public static String validatePolicy(String password) {
        if (password == null || password.length() < 8) return "Password must be at least 8 characters.";
        if (password.length() > 72) return "Password must be at most 72 characters.";
        boolean letter = false, digit = false;
        for (char c : password.toCharArray()) {
            if (Character.isLetter(c)) letter = true;
            if (Character.isDigit(c)) digit = true;
        }
        if (!letter || !digit) return "Password must contain at least one letter and one digit.";
        return null;
    }
}
