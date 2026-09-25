package com.ecommerce.service;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Registre des prestataires (Phase 11). Seuls les providers implémentés
 * et honnêtes sont enregistrés. Toute méthode inconnue est rejetée.
 */
public final class PaymentProviders {

    private PaymentProviders() {}

    private static final List<PaymentProvider> ALL = Collections.unmodifiableList(Arrays.asList(
        new CashOnDeliveryProvider(),
        new ManualMobileMoneyProvider()
    ));

    /** Méthodes `payments.method` proposables au checkout. */
    public static List<String> offeredMethods() {
        return Arrays.asList("PAIEMENT_LIVRAISON", "MOBILE_MONEY_MTN", "MOBILE_MONEY_ORANGE");
    }

    public static PaymentProvider forMethod(String method) {
        for (PaymentProvider p : ALL) {
            if (p.supportsMethod(method)) return p;
        }
        return null;
    }

    public static List<PaymentProvider> all() {
        return ALL;
    }
}
