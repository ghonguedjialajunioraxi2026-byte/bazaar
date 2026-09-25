package com.ecommerce.service;

/**
 * Abstraction prestataire de paiement (Phase 11).
 * Seuls les providers réellement disponibles sont implémentés :
 * - COD (paiement à la livraison, confirmation à la livraison)
 * - MANUAL_MOMO (Mobile Money manuel vérifié par un admin)
 * PayPal / cartes / API MTN / API Orange : NON intégrés
 * (pas de credentials ni sandbox) — voir docs/payments.md.
 */
public interface PaymentProvider {

    /** Identifiant stable : COD, MANUAL_MOMO. */
    String getId();

    /** Méthodes `payments.method` gérées par ce provider. */
    boolean supportsMethod(String method);

    /** Référence de transaction fournie par le client valide pour ce provider. */
    boolean isReferenceValid(String transactionRef);

    /** Le provider peut-il confirmer automatiquement (sans action humaine) ? */
    default boolean autoConfirmable() {
        return false;
    }
}
