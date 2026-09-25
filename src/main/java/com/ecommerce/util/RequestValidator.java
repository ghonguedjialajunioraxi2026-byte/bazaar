package com.ecommerce.util;

import java.util.Map;
import java.util.regex.Pattern;

/**
 * Validation serveur des entrées (Phase 2).
 * Le frontend valide aussi, mais ne constitue JAMAIS une frontière de sécurité.
 * Chaque méthode lève AppException(VALIDATION_ERROR, 400, …) en cas de rejet.
 */
public final class RequestValidator {

    private RequestValidator() {}

    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
    private static final Pattern PHONE = Pattern.compile("^[0-9+\\s]{9,15}$");

    public static String required(Map<String, Object> body, String key, String label) throws AppException {
        Object v = body == null ? null : body.get(key);
        String s = v == null ? null : String.valueOf(v).trim();
        if (s == null || s.isEmpty()) {
            throw new AppException(ErrorCodes.VALIDATION_ERROR, 400, label + " est obligatoire");
        }
        return s;
    }

    public static String optional(Map<String, Object> body, String key, int maxLen) {
        if (body == null || body.get(key) == null) return null;
        String s = String.valueOf(body.get(key)).trim();
        if (s.isEmpty()) return null;
        return s.length() > maxLen ? s.substring(0, maxLen) : s;
    }

    public static int requiredPositiveInt(Map<String, Object> body, String key, String label) throws AppException {
        Object v = body == null ? null : body.get(key);
        int n = -1;
        if (v instanceof Number) n = ((Number) v).intValue();
        else {
            try { n = Integer.parseInt(String.valueOf(v)); } catch (Exception ignored) {}
        }
        if (n <= 0) {
            throw new AppException(ErrorCodes.VALIDATION_ERROR, 400, label + " invalide");
        }
        return n;
    }

    public static void email(String value, String label) throws AppException {
        if (value == null || !EMAIL.matcher(value.trim()).matches()) {
            throw new AppException(ErrorCodes.VALIDATION_ERROR, 400, label + " invalide");
        }
    }

    public static void phone(String value, String label) throws AppException {
        if (value == null || !PHONE.matcher(value.trim()).matches()) {
            throw new AppException(ErrorCodes.VALIDATION_ERROR, 400, label + " invalide");
        }
    }

    public static void maxLen(String value, int max, String label) throws AppException {
        if (value != null && value.length() > max) {
            throw new AppException(ErrorCodes.VALIDATION_ERROR, 400, label + " trop long (max " + max + ")");
        }
    }
}
