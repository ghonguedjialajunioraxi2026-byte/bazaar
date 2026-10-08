package com.ecommerce.util;

import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

/**
 * Erreurs métier des servlets CRUD : les messages propres des services/DAO
 * (listés par préfixes sûrs) sont renvoyés tels quels (400) pour que le
 * dashboard explique le refus ; tout le reste (SQL technique, NPE, FK…)
 * reste générique (500) sans fuite de détail interne.
 */
public final class ServiceErrors {

    private ServiceErrors() {}

    public static void send(HttpServletResponse resp, Exception e, String... prefixes) throws IOException {
        String msg = e.getMessage();
        if (isBusinessMessage(msg, prefixes)) {
            resp.setStatus(400);
            resp.getWriter().write("{\"error\":\"" + escape(msg) + "\"}");
        } else {
            resp.setStatus(500);
            ApiResponse.internalError(resp);
        }
    }

    static boolean isBusinessMessage(String msg, String... prefixes) {
        if (msg == null || prefixes == null) return false;
        for (String p : prefixes) {
            if (p != null && msg.startsWith(p)) return true;
        }
        return false;
    }

    static String escape(String s) {
        return s.replace("\\", "\\\\").replace("\"", "'")
            .replace("\n", " ").replace("\r", "");
    }

    /** Lecture booléenne tolérante (Boolean, "true"/"1", 1) — jamais de ClassCastException. */
    public static Boolean toBoolean(Object o) {
        if (o == null) return null;
        if (o instanceof Boolean) return (Boolean) o;
        if (o instanceof Number) return ((Number) o).intValue() != 0;
        String s = String.valueOf(o).trim();
        if ("true".equalsIgnoreCase(s) || "1".equals(s)) return true;
        if ("false".equalsIgnoreCase(s) || "0".equals(s)) return false;
        return null;
    }

    /** Identifiant entier sûr (null si absent/invalide). */
    public static Integer toId(Object o) {
        if (o == null) return null;
        if (o instanceof Number) {
            int v = ((Number) o).intValue();
            return v > 0 ? v : null;
        }
        try {
            int v = Integer.parseInt(String.valueOf(o).trim());
            return v > 0 ? v : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
