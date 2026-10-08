package com.ecommerce.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Vérification email en 2 étapes — Espace Fournisseur BAZAAR.
 *
 * Après email + mot de passe valides, un code aléatoire à 6 chiffres est
 * envoyé à l'adresse du compte. La session n'est créée qu'après saisie
 * du code. Sans ce code, aucun accès côté fournisseur.
 *
 * - Code : 6 chiffres (100000-999999), SecureRandom, validité 10 min.
 * - Stockage : mémoire serveur (même limite connue que LoginAttemptService :
 *   multi-instances = codes locaux). Hash SHA-256, jamais en clair.
 * - Garde-fous : 5 essais max par code, renvoi limité (cooldown 60 s).
 * - Best-effort : ne lève jamais vers l'appelant sauf usage invalide.
 */
public class SupplierLoginCodeService {

    public static final int CODE_LENGTH = 6;
    public static final long CODE_TTL_MS = 10L * 60 * 1000;
    public static final int MAX_ATTEMPTS = 5;
    public static final long RESEND_COOLDOWN_MS = 60L * 1000;

    public enum VerifyResult {
        OK,
        NO_CODE,
        EXPIRED,
        TOO_MANY_ATTEMPTS,
        INVALID
    }

    static final class Entry {
        String codeHash;
        int supplierId;
        long expiresAt;
        long lastSentAt;
        int attempts;
    }

    private final ConcurrentHashMap<String, Entry> codes = new ConcurrentHashMap<>();
    private final SecureRandom random = new SecureRandom();

    private static final SupplierLoginCodeService INSTANCE = new SupplierLoginCodeService();

    public static SupplierLoginCodeService get() {
        return INSTANCE;
    }

    /** Génère un code à 6 chiffres et le stocke (hashé). Retourne le code en clair pour l'envoi email. */
    public String requestCode(int supplierId, String email) {
        if (email == null || email.trim().isEmpty() || supplierId <= 0) return null;
        String key = normalize(email);
        String code = String.format("%06d", random.nextInt(1_000_000));
        // Garantir 6 caractères : nextInt peut donner < 100000 -> formaté avec zéros.
        Entry e = new Entry();
        e.codeHash = sha256(code);
        e.supplierId = supplierId;
        long now = System.currentTimeMillis();
        e.expiresAt = now + CODE_TTL_MS;
        e.lastSentAt = now;
        e.attempts = 0;
        codes.put(key, e);
        return code;
    }

    /** Cooldown anti-spam de renvoi : true si un renvoi est autorisé maintenant. */
    public boolean canResend(String email) {
        if (email == null) return false;
        Entry e = codes.get(normalize(email));
        if (e == null) return true;
        return System.currentTimeMillis() - e.lastSentAt >= RESEND_COOLDOWN_MS;
    }

    /** Secondes restantes avant un renvoi autorisé (0 = autorisé). */
    public long resendWaitSeconds(String email) {
        if (email == null) return 0;
        Entry e = codes.get(normalize(email));
        if (e == null) return 0;
        long wait = RESEND_COOLDOWN_MS - (System.currentTimeMillis() - e.lastSentAt);
        return wait <= 0 ? 0 : (wait + 999) / 1000;
    }

    /** Renvoie un NOUVEAU code (invalide l'ancien). Retourne le code en clair ou null si cooldown. */
    public String resendCode(int supplierId, String email) {
        if (!canResend(email)) return null;
        return requestCode(supplierId, email);
    }

    /** Vérifie le code saisi. En cas de succès le code est consommé (usage unique). */
    public VerifyResult verify(String email, String code) {
        if (email == null || code == null) return VerifyResult.NO_CODE;
        String key = normalize(email);
        Entry e = codes.get(key);
        if (e == null) return VerifyResult.NO_CODE;
        long now = System.currentTimeMillis();
        if (now > e.expiresAt) {
            codes.remove(key);
            return VerifyResult.EXPIRED;
        }
        if (e.attempts >= MAX_ATTEMPTS) {
            codes.remove(key);
            return VerifyResult.TOO_MANY_ATTEMPTS;
        }
        String candidate = code.trim().replaceAll("\\s+", "");
        if (!candidate.matches("\\d{6}")) {
            e.attempts++;
            if (e.attempts >= MAX_ATTEMPTS) {
                codes.remove(key);
                return VerifyResult.TOO_MANY_ATTEMPTS;
            }
            return VerifyResult.INVALID;
        }
        if (!sha256(candidate).equals(e.codeHash)) {
            e.attempts++;
            if (e.attempts >= MAX_ATTEMPTS) {
                codes.remove(key);
                return VerifyResult.TOO_MANY_ATTEMPTS;
            }
            return VerifyResult.INVALID;
        }
        codes.remove(key);
        return VerifyResult.OK;
    }

    /** Fournisseur lié au code en attente (0 si aucun / expiré). */
    public int pendingSupplierId(String email) {
        if (email == null) return 0;
        Entry e = codes.get(normalize(email));
        if (e == null || System.currentTimeMillis() > e.expiresAt) return 0;
        return e.supplierId;
    }

    /** Secondes de validité restantes (0 si aucun code). */
    public long expiresInSeconds(String email) {
        if (email == null) return 0;
        Entry e = codes.get(normalize(email));
        if (e == null) return 0;
        long left = e.expiresAt - System.currentTimeMillis();
        return left <= 0 ? 0 : left / 1000;
    }

    /** Masquage anti-énumération : "a***@exemple.com" (jamais l'email complet en réponse). */
    public static String maskEmail(String email) {
        if (email == null) return "***";
        String e = email.trim().toLowerCase();
        int at = e.indexOf('@');
        if (at <= 0) return "***";
        String local = e.substring(0, at);
        String domain = e.substring(at);
        String first = local.isEmpty() ? "*" : String.valueOf(local.charAt(0));
        return first + "***" + domain;
    }

    static String normalize(String email) {
        return email.trim().toLowerCase();
    }

    static String sha256(String v) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] d = md.digest(v.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(d.length * 2);
            for (byte b : d) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception ex) {
            return "sha256:" + v;
        }
    }

    // Testabilité.
    void clear() {
        codes.clear();
    }

    int pendingCount() {
        return codes.size();
    }
}
