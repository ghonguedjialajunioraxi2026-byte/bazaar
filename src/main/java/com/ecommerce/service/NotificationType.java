package com.ecommerce.service;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Nomenclature centralisée des notifications (Phase 13).
 * Seuls les types réellement émis par le backend y figurent.
 * Chaque event code mappe vers : catégorie (préférences) + priorité par défaut.
 */
public final class NotificationType {

    private NotificationType() {}

    public static final class Def {
        public final String category;
        public final String priority;
        Def(String category, String priority) {
            this.category = category;
            this.priority = priority;
        }
    }

    private static final Map<String, Def> REGISTRY = new LinkedHashMap<>();

    static {
        // Commandes
        reg("ORDER_CREATED", "ORDER", "HIGH");
        reg("ORDER_CONFIRMED", "ORDER", "HIGH");
        reg("ORDER_CANCELLED", "ORDER", "HIGH");
        // Paiements
        reg("PAYMENT_PENDING", "PAYMENT", "NORMAL");
        reg("PAYMENT_SUCCEEDED", "PAYMENT", "HIGH");
        reg("PAYMENT_FAILED", "PAYMENT", "HIGH");
        reg("PAYMENT_REFUNDED", "PAYMENT", "HIGH");
        // Livraison
        reg("DELIVERY_PREPARING", "DELIVERY", "NORMAL");
        reg("DELIVERY_SHIPPED", "DELIVERY", "NORMAL");
        reg("DELIVERY_IN_TRANSIT", "DELIVERY", "NORMAL");
        reg("DELIVERY_OUT_FOR_DELIVERY", "DELIVERY", "HIGH");
        reg("DELIVERY_DELIVERED", "DELIVERY", "NORMAL");
        reg("DELIVERY_FAILED", "DELIVERY", "HIGH");
        // Livraison v2 : workflow en 2 actions (fournisseur -> client -> fournisseur)
        reg("DELIVERY_STARTED", "DELIVERY", "HIGH");
        reg("RECEIPT_CONFIRMED", "DELIVERY", "HIGH");
        reg("DELIVERY_VALIDATED", "DELIVERY", "HIGH");
        // Règlement fournisseur (suite de DELIVERY_VALIDATED)
        reg("PAYOUT_REQUESTED", "PAYMENT", "HIGH");
        reg("PAYOUT_COMPLETED", "PAYMENT", "HIGH");
        // Messagerie / négociation
        reg("MESSAGE_RECEIVED", "MESSAGE", "NORMAL");
        reg("OFFER_RECEIVED", "NEGOTIATION", "NORMAL");
        reg("OFFER_COUNTERED", "NEGOTIATION", "NORMAL");
        reg("OFFER_ACCEPTED", "NEGOTIATION", "HIGH");
        reg("OFFER_REJECTED", "NEGOTIATION", "NORMAL");
        // Produits / stock
        reg("PRODUCT_LOW_STOCK", "PRODUCT", "HIGH");
        reg("PRODUCT_OUT_OF_STOCK", "PRODUCT", "HIGH");
        reg("PRODUCT_SUBMITTED", "PRODUCT", "NORMAL");
        reg("PRODUCT_APPROVED", "PRODUCT", "NORMAL");
        reg("PRODUCT_REJECTED", "PRODUCT", "HIGH");
        // Compte / sécurité (non désactivables)
        reg("ACCOUNT_SECURITY", "SECURITY", "CRITICAL");
        reg("PASSWORD_CHANGED", "SECURITY", "CRITICAL");
        // Système / marketing
        reg("SYSTEM", "SYSTEM", "NORMAL");
        reg("MARKETING", "MARKETING", "LOW");
    }

    private static void reg(String code, String category, String priority) {
        REGISTRY.put(code, new Def(category, priority));
    }

    public static boolean isKnown(String eventCode) {
        return eventCode != null && REGISTRY.containsKey(eventCode);
    }

    public static String categoryOf(String eventCode) {
        Def d = eventCode == null ? null : REGISTRY.get(eventCode);
        return d == null ? "SYSTEM" : d.category;
    }

    public static String defaultPriorityOf(String eventCode) {
        Def d = eventCode == null ? null : REGISTRY.get(eventCode);
        return d == null ? "NORMAL" : d.priority;
    }

    public static Map<String, Def> all() {
        return Collections.unmodifiableMap(REGISTRY);
    }

    /** Catégories désactivables par l'utilisateur. Sécurité/commande/paiement critiques : forcées. */
    public static final Set<String> CRITICAL_CATEGORIES = Set.of("SECURITY");

    /** Seuil stock faible unique et explicite (réutilisé partout). */
    public static final int LOW_STOCK_THRESHOLD = 5;

    /**
     * Normalise une priorité brute vers LOW/NORMAL/HIGH/CRITICAL/URGENT.
     * URGENT historique conservé (= CRITICAL). Inconnu -> NORMAL.
     */
    public static String normalizePriority(String raw) {
        if (raw == null) return "NORMAL";
        String p = raw.trim().toUpperCase();
        switch (p) {
            case "LOW": case "NORMAL": case "HIGH": case "CRITICAL": case "URGENT":
                return "URGENT".equals(p) ? "CRITICAL" : p;
            default:
                return "NORMAL";
        }
    }

    /**
     * URL d'action sûre : relative uniquement (/...), sans schéma, sans
     * javascript:, sans protocole-relatif. Retourne null si rejetée.
     */
    public static String sanitizeActionUrl(String url) {
        if (url == null) return null;
        String u = url.trim();
        if (u.isEmpty()) return null;
        if (u.length() > 255) return null;
        if (!u.startsWith("/")) return null;
        if (u.startsWith("//")) return null;
        String low = u.toLowerCase();
        if (low.contains("javascript:") || low.contains("data:") || low.contains("vbscript:")) return null;
        if (low.contains("<") || low.contains(">") || low.contains("\"") || low.contains(" ")) return null;
        return u;
    }

    /** Tronque un champ texte (titre 200, message 2000) sans jamais lever. */
    public static String clamp(String s, int max) {
        if (s == null) return s;
        String t = s.trim();
        return t.length() > max ? t.substring(0, max) : t;
    }
}
