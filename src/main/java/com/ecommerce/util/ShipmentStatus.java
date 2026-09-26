package com.ecommerce.util;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Machine d'état des expéditions (Phase 12), distincte des statuts commande.
 * Livraison interne uniquement (carrier INTERNAL par défaut).
 */
public final class ShipmentStatus {

    private ShipmentStatus() {}

    public static final String PREPARING = "PREPARING";
    public static final String READY_FOR_PICKUP = "READY_FOR_PICKUP";
    public static final String PICKED_UP = "PICKED_UP";
    public static final String IN_TRANSIT = "IN_TRANSIT";
    public static final String OUT_FOR_DELIVERY = "OUT_FOR_DELIVERY";
    public static final String DELIVERED = "DELIVERED";
    public static final String FAILED = "FAILED";
    public static final String RETURNED = "RETURNED";
    public static final String CANCELLED = "CANCELLED";

    private static final Map<String, List<String>> TRANSITIONS;
    static {
        Map<String, List<String>> m = new LinkedHashMap<>();
        m.put(PREPARING, Arrays.asList(READY_FOR_PICKUP, CANCELLED));
        m.put(READY_FOR_PICKUP, Arrays.asList(PICKED_UP, CANCELLED));
        m.put(PICKED_UP, Arrays.asList(IN_TRANSIT, FAILED, RETURNED));
        m.put(IN_TRANSIT, Arrays.asList(OUT_FOR_DELIVERY, FAILED, RETURNED));
        m.put(OUT_FOR_DELIVERY, Arrays.asList(DELIVERED, FAILED, RETURNED));
        m.put(DELIVERED, Collections.emptyList());
        m.put(FAILED, Collections.emptyList());
        m.put(RETURNED, Collections.emptyList());
        m.put(CANCELLED, Collections.emptyList());
        TRANSITIONS = Collections.unmodifiableMap(m);
    }

    public static boolean isKnown(String status) {
        return status != null && TRANSITIONS.containsKey(status);
    }

    public static boolean canTransition(String from, String to) {
        if (from == null || to == null) return false;
        if (from.equals(to)) return true;
        List<String> allowed = TRANSITIONS.get(from);
        return allowed != null && allowed.contains(to);
    }
}
