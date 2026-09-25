package com.ecommerce.service;

/** Paiement à la livraison : rien à payer en ligne, confirmation à la livraison. */
public class CashOnDeliveryProvider implements PaymentProvider {

    @Override
    public String getId() {
        return "COD";
    }

    @Override
    public boolean supportsMethod(String method) {
        return "PAIEMENT_LIVRAISON".equals(method);
    }

    @Override
    public boolean isReferenceValid(String transactionRef) {
        return true; // aucune référence attendue
    }
}
