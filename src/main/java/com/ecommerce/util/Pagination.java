package com.ecommerce.util;

/**
 * Bornes serveur de pagination (Phase 20).
 * Des valeurs hostiles (page/limit négatifs ou énormes) provoquaient
 * un OFFSET négatif puis une erreur 500. Tout listing paginé doit
 * passer par ces bornes côté servlet.
 */
public final class Pagination {

    private Pagination() {}

    /** Page minimale : 1 (les valeurs <= 0 sont ramenées à 1). */
    public static int clampPage(int page) {
        return Math.max(1, page);
    }

    /**
     * Taille de page bornée : 1..maxLimit.
     * Protège contre le dump complet (limit énorme) et l'OFFSET négatif.
     */
    public static int clampLimit(int limit, int maxLimit) {
        return Math.min(maxLimit, Math.max(1, limit));
    }
}
