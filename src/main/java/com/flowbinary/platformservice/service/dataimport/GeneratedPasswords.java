package com.flowbinary.platformservice.service.dataimport;

import java.security.SecureRandom;

/**
 * Generates a reasonably strong temporary password for logins created during a
 * bulk import when the sheet does not supply one. The value is returned in the
 * import result so the operator can distribute it; recipients should be forced
 * to reset on first login.
 */
final class GeneratedPasswords {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String UPPER  = "ABCDEFGHJKLMNPQRSTUVWXYZ";
    private static final String LOWER  = "abcdefghijkmnpqrstuvwxyz";
    private static final String DIGITS = "23456789";
    private static final String SYMBOLS = "@#$%&*";
    private static final String ALL = UPPER + LOWER + DIGITS + SYMBOLS;

    private GeneratedPasswords() {}

    static String generate() {
        StringBuilder sb = new StringBuilder(14);
        sb.append(pick(UPPER)).append(pick(LOWER)).append(pick(DIGITS)).append(pick(SYMBOLS));
        for (int i = 4; i < 14; i++) {
            sb.append(pick(ALL));
        }
        return shuffle(sb.toString());
    }

    private static char pick(String pool) {
        return pool.charAt(RANDOM.nextInt(pool.length()));
    }

    private static String shuffle(String s) {
        char[] chars = s.toCharArray();
        for (int i = chars.length - 1; i > 0; i--) {
            int j = RANDOM.nextInt(i + 1);
            char t = chars[i];
            chars[i] = chars[j];
            chars[j] = t;
        }
        return new String(chars);
    }
}
