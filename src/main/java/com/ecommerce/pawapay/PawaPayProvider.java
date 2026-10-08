package com.ecommerce.pawapay;

import com.ecommerce.service.PaymentProvider;

/**
 * Prestataire PawaPay (collection MTN / Orange Cameroun).
 *
 * Volontairement NON enregistré dans {@code PaymentProviders.ALL} pour
 * l'instant : le flux manuel existant (MANUAL_MOMO + vérification admin)
 * reste le défaut tant que le Sandbox n'est pas validé. Le flux PawaPay
 * passe explicitement par {@link PawaPayPaymentService} (provider='PAWAPAY'
 * en base). Quand PawaPay deviendra le défaut, l'enregistrer dans
 * {@code PaymentProviders} suffira.
 */
public class PawaPayProvider implements PaymentProvider {

    private static final PawaPayProvider INSTANCE = new PawaPayProvider();

    private PawaPayProvider() {}

    public static PawaPayProvider instance() {
        return INSTANCE;
    }

    @Override
    public String getId() {
        return PawaPayConfig.PROVIDER_ID;
    }

    @Override
    public boolean supportsMethod(String method) {
        return PawaPayConfig.toPawaPayProvider(method) != null;
    }

    /**
     * Ici la "référence" attendue est le MSISDN du payeur (le flux PawaPay
     * ne demande jamais de référence opérateur manuelle).
     */
    @Override
    public boolean isReferenceValid(String transactionRef) {
        return PawaPayPaymentService.normalizeMsisdn(transactionRef) != null;
    }

    /** La confirmation arrive seule via callback/polling (pas d'admin). */
    @Override
    public boolean autoConfirmable() {
        return true;
    }
}
