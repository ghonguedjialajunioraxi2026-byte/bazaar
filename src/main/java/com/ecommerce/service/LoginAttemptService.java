package com.ecommerce.service;

import java.util.concurrent.ConcurrentHashMap;

/**
 * Rate limiting anti brute-force (Phase 15).
 * Serveur, en mémoire, configurable : 5 échecs → blocage 15 min,
 * par clé (scope + identifiant + IP). Les succès réinitialisent.
 * Limite connue : multi-instances Tomcat = compteurs locaux (documenté).
 */
public class LoginAttemptService {

    public static final int MAX_FAILURES = 5;
    public static final long BLOCK_MS = 15L * 60 * 1000;
    /** Fenêtre glissante de comptage (au-delà, le compteur repart). */
    public static final long WINDOW_MS = 15L * 60 * 1000;

    private static final LoginAttemptService INSTANCE = new LoginAttemptService();

    public static LoginAttemptService get() {
        return INSTANCE;
    }

    private static final class Entry {
        int failures;
        long windowStart;
        long blockedUntil;
    }

    private final ConcurrentHashMap<String, Entry> attempts = new ConcurrentHashMap<>();

    public boolean isBlocked(String key) {
        if (key == null) return false;
        Entry e = attempts.get(key);
        if (e == null) return false;
        synchronized (e) {
            return System.currentTimeMillis() < e.blockedUntil;
        }
    }

    public void recordFailure(String key) {
        if (key == null) return;
        long now = System.currentTimeMillis();
        Entry e = attempts.computeIfAbsent(key, k -> new Entry());
        synchronized (e) {
            if (now - e.windowStart > WINDOW_MS) {
                e.failures = 0;
                e.windowStart = now;
            }
            e.failures++;
            if (e.failures >= MAX_FAILURES) {
                e.blockedUntil = now + BLOCK_MS;
            }
        }
    }

    public void recordSuccess(String key) {
        if (key == null) return;
        attempts.remove(key);
    }

    /** Clé insensible à la casse côté identifiant, IP incluse. */
    public static String key(String scope, String login, String ip) {
        String l = login == null ? "" : login.trim().toLowerCase();
        String i = ip == null ? "" : ip.trim();
        return scope + "|" + l + "|" + i;
    }

    // Testabilité.
    void clear() {
        attempts.clear();
    }
}
