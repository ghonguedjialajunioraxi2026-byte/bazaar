package com.ecommerce.pawapay;

import com.ecommerce.dao.OrderDAO;
import com.ecommerce.dao.PaymentDAO;
import com.ecommerce.dao.PaymentEventDAO;
import com.ecommerce.model.Order;
import com.ecommerce.model.Payment;
import com.ecommerce.util.AppException;
import com.ecommerce.util.ErrorCodes;
import com.ecommerce.util.JsonUtil;

import java.security.SecureRandom;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * Service métier PawaPay : pont entre les commandes MBOA et les dépôts
 * PawaPay (MTN / Orange Cameroun, devise XAF).
 *
 * Règles :
 * - Montant/devise recalculés serveur (total commande, jamais du navigateur).
 * - Propriété commande vérifiée (numéro + téléphone, comme le flux manuel).
 * - Ligne PawaPay persistée AVANT l'appel réseau (réconciliation garantie).
 * - ACCEPTED/PROCESSING ne valent jamais "payé" : seul COMPLETED (confirmé
 *   par callback ou par polling serveur) vaut paiement confirmé.
 * - Webhook idempotent (table payment_events + transitions conditionnelles).
 * - Aucun token, PIN ou secret en logs, en base ou en réponse API.
 */
public class PawaPayPaymentService {

    private static final Logger LOG = Logger.getLogger(PawaPayPaymentService.class.getName());
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String REF_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";

    private final PaymentDAO paymentDAO = new PaymentDAO();
    private final OrderDAO orderDAO = new OrderDAO();
    private final PaymentEventDAO eventDAO = new PaymentEventDAO();

    // ------------------------------------------------------------------
    // Création d'un dépôt
    // ------------------------------------------------------------------

    /** Résultat de création : paiement + flag doublon (retry sûr). */
    public static class DepositOutcome {
        public final Payment payment;
        /** true si une tentative ouverte existait déjà (aucun nouveau Deposit). */
        public final boolean duplicate;

        DepositOutcome(Payment payment, boolean duplicate) {
            this.payment = payment;
            this.duplicate = duplicate;
        }
    }

    /**
     * Crée une demande de paiement PawaPay pour une commande.
     * Idempotent : une tentative ouverte existante est retournée telle quelle.
     */
    public Payment createDeposit(String orderNumber, String phone, String method, String msisdn)
            throws AppException, SQLException {
        return createDepositOutcome(orderNumber, phone, method, msisdn).payment;
    }

    /** Variante informant d'un doublon (double clic, refresh, retry). */
    public DepositOutcome createDepositOutcome(String orderNumber, String phone, String method, String msisdn)
            throws AppException, SQLException {
        PawaPayConfig.requireConfigured();
        LOG.info(() -> "PawaPay Deposit request started: order=" + orderNumber + " method=" + method);
        Order order = ownedOrder(orderNumber, phone);
        if (!"EN_ATTENTE".equals(order.getStatus()) && !"CONFIRMEE".equals(order.getStatus())) {
            throw new AppException(ErrorCodes.PAYMENT_INVALID, 400, "Commande non payable à ce stade");
        }
        String provider = PawaPayConfig.toPawaPayProvider(method);
        if (provider == null) {
            throw new AppException(ErrorCodes.PROVIDER_UNAVAILABLE, 400, "Moyen de paiement indisponible via PawaPay");
        }
        String payer = normalizeMsisdn(msisdn);
        if (payer == null) {
            throw new AppException(ErrorCodes.VALIDATION_ERROR, 400, "Numéro Mobile Money invalide");
        }
        if (order.getTotal() <= 0) {
            throw new AppException(ErrorCodes.PAYMENT_INVALID, 400, "Montant de commande invalide");
        }
        try {
            Payment open = paymentDAO.findOpenPawaPayByOrderId(order.getId());
            if (open != null) {
                LOG.info(() -> "PawaPay duplicate deposit ignored: internalRef="
                    + open.getInternalReference());
                return new DepositOutcome(open, true);
            }
        } catch (SQLException e) {
            throw new AppException(ErrorCodes.DATABASE_ERROR, 500, "Impossible de traiter votre demande.", e);
        }

        String depositId = newDepositId();
        String internalRef = buildInternalReference();
        Payment p = new Payment();
        p.setOrderId(order.getId());
        p.setMethod(method);
        p.setProvider(PawaPayConfig.PROVIDER_ID);
        p.setAmount(order.getTotal());
        p.setCurrency(PawaPayConfig.CURRENCY);
        p.setIdempotenceKey(internalRef);
        p.setInternalReference(internalRef);
        p.setProviderTransactionId(depositId);
        p.setClientReferenceId(order.getOrderNumber());
        p.setOperator(provider);
        p.setPhoneNumber(payer);
        p.setStatus("PENDING");
        final int paymentId;
        try {
            paymentId = paymentDAO.createPawaPay(p);
            p.setId(paymentId);
        } catch (SQLException e) {
            // Course check-then-insert (double clic simultané) : la garde
            // UNIQUE uq_pay_open a rejeté le doublon → retourner la ligne
            // ouverte existante (AUCUN 2ᵉ appel PawaPay).
            if (isDuplicateKey(e)) {
                Payment raced = findOpenQuiet(order.getId());
                if (raced != null) {
                    LOG.info(() -> "PawaPay duplicate deposit ignored (race): internalRef="
                        + raced.getInternalReference());
                    return new DepositOutcome(raced, true);
                }
            }
            throw new AppException(ErrorCodes.DATABASE_ERROR, 500, "Impossible de traiter votre demande.", e);
        }
        LOG.info(() -> "PawaPay Deposit created: internalRef=" + internalRef
            + " order=" + order.getOrderNumber() + " provider=" + provider);

        // Appel réseau APRÈS persistance (depositId connu même en cas de timeout).
        // En cas de timeout : AUCUN nouveau Deposit créé au retry — la ligne
        // PENDING existante est retournée et le statut est réconcilié par polling.
        PawaPayClient client = new PawaPayClient();
        Map<String, Object> resp;
        try {
            // Metadata PawaPay = LISTE d'objets (doc officielle), pas un objet.
            // Aucune donnée sensible (ni PIN, ni token, ni téléphone).
            java.util.List<Map<String, String>> metadata = new java.util.ArrayList<>();
            metadata.add(singleMeta("orderId", order.getOrderNumber()));
            metadata.add(singleMeta("channel", "BAZAAR_WEB"));
            metadata.add(singleMeta("internalReference", internalRef));
            // customerMessage : 4-22 chars [A-Za-z0-9 ] (pattern officiel).
            // preAuthorisationCode : NON envoyé (réservé aux providers PREAUTH ;
            // MTN/Orange CMR sont en PROVIDER_AUTH — jamais de faux OTP).
            resp = client.initiateDeposit(depositId, String.valueOf(order.getTotal()),
                PawaPayConfig.CURRENCY, payer, provider,
                order.getOrderNumber(), "BAZAAR PAYMENT", metadata);
        } catch (AppException e) {
            // Réseau/PawaPay injoignable : tenter une réconciliation immédiate
            // (si PawaPay ne connaît pas ce depositId, la ligne PENDING passe
            // FAILED et un retry propre est possible ; sinon elle reste en
            // attente et le polling fera foi). L'erreur d'origine remonte.
            // Réseau/PawaPay injoignable : tenter une réconciliation immédiate
            // (si PawaPay ne connaît pas ce depositId, la ligne PENDING passe
            // FAILED et un retry propre est possible ; sinon elle reste en
            // attente et le polling fera foi). L'erreur d'origine remonte.
            try {
                Payment created = paymentDAO.findById(paymentId);
                if (created != null && "PENDING".equals(created.getStatus())) {
                    reconcile(created, new PawaPayClient().getDeposit(depositId));
                }
            } catch (Exception ignored) {}
            LOG.warning(() -> "PawaPay Deposit processing (initiation failed): internalRef=" + internalRef);
            throw e;
        }
        String status = str(resp.get("status"));
        try {
            if ("ACCEPTED".equals(status) || "DUPLICATE_IGNORED".equals(status)) {
                paymentDAO.updateAfterInitiation(paymentId, "PROCESSING", null);
                p.setStatus("PROCESSING");
                LOG.info(() -> "PawaPay Deposit processing: internalRef=" + internalRef);
            } else {
                String reason = failureOf(resp);
                paymentDAO.updateAfterInitiation(paymentId, "FAILED", reason);
                p.setStatus("FAILED");
                p.setFailureReason(reason);
                LOG.info(() -> "PawaPay Deposit failed at initiation: internalRef=" + internalRef);
            }
        } catch (SQLException e) {
            throw new AppException(ErrorCodes.DATABASE_ERROR, 500, "Impossible de traiter votre demande.", e);
        }
        return new DepositOutcome(p, false);
    }

    private static Map<String, String> singleMeta(String key, String value) {
        Map<String, String> m = new LinkedHashMap<>();
        m.put(key, value);
        return m;
    }

    // ------------------------------------------------------------------
    // Statut (polling serveur = source de vérité)
    // ------------------------------------------------------------------

    /** Statut d'un dépôt par depositId, réconcilié en base si final. */
    public Map<String, Object> getDepositStatus(String depositId) throws AppException, SQLException {
        PawaPayConfig.requireConfigured();
        if (depositId == null || depositId.trim().isEmpty()) {
            throw new AppException(ErrorCodes.VALIDATION_ERROR, 400, "Identifiant de paiement requis");
        }
        Payment p;
        try {
            p = paymentDAO.findByProviderTransactionId(depositId.trim());
        } catch (SQLException e) {
            throw new AppException(ErrorCodes.DATABASE_ERROR, 500, "Impossible de traiter votre demande.", e);
        }
        if (p == null) {
            throw new AppException(ErrorCodes.PAYMENT_NOT_FOUND, 404, "Paiement introuvable");
        }
        Map<String, Object> remote = new PawaPayClient().getDeposit(depositId.trim());
        reconcile(p, remote);
        try {
            p = paymentDAO.findById(p.getId());
        } catch (SQLException e) {
            throw new AppException(ErrorCodes.DATABASE_ERROR, 500, "Impossible de traiter votre demande.", e);
        }
        return maskedView(p);
    }

    /** Statut du paiement PawaPay d'une commande (propriété vérifiée). */
    public Map<String, Object> getOrderPaymentStatus(String orderNumber, String phone)
            throws AppException, SQLException {
        Order order = ownedOrder(orderNumber, phone);
        Payment p;
        try {
            p = paymentDAO.findOpenPawaPayByOrderId(order.getId());
            if (p == null) p = latestPawaPay(order.getId());
        } catch (SQLException e) {
            throw new AppException(ErrorCodes.DATABASE_ERROR, 500, "Impossible de traiter votre demande.", e);
        }
        if (p == null) {
            throw new AppException(ErrorCodes.PAYMENT_NOT_FOUND, 404, "Aucun paiement PawaPay initié");
        }
        if (PawaPayConfig.isConfigured() && !isFinalStatus(p.getStatus())) {
            final String ref = p.getInternalReference();
            try {
                Map<String, Object> remote = new PawaPayClient().getDeposit(p.getProviderTransactionId());
                reconcile(p, remote);
                p = paymentDAO.findById(p.getId());
            } catch (AppException | SQLException e) {
                // Polling en panne : retourner le dernier statut connu.
                LOG.warning(() -> "PawaPay status check failed for " + ref);
            }
        }
        return maskedView(p);
    }

    // ------------------------------------------------------------------
    // Webhook / callback
    // ------------------------------------------------------------------

    /**
     * Traite un callback PawaPay ({depositId, status, ...}).
     * Toujours idempotent : un doublon retourne {"duplicate":true} sans
     * effet financier. Ne lève que sur payload inexploitable.
     */
    public Map<String, Object> handleWebhook(Map<String, Object> payload, String rawJson)
            throws AppException, SQLException {
        String depositId = payload == null ? null : str(payload.get("depositId"));
        String status = payload == null ? null : str(payload.get("status"));
        if (depositId == null || depositId.isEmpty() || status == null || status.isEmpty()) {
            throw new AppException(ErrorCodes.VALIDATION_ERROR, 400, "Notification PawaPay inexploitable");
        }
        LOG.info(() -> "PawaPay callback received: depositId=" + depositId + " status=" + status);
        Payment p;
        try {
            p = paymentDAO.findByProviderTransactionId(depositId);
        } catch (SQLException e) {
            throw new AppException(ErrorCodes.DATABASE_ERROR, 500, "Impossible de traiter votre demande.", e);
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("depositId", depositId);
        if (p == null) {
            out.put("success", true);
            out.put("known", false);
            return out;
        }

        // Réconciliation serveur : le polling prime sur le callback
        // (un callback ne prouve rien à lui seul). Si PawaPay est configuré
        // mais que la réconciliation échoue (ou ne rend aucun statut), on ne
        // bascule RIEN : répondre deferred laisse PawaPay réessayer, et le
        // polling /status réconciliera. Accepter le callback ici permettrait
        // de marquer un dépôt « payé » avec une simple requête forgée.
        String effectiveStatus = status;
        if (PawaPayConfig.isConfigured()) {
            boolean reconciled = false;
            try {
                Map<String, Object> remote = new PawaPayClient().getDeposit(depositId);
                String polled = dataStatusOf(remote);
                if (polled != null) { effectiveStatus = polled; reconciled = true; }
            } catch (AppException e) {
                LOG.warning(() -> "PawaPay reconcile failed for " + depositId + ", deferring callback");
            }
            if (!reconciled) {
                out.put("success", true);
                out.put("known", true);
                out.put("deferred", true);
                return out;
            }
        }
        String eventKey = depositId + ":" + effectiveStatus;
        boolean isNew;
        try {
            isNew = eventDAO.insertIfNew(p.getId(), eventKey, rawJson);
        } catch (SQLException e) {
            throw new AppException(ErrorCodes.DATABASE_ERROR, 500, "Impossible de traiter votre demande.", e);
        }
        out.put("success", true);
        out.put("known", true);
        if (!isNew) {
            out.put("duplicate", true);
            return out;
        }
        boolean transitioned = false;
        if (isFinalStatus(effectiveStatus)) {
            String reason = "FAILED".equals(effectiveStatus) ? failureOf(payload) : null;
            try {
                transitioned = paymentDAO.transitionFromWebhook(p.getId(), effectiveStatus, reason);
            } catch (SQLException e) {
                throw new AppException(ErrorCodes.DATABASE_ERROR, 500, "Impossible de traiter votre demande.", e);
            }
            if (transitioned && "COMPLETED".equals(effectiveStatus)) {
                LOG.info(() -> "PawaPay Deposit completed: internalRef=" + p.getInternalReference());
                notifyCompleted(p);
            } else if (transitioned) {
                LOG.info(() -> "PawaPay Deposit failed: internalRef=" + p.getInternalReference());
                if ("FAILED".equals(effectiveStatus)) notifyFailed(p);
            }
        }
        out.put("duplicate", false);
        out.put("transitioned", transitioned);
        out.put("status", effectiveStatus);
        return out;
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private Order ownedOrder(String orderNumber, String phone) throws AppException, SQLException {
        if (orderNumber == null || orderNumber.trim().isEmpty()) {
            throw new AppException(ErrorCodes.VALIDATION_ERROR, 400, "Numéro de commande requis");
        }
        Order o;
        try {
            o = orderDAO.findByNumberAndPhone(orderNumber.trim(), phone == null ? "" : phone.trim());
        } catch (SQLException e) {
            throw new AppException(ErrorCodes.DATABASE_ERROR, 500, "Impossible de traiter votre demande.", e);
        }
        if (o == null) {
            throw new AppException(ErrorCodes.PAYMENT_NOT_FOUND, 404, "Commande introuvable");
        }
        return o;
    }

    private Payment findOpenQuiet(int orderId) {
        try {
            return paymentDAO.findOpenPawaPayByOrderId(orderId);
        } catch (SQLException e) {
            return null;
        }
    }

    /** Violation d'unicité (garde uq_pay_open : doublon concurrent). */
    static boolean isDuplicateKey(SQLException e) {
        return e != null && "23000".equals(e.getSQLState());
    }

    private Payment latestPawaPay(int orderId) throws SQLException {        // AVANT : balayait TOUTES les lignes COMPLETED puis FAILED de la table
        // (findByStatus sans filtre commande) — correct mais en O(table).
        // APRÈS : une seule requête ciblée (findLatestPawaPayByOrderId).
        // POURQUOI : équivalent — createDepositOutcome ne crée une ligne que
        // s'il n'existe aucune tentative ouverte, donc une tentative ouverte
        // éventuelle EST toujours la plus récente ; sinon la plus récente
        // (quel que soit son statut) est le même objet dans les deux versions.
        // TEST : PawaPayServiceLogicTest + parcours checkout existant.
        return paymentDAO.findLatestPawaPayByOrderId(orderId);
    }

    /** Applique un statut distant à la ligne locale (déjà journalisé). */
    private void reconcile(Payment p, Map<String, Object> remote) throws SQLException {
        if (remote == null) return;
        String found = str(remote.get("status"));
        if (!"FOUND".equals(found)) {
            // PawaPay ne connaît pas ce depositId : l'initiation n'est jamais
            // parvenue (rejet/coupure avant acceptation). Une ligne PENDING
            // n'est pas de l'argent : elle passe FAILED pour autoriser un
            // retry propre (nouveau depositId). Une ligne PROCESSING a été
            // ACCEPTED : on ne touche à rien (transitoire).
            if ("NOT_FOUND".equals(found) && "PENDING".equals(p.getStatus())) {
                try {
                    eventDAO.insertIfNew(p.getId(), p.getProviderTransactionId() + ":FAILED", null);
                } catch (SQLException ignored) {}
                if (paymentDAO.transitionFromWebhook(p.getId(), "FAILED",
                        "Demande non parvenue au prestataire")) {
                    LOG.info(() -> "PawaPay Deposit marked failed (not found): internalRef="
                        + p.getInternalReference());
                }
            }
            return;
        }
        String polled = dataStatusOf(remote);
        if (polled == null || !isFinalStatus(polled) || isFinalStatus(p.getStatus())) return;
        String reason = "FAILED".equals(polled) ? failureOf(dataOf(remote)) : null;
        try {
            eventDAO.insertIfNew(p.getId(), p.getProviderTransactionId() + ":" + polled, null);
        } catch (SQLException ignored) {}
        boolean moved = paymentDAO.transitionFromWebhook(p.getId(), polled, reason);
        if (moved && "COMPLETED".equals(polled)) {
            LOG.info(() -> "PawaPay Deposit completed (poll): internalRef=" + p.getInternalReference());
            notifyCompleted(p);
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> dataOf(Map<String, Object> remote) {
        Object d = remote.get("data");
        return d instanceof Map ? (Map<String, Object>) d : null;
    }

    private static String dataStatusOf(Map<String, Object> remote) {
        Map<String, Object> d = dataOf(remote);
        return d == null ? null : str(d.get("status"));
    }

    /** failureReason.failureCode + message, tronqué (jamais de secret). */
    @SuppressWarnings("unchecked")
    static String failureOf(Map<String, Object> node) {
        if (node == null) return null;
        Object fr = node.get("failureReason");
        if (!(fr instanceof Map)) return null;
        Map<String, Object> m = (Map<String, Object>) fr;
        String code = str(m.get("failureCode"));
        String msg = str(m.get("failureMessage"));
        String joined = code == null ? msg : (msg == null ? code : code + " : " + msg);
        if (joined == null) return null;
        return joined.substring(0, Math.min(255, joined.length()));
    }

    /** Statuts finaux PawaPay (COMPLETED/FAILED) + historiques MBOA. */
    static boolean isFinalStatus(String status) {
        return "COMPLETED".equals(status) || "FAILED".equals(status)
            || "REUSSIE".equals(status) || "ECHOUEE".equals(status)
            || "ANNULEE".equals(status) || "REFUNDED".equals(status);
    }

    /**
     * Normalise un MSISDN vers le format international PawaPay (chiffres
     * uniquement, indicatif obligatoire, jamais de zéro initial).
     * Ex : "6XXXXXXXX" → "2376XXXXXXXX", "06XXXXXXXX" (trunk national) →
     * "2376XXXXXXXX", "+237…" / "00237…" → "237…".
     * Retourne null si inexploitable.
     */
    public static String normalizeMsisdn(String raw) {
        if (raw == null) return null;
        String d = raw.trim().replaceAll("[\\s\\-\\.\\(\\)]", "");
        if (d.startsWith("+")) d = d.substring(1);
        while (d.startsWith("00")) d = d.substring(2);
        if (!d.matches("\\d{9,15}")) return null;
        if (d.length() == 9 && d.startsWith("6")) return "237" + d;
        // Trunk national camerounais : 06XXXXXXXX → 2376XXXXXXXX.
        if (d.length() == 10 && d.startsWith("06")) return "237" + d.substring(1);
        if (d.startsWith("0")) return null;
        return d;
    }

    /** depositId PawaPay : UUIDv4 unique, jamais réutilisé. */
    static String newDepositId() {
        return UUID.randomUUID().toString();
    }

    /** Référence interne unique : MBOA-ORD-YYYYMMDD-XXXXXXXX. */
    static String buildInternalReference() {
        String date = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);
        StringBuilder sb = new StringBuilder(8);
        for (int i = 0; i < 8; i++) sb.append(REF_ALPHABET.charAt(RANDOM.nextInt(REF_ALPHABET.length())));
        return "MBOA-ORD-" + date + "-" + sb;
    }

    /** Vue JSON sans donnée sensible (téléphone masqué, jamais de token). */
    public static Map<String, Object> maskedView(Payment p) {
        Map<String, Object> m = new LinkedHashMap<>();
        if (p == null) return m;
        m.put("id", p.getId());
        m.put("orderId", p.getOrderId());
        m.put("method", p.getMethod());
        m.put("provider", p.getProvider());
        m.put("operator", p.getOperator());
        m.put("amount", p.getAmount());
        m.put("currency", p.getCurrency());
        m.put("status", p.getStatus());
        m.put("internalReference", p.getInternalReference());
        m.put("providerTransactionId", p.getProviderTransactionId());
        m.put("depositId", p.getProviderTransactionId());
        m.put("clientReferenceId", p.getClientReferenceId());
        m.put("paid", "COMPLETED".equals(p.getStatus()) || "REUSSIE".equals(p.getStatus()));
        m.put("phoneMasked", maskPhone(p.getPhoneNumber()));
        m.put("failureReason", p.getFailureReason());
        m.put("paidAt", p.getPaidAt());
        m.put("createdAt", p.getCreatedAt());
        return m;
    }

    public static String maskPhone(String phone) {        if (phone == null || phone.length() < 3) return "***";
        return "***" + phone.substring(phone.length() - 3);
    }

    private void notifyCompleted(Payment p) {
        try {
            Order o = orderDAO.findById(p.getOrderId());
            if (o == null) return;
            com.ecommerce.service.NotificationService notifs = new com.ecommerce.service.NotificationService();
            if (o.getSupplierId() > 0) {
                notifs.notifySupplier(o.getSupplierId(), "PAYMENT_SUCCEEDED", "Paiement confirmé",
                    "Le paiement PawaPay de la commande " + o.getOrderNumber() + " a été confirmé.",
                    "PAYMENT", p.getId(), null, "ORDER:" + o.getId() + ":PAYMENT_SUCCEEDED");
            }
            // Email client (best-effort) si la commande porte un email.
            if (o.getEmail() != null
                    && com.ecommerce.service.EmailService.isValidEmail(o.getEmail())) {
                com.ecommerce.service.EmailService emails = new com.ecommerce.service.EmailService();
                emails.queueAndSend(o.getEmail(),
                    com.ecommerce.service.EmailService.paymentSucceededSubject(o.getOrderNumber()),
                    com.ecommerce.service.EmailService.paymentSucceededBody(o.getFullname(),
                        o.getOrderNumber(), p.getAmount()),
                    "PAYMENT_SUCCEEDED", o.getId());
            }
        } catch (Exception ignored) {}
    }

    private void notifyFailed(Payment p) {
        try {
            Order o = orderDAO.findById(p.getOrderId());
            if (o == null || o.getEmail() == null
                    || !com.ecommerce.service.EmailService.isValidEmail(o.getEmail())) return;
            com.ecommerce.service.EmailService emails = new com.ecommerce.service.EmailService();
            emails.queueAndSend(o.getEmail(),
                com.ecommerce.service.EmailService.paymentFailedSubject(o.getOrderNumber()),
                com.ecommerce.service.EmailService.paymentFailedBody(o.getFullname(), o.getOrderNumber()),
                "PAYMENT_FAILED", o.getId());
        } catch (Exception ignored) {}
    }

    private static String str(Object o) {
        return o == null ? null : String.valueOf(o);
    }
}
