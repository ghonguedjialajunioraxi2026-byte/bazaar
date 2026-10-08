package com.ecommerce.pawapay;

import com.ecommerce.util.AppException;
import com.ecommerce.util.ErrorCodes;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Toolkit PawaPay (Merchant API v2, doc officielle) : lecture seule,
 * AUCUNE persistance (pas de table : données distantes volatiles, clés
 * publiques à rotation), AUCUNE écriture comptable.
 *
 * - Active Configuration : GET /v2/active-conf[?country&operationType].
 * - Provider Availability : GET /v2/availability[?country&operationType].
 * - Predict Provider : POST /v2/predict-provider {phoneNumber} (indication,
 *   jamais garantie ni provider imposé).
 * - Public Keys : GET /v2/public-key/http (tableau [{id,key}], pas le token).
 */
public class PawaPayToolkitService {

    /** Opérations PawaPay valides pour les filtres toolkit (doc). */
    public static final java.util.Set<String> OPERATION_TYPES = new java.util.HashSet<>(
        java.util.Arrays.asList("DEPOSIT", "PAYOUT", "REFUND", "REMITTANCE"));

    /** Configuration active (admin) + horodatage lecture. */
    public Map<String, Object> activeConfiguration(String country, String operationType)
            throws AppException {
        PawaPayConfig.requireConfigured();
        String c = country == null || country.trim().isEmpty() ? null : requireCountry(country);
        String op = operationType == null || operationType.trim().isEmpty()
            ? null : requireOperationType(operationType);
        Map<String, Object> conf = new PawaPayClient().getActiveConfiguration(c, op);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("fetchedAt", java.time.OffsetDateTime.now(java.time.ZoneOffset.UTC).toString());
        out.put("environment", PawaPayConfig.getEnvironment().toUpperCase());
        out.put("configuration", conf);
        return out;
    }

    /** Disponibilité providers (admin) + résumé compact par provider. */
    public Map<String, Object> availability(String country, String operationType)
            throws AppException {
        PawaPayConfig.requireConfigured();
        String c = country == null || country.trim().isEmpty() ? null : requireCountry(country);
        String op = operationType == null || operationType.trim().isEmpty()
            ? null : requireOperationType(operationType);
        Map<String, Object> raw = new PawaPayClient().getAvailability(c, op);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("fetchedAt", java.time.OffsetDateTime.now(java.time.ZoneOffset.UTC).toString());
        out.put("environment", PawaPayConfig.getEnvironment().toUpperCase());
        out.put("availability", raw);
        out.put("summary", summarizeAvailability(raw));
        return out;
    }

    /**
     * Prédiction provider (admin). MSISDN validé serveur (chiffres, indicatif
     * obligatoire, 7-15 chiffres — SANS hypothèse pays, contrairement au
     * normalize CMR des deposits). Résultat = indication uniquement.
     */
    public Map<String, Object> predict(String phoneNumber) throws AppException {
        PawaPayConfig.requireConfigured();
        String msisdn = requireMsisdn(phoneNumber);
        Map<String, Object> resp = new PawaPayClient().predictProvider(msisdn);
        Map<String, Object> out = new LinkedHashMap<>(resp);
        out.put("disclaimer",
            "Indication PawaPay (taux d'erreur doc 0.12 %) : ni garantie de"
                + " disponibilité, ni provider imposé à un Deposit/Payout.");
        return out;
    }

    /** Clés publiques callbacks (admin) : jamais le token, jamais persistées. */
    public Map<String, Object> publicKeys() throws AppException {
        PawaPayConfig.requireConfigured();
        List<Map<String, Object>> keys = new PawaPayClient().getPublicKeys();
        List<Map<String, String>> safe = new ArrayList<>();
        for (Map<String, Object> k : keys) {
            Map<String, String> m = new LinkedHashMap<>();
            m.put("id", k.get("id") == null ? null : String.valueOf(k.get("id")));
            m.put("key", k.get("key") == null ? null : String.valueOf(k.get("key")));
            safe.add(m);
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("fetchedAt", java.time.OffsetDateTime.now(java.time.ZoneOffset.UTC).toString());
        out.put("environment", PawaPayConfig.getEnvironment().toUpperCase());
        out.put("count", safe.size());
        out.put("keys", safe);
        return out;
    }

    // ------------------------------------------------------------------
    // Validation pure (testée)
    // ------------------------------------------------------------------

    public static String requireCountry(String country) throws AppException {
        if (country == null || !country.trim().matches("[A-Z]{3}")) {
            throw new AppException(ErrorCodes.VALIDATION_ERROR, 400,
                "Pays invalide (ISO 3166-1 alpha-3, ex : CMR)");
        }
        return country.trim();
    }

    static String requireOperationType(String op) throws AppException {
        if (op == null || !OPERATION_TYPES.contains(op.trim().toUpperCase())) {
            throw new AppException(ErrorCodes.VALIDATION_ERROR, 400,
                "Opération invalide (DEPOSIT, PAYOUT, REFUND ou REMITTANCE)");
        }
        return op.trim().toUpperCase();
    }

    /** MSISDN générique : chiffres uniquement, indicatif obligatoire (10-15 chiffres). */
    static String requireMsisdn(String raw) throws AppException {
        if (raw == null) {
            throw new AppException(ErrorCodes.VALIDATION_ERROR, 400, "Numéro requis");
        }
        String d = raw.trim().replaceAll("[\\s\\-\\.\\(\\)]", "");
        if (d.startsWith("+")) d = d.substring(1);
        while (d.startsWith("00")) d = d.substring(2);
        if (!d.matches("[1-9][0-9]{9,14}")) {
            throw new AppException(ErrorCodes.VALIDATION_ERROR, 400,
                "Numéro invalide (indicatif pays obligatoire, 10-15 chiffres)");
        }
        return d;
    }

    /**
     * Résumé compact availability : {provider: {OPERATION: STATUS}}.
     * Parsing défensif (forme inattendue → résumé vide, brut conservé).
     */
    @SuppressWarnings("unchecked")
    static Map<String, Map<String, String>> summarizeAvailability(Object raw) {
        Map<String, Map<String, String>> out = new LinkedHashMap<>();
        if (!(raw instanceof Map)) return out;
        try {
            Object countries = ((Map<String, Object>) raw).get("countries");
            // La réponse /v2/availability est un TABLEAU à la racine,
            // remonté par le client sous "data" — on accepte les 3 formes.
            java.util.List<Object> list = null;
            if (countries instanceof java.util.List) list = (java.util.List<Object>) countries;
            else {
                Object data = ((Map<String, Object>) raw).get("data");
                if (data instanceof java.util.List) list = (java.util.List<Object>) data;
                else if (raw instanceof java.util.List) list = (java.util.List<Object>) raw;
            }
            if (list == null) return out;
            for (Object c : list) {
                if (!(c instanceof Map)) continue;
                Object providers = ((Map<String, Object>) c).get("providers");
                if (!(providers instanceof java.util.List)) continue;
                for (Object p : (java.util.List<Object>) providers) {
                    if (!(p instanceof Map)) continue;
                    Map<String, Object> pm = (Map<String, Object>) p;
                    Object code = pm.get("provider");
                    Object ops = pm.get("operationTypes");
                    if (!(code instanceof String)
                            || (!(ops instanceof java.util.List) && !(ops instanceof Map))) {
                        continue;
                    }
                    Map<String, String> perOp = out.computeIfAbsent(
                        (String) code, k -> new LinkedHashMap<>());
                    // Forme doc exemple : LISTE [{operationType, status}].
                    // Forme live constatée : OBJET {OPERATION: STATUS}.
                    if (ops instanceof java.util.List) {
                        for (Object o : (java.util.List<Object>) ops) {
                            if (!(o instanceof Map)) continue;
                            Map<String, Object> om = (Map<String, Object>) o;
                            Object t = om.get("operationType");
                            Object s = om.get("status");
                            if (t instanceof String && s instanceof String) {
                                perOp.put((String) t, (String) s);
                            }
                        }
                    } else if (ops instanceof Map) {
                        for (Map.Entry<String, Object> e :
                                ((Map<String, Object>) ops).entrySet()) {
                            if (e.getValue() instanceof String) {
                                perOp.put(e.getKey(), (String) e.getValue());
                            }
                        }
                    }
                }
            }
        } catch (Exception ignored) {}
        return out;
    }
}
