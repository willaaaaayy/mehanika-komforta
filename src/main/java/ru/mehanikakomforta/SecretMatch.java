package ru.mehanikakomforta;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

public final class SecretMatch {

    private SecretMatch() {
    }

    public static boolean same(String expected, String given) {
        if (expected == null || expected.isBlank() || given == null) {
            return false;
        }
        return MessageDigest.isEqual(
                sha(expected),
                sha(given)
        );
    }

    private static byte[] sha(String value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }
}
