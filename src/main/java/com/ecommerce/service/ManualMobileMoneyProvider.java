package com.ecommerce.service;

/**
 * Mobile Money MANUEL (pratique réelle au Cameroun) : le client envoie
 * l'argent au numéro marchand affiché puis saisit la référence de
 * transaction ; un administrateur vérifie et confirme. AUCUN appel API
 * vers MTN/Orange (pas d'intégration, voir docs/payments.md).
 */
public class ManualMobileMoneyProvider implements PaymentProvider {

    @Override
    public String getId() {
        return "MANUAL_MOMO";
    }

    @Override
    public boolean supportsMethod(String method) {
        return "MOBILE_MONEY_MTN".equals(method) || "MOBILE_MONEY_ORANGE".equals(method);
    }

    @Override
    public boolean isReferenceValid(String transactionRef) {
        if (transactionRef == null) return false;
        String t = transactionRef.trim();
        // Référence opérateur : 6 à 30 caractères (lettres, chiffres, ., -, espace).
        return t.length() >= 6 && t.length() <= 30 && t.matches("[A-Za-z0-9.\\- ]+");
    }
}
