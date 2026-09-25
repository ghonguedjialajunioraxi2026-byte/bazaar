package com.ecommerce.util;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Machine à états des commandes (Phase 7).
 * Statuts ENUM en base : EN_ATTENTE, CONFIRMEE, EN_PREPARATION,
 * EN_LIVRAISON, LIVREE, ANNULEE.
 */
public final class OrderStatus {

    private OrderStatus() {}

    public static final String EN_ATTENTE = "EN_ATTENTE";
    public static final String CONFIRMEE = "CONFIRMEE";
    public static final String EN_PREPARATION = "EN_PREPARATION";
    public static final String EN_LIVRAISON = "EN_LIVRAISON";
    public static final String LIVREE = "LIVREE";
    public static final String ANNULEE = "ANNULEE";

    private static final Map<String, List<String>> TRANSITIONS;
    static {
        Map<String, List<String>> m = new LinkedHashMap<>();
        m.put(EN_ATTENTE, Arrays.asList(CONFIRMEE, ANNULEE));
        m.put(CONFIRMEE, Arrays.asList(EN_PREPARATION, ANNULEE));
        m.put(EN_PREPARATION, Collections.singletonList(EN_LIVRAISON));
        m.put(EN_LIVRAISON, Collections.singletonList(LIVREE));
        m.put(LIVREE, Collections.emptyList());
        m.put(ANNULEE, Collections.emptyList());
        TRANSITIONS = Collections.unmodifiableMap(m);
    }

    public static boolean isKnown(String status) {
        return status != null && TRANSITIONS.containsKey(status);
    }

    public static boolean canTransition(String from, String to) {
        if (from == null || to == null) return false;
        if (from.equals(to)) return true; // idempotent
        List<String> allowed = TRANSITIONS.get(from);
        return allowed != null && allowed.contains(to);
    }
}
