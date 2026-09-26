package com.ecommerce.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

/**
 * Mots de passe (Phase 15).
 * Nouveau : PBKDF2-HMAC-SHA256, 210 000 itérations, sel 128 bits.
 * Format : {@code pbkdf2$210000$<sel b64>$<hash b64>}.
 * Compat : les anciens hash SHA-256 simples restent vérifiables
 * (migration transparente au prochain login via {@link #needsRehash}).
 */
public class PasswordUtil {

    private static final String PREFIX = "pbkdf2";
    private static final int ITERATIONS = 210_000;
    private static final int KEY_BITS = 256;
    private static final int SALT_BYTES = 16;

    private static final SecureRandom RANDOM = new SecureRandom();

    public static String hash(String password) {
        byte[] salt = new byte[SALT_BYTES];
        RANDOM.nextBytes(salt);
        byte[] key = pbkdf2(password, salt, ITERATIONS);
        return PREFIX + "$" + ITERATIONS + "$"
            + Base64.getEncoder().encodeToString(salt) + "$"
            + Base64.getEncoder().encodeToString(key);
    }

    public static boolean verify(String password, String hash) {
        if (password == null || hash == null) return false;
        try {
            if (hash.startsWith(PREFIX + "$")) {
                String[] parts = hash.split("\\$");
                if (parts.length != 4) return false;
                int iters = Integer.parseInt(parts[1]);
                byte[] salt = Base64.getDecoder().decode(parts[2]);
                byte[] expected = Base64.getDecoder().decode(parts[3]);
                byte[] actual = pbkdf2(password, salt, iters);
                return MessageDigest.isEqual(expected, actual);
            }
            // Héritage : SHA-256 simple (comptes créés avant Phase 15).
            return legacySha256(password).equals(hash);
        } catch (Exception e) {
            return false;
        }
    }

    /** Vrai si le hash stocké utilise l'ancien format (à migrer au login). */
    public static boolean needsRehash(String hash) {
        return hash != null && !hash.startsWith(PREFIX + "$");
    }

    private static byte[] pbkdf2(String password, byte[] salt, int iters) {
        try {
            PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), salt, iters, KEY_BITS);
            SecretKeyFactory skf = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
            byte[] out = skf.generateSecret(spec).getEncoded();
            spec.clearPassword();
            return out;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    static String legacySha256(String password) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(password.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }
}
