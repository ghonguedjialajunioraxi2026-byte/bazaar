package com.ecommerce.controller;

import com.ecommerce.model.Payment;
import com.ecommerce.pawapay.PawaPayConfig;
import com.ecommerce.pawapay.PawaPayDepositService;
import com.ecommerce.pawapay.PawaPayPaymentService;
import com.ecommerce.util.ApiResponse;
import com.ecommerce.util.AppException;
import com.ecommerce.util.ErrorCodes;
import com.ecommerce.util.JsonUtil;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.BufferedReader;
import java.io.IOException;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Endpoints PawaPay (Sandbox). Le frontend ne dialogue QU'AVEC ce backend ;
 * le token PawaPay ne transite jamais côté client.
 *
 * - POST /api/payments/pawapay/deposit          (checkout : orderId|orderNumber+phone, provider, phoneNumber)
 * - GET  /api/payments/pawapay/status            (invité : orderNumber+phone | depositId)
 * - POST /api/payments/pawapay/deposit/callback  (PawaPay : callback {depositId,status,...})
 * - POST /api/payments/pawapay/deposit/resend    (invité propriétaire : redemande le callback final)
 * - GET  /api/payments/pawapay/refund/status     (admin ou invité propriétaire : statut du remboursement)
 * - POST /api/payments/pawapay/refund/resend     (admin MONEY : redemande le callback final)
 * - POST /api/payments/pawapay/refund/callback   (PawaPay : callback {refundId,status,...})
 * - GET  /api/payments/pawapay/config-status     (public, SANS secret : présence + env)
 * Aliases historiques conservés : /create → /deposit, /webhook → /deposit/callback.
 *
 * Sécurité webhook (mécanismes réellement supportés, sans fausse vérif) :
 * 1. payload exigé (depositId+status) ; 2. réconciliation serveur via
 *    GET /v2/deposits/{id} (source de vérité) ; 3. idempotence par
 *    payment_events (clé unique) ; 4. transitions conditionnelles.
 *    Configurer en plus côté dashboard PawaPay : URL callback, whitelist IP,
 *    signatures HTTP optionnelles (lues et journalisées si présentes).
 */
@WebServlet("/api/payments/pawapay/*")
public class PawaPayServlet extends HttpServlet {

    private final PawaPayDepositService deposits = new PawaPayDepositService();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");
        String path = pathOf(req);
        try {
            if ("/config-status".equals(path)) {
                // Jamais de secret ici : présence + environnement uniquement.
                resp.getWriter().write(JsonUtil.toJson(PawaPayConfig.statusSummary()));
                return;
            }
            if ("/providers".equals(path)) {
                // Opérateurs issus de la config (MTN/Orange CMR, XAF) : le
                // frontend construit ses cartes de paiement depuis cette liste.
                // Si le token est configuré, la disponibilité live (active-conf)
                // désactive les providers non supportés ; sinon tout est
                // proposé (repli statique vérifié).
                java.util.List<Map<String, Object>> providers = PawaPayConfig.providers();
                for (Map<String, Object> p : providers) p.put("available", true);
                if (PawaPayConfig.isConfigured()) {
                    try {
                        Map<String, Object> conf = new com.ecommerce.pawapay.PawaPayClient()
                            .getActiveConfiguration(PawaPayConfig.COUNTRY);
                        java.util.Set<String> live =
                            com.ecommerce.pawapay.PawaPayClient.parseDepositProviders(conf);
                        if (!live.isEmpty()) {
                            for (Map<String, Object> p : providers) {
                                p.put("available", live.contains(String.valueOf(p.get("provider"))));
                            }
                        }
                    } catch (Exception ignored) {
                        // Repli statique : jamais d'échec sur cette route.
                    }
                }
                Map<String, Object> out = new LinkedHashMap<>();
                out.put("currency", PawaPayConfig.CURRENCY);
                out.put("country", PawaPayConfig.COUNTRY);
                out.put("providers", providers);
                resp.getWriter().write(JsonUtil.toJson(out));
                return;
            }
            if ("/status".equals(path)) {
                try {
                    Integer session = com.ecommerce.service.CustomerIsolation.sessionOf(
                        com.ecommerce.util.CustomerAuth.validateCustomer(req));
                    Map<String, Object> view = deposits.getDepositStatus(
                        num(req.getParameter("orderId")),
                        req.getParameter("orderNumber"), req.getParameter("phone"),
                        req.getParameter("depositId"), session);
                    resp.getWriter().write(JsonUtil.toJson(view));
                } catch (AppException ae) {
                    ApiResponse.fromException(resp, ae);
                }
                return;
            }
            if ("/refund/status".equals(path)) {
                // Admin (session) ou invité propriétaire (orderNumber+phone).
                // refundId seul = capacité UUID imprévisible (vue masquée).
                try {
                    Map<String, Object> view = refundStatusFor(req);
                    resp.getWriter().write(JsonUtil.toJson(view));
                } catch (AppException ae) {
                    ApiResponse.fromException(resp, ae);
                }
                return;
            }
            resp.setStatus(404);
            resp.getWriter().write("{\"error\":\"Endpoint PawaPay inconnu\"}");
        } catch (Exception e) {
            resp.setStatus(500);
            ApiResponse.internalError(resp);
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");
        String path = pathOf(req);
        try {
            if ("/deposit".equals(path) || "/create".equals(path)) {
                Map<String, Object> body = readJson(req);
                // Le montant client éventuel ("amount") est IGNORÉ : le backend
                // recalcule le total depuis la commande (anti-falsification).
                String method = firstPresent(body, "provider", "method");
                String msisdn = firstPresent(body, "phoneNumber", "msisdn");
                try {
                    Integer session = com.ecommerce.service.CustomerIsolation.sessionOf(
                        com.ecommerce.util.CustomerAuth.validateCustomer(req));
                    PawaPayPaymentService.DepositOutcome outcome = deposits.createDeposit(
                        num(body.get("orderId")),
                        str(body.get("orderNumber")), str(body.get("phone")),
                        method, msisdn, session);
                    Payment p = outcome.payment;
                    Map<String, Object> view = PawaPayPaymentService.maskedView(p);
                    Map<String, Object> out = new LinkedHashMap<>();
                    out.put("success", true);
                    out.put("paymentId", p.getId());
                    out.put("depositId", p.getProviderTransactionId());
                    out.put("status", p.getStatus());
                    out.put("payment", view);
                    if (outcome.duplicate) {
                        // Retry sûr : aucune nouvelle transaction PawaPay.
                        out.put("duplicate", true);
                        out.put("code", "PROCESSING".equals(p.getStatus())
                            ? ErrorCodes.PAYMENT_ALREADY_PROCESSING
                            : ErrorCodes.DUPLICATE_DEPOSIT);
                        resp.setStatus(200);
                    } else if ("FAILED".equals(p.getStatus())) {
                        out.put("code", ErrorCodes.PAWAPAY_REJECTED);
                        resp.setStatus(200);
                    } else {
                        resp.setStatus(201);
                    }
                    resp.getWriter().write(JsonUtil.toJson(out));
                } catch (AppException ae) {
                    ApiResponse.fromException(resp, ae);
                }
                return;
            }
            // Réconciliation : redemande le callback final PawaPay d'un dépôt
            // existant (propriété commande vérifiée, aucune transaction créée).
            if ("/deposit/resend".equals(path)) {
                Map<String, Object> body = readJson(req);
                try {
                    Integer session = com.ecommerce.service.CustomerIsolation.sessionOf(
                        com.ecommerce.util.CustomerAuth.validateCustomer(req));
                    Map<String, Object> out = deposits.resendCallback(
                        num(body.get("orderId")),
                        str(body.get("orderNumber")), str(body.get("phone")), session);
                    resp.getWriter().write(JsonUtil.toJson(out));
                } catch (AppException ae) {
                    ApiResponse.fromException(resp, ae);
                }
                return;
            }
                        // Callback PawaPay des remboursements (même discipline que deposit).
            if ("/refund/callback".equals(path)) {
                // Signatures (doc : Content-Digest, Signature, Signature-Input,
                // Signature-Date — incluses SEULEMENT si les callbacks signés
                // sont activés au dashboard PawaPay) : journalisées si présentes,
                // jamais inventées. Configuration actuelle : callbacks NON
                // configurés côté compte → la preuve reste le polling serveur.
                if (req.getHeader("Signature") != null
                        || req.getHeader("Content-Digest") != null
                        || req.getHeader("Signature-Input") != null) {
                    getServletContext().log("PawaPay refund webhook: signed headers present");
                }
                String raw = readRaw(req);
                Map<String, Object> payload;
                try {
                    payload = JsonUtil.fromJson(raw, Map.class);
                } catch (Exception e) {
                    resp.setStatus(400);
                    resp.getWriter().write("{\"error\":\"JSON invalide\"}");
                    return;
                }
                try {
                    Map<String, Object> out = new com.ecommerce.pawapay.PawaPayRefundService()
                        .handleCallback(payload, raw);
                    resp.getWriter().write(JsonUtil.toJson(out));
                } catch (AppException ae) {
                    if (ae.getHttpStatus() == 400
                            || ErrorCodes.VALIDATION_ERROR.equals(ae.getCode())) {
                        ApiResponse.fromException(resp, ae);
                    } else {
                        resp.getWriter().write("{\"success\":true,\"deferred\":true}");
                    }
                }
                return;
            }
            // Callback statement PawaPay (public) : POST vers la callbackUrl
            // fournie à la création. Corps doc non détaillé : statementId
            // exigé, transitions idempotentes, preuve = polling serveur.
            if ("/statement/callback".equals(path)) {
                if (req.getHeader("Signature") != null
                        || req.getHeader("Content-Digest") != null
                        || req.getHeader("Signature-Input") != null) {
                    getServletContext().log("PawaPay statement webhook: signed headers present");
                }
                String raw = readRaw(req);
                Map<String, Object> payload;
                try {
                    payload = JsonUtil.fromJson(raw, Map.class);
                } catch (Exception e) {
                    resp.setStatus(400);
                    resp.getWriter().write("{\"error\":\"JSON invalide\"}");
                    return;
                }
                try {
                    Map<String, Object> out = new com.ecommerce.pawapay.PawaPayStatementService()
                        .handleCallback(payload, raw);
                    resp.getWriter().write(JsonUtil.toJson(out));
                } catch (AppException ae) {
                    if (ae.getHttpStatus() == 400
                            || ErrorCodes.VALIDATION_ERROR.equals(ae.getCode())) {
                        ApiResponse.fromException(resp, ae);
                    } else {
                        resp.getWriter().write("{\"success\":true,\"deferred\":true}");
                    }
                }
                return;
            }
            // Redemande le callback final d'un remboursement (admin MONEY).
            if ("/refund/resend".equals(path)) {                int adminId = com.ecommerce.util.AdminRole.requireAction(req, resp,
                    com.ecommerce.util.AdminRole.MONEY);
                if (adminId <= 0) return;
                Map<String, Object> body = readJson(req);
                try {
                    Map<String, Object> out = new com.ecommerce.pawapay.PawaPayRefundService()
                        .resendCallback(str(body.get("refundId")));
                    resp.getWriter().write(JsonUtil.toJson(out));
                } catch (AppException ae) {
                    ApiResponse.fromException(resp, ae);
                }
                return;
            }
            if ("/deposit/callback".equals(path) || "/webhook".equals(path)) {                String raw = readRaw(req);
                Map<String, Object> payload;
                try {
                    payload = JsonUtil.fromJson(raw, Map.class);
                } catch (Exception e) {
                    resp.setStatus(400);
                    resp.getWriter().write("{\"error\":\"JSON invalide\"}");
                    return;
                }
                // Signatures HTTP PawaPay (optionnelles, activables au dashboard) :
                // journalisées si présentes ; la preuve reste la réconciliation
                // serveur (GET /v2/deposits/{id}), jamais le seul callback.
                if (req.getHeader("Signature") != null) {
                    getServletContext().log("PawaPay webhook: signature header present");
                }
                try {
                    Map<String, Object> out = deposits.handleCallback(payload, raw);
                    resp.getWriter().write(JsonUtil.toJson(out));
                } catch (AppException ae) {
                    // Payload inexploitable → 400 ; tout le reste → 200 avec
                    // known=false/duplicate pour ne pas boucler les retries
                    // sur une erreur déjà gérée côté métier.
                    if (ae.getHttpStatus() == 400
                            || ErrorCodes.VALIDATION_ERROR.equals(ae.getCode())) {
                        ApiResponse.fromException(resp, ae);
                    } else {
                        Map<String, Object> out = new LinkedHashMap<>();
                        out.put("success", true);
                        out.put("deferred", true);
                        resp.getWriter().write(JsonUtil.toJson(out));
                    }
                }
                return;
            }
            resp.setStatus(404);
            resp.getWriter().write("{\"error\":\"Endpoint PawaPay inconnu\"}");
        } catch (Exception e) {
            resp.setStatus(500);
            ApiResponse.internalError(resp);
        }
    }

    private static String pathOf(HttpServletRequest req) {
        String pi = req.getPathInfo();
        return pi == null || pi.isEmpty() ? "/" : pi;
    }

    /** Statut refund : admin, ou invité propriétaire (orderNumber+phone), ou refundId (capacité). */
    private static Map<String, Object> refundStatusFor(HttpServletRequest req) throws AppException {
        com.ecommerce.pawapay.PawaPayRefundService refunds =
            new com.ecommerce.pawapay.PawaPayRefundService();
        String refundId = req.getParameter("refundId");
        String orderNumber = req.getParameter("orderNumber");
        String phone = req.getParameter("phone");
        Integer orderIdBox = num(req.getParameter("orderId"));
        int orderId = orderIdBox == null ? 0 : orderIdBox;
        boolean admin = com.ecommerce.util.AdminAuth.validateAdmin(req) > 0;
        if (admin && orderId > 0) {
            try {
                return refunds.getOrderRefundStatus(orderId);
            } catch (SQLException e) {
                throw new AppException(ErrorCodes.DATABASE_ERROR, 500, "Impossible de traiter votre demande.", e);
            }
        }
        if (admin && refundId != null && !refundId.isBlank()) {
            try {
                return refunds.getRefundStatus(refundId.trim());
            } catch (SQLException e) {
                throw new AppException(ErrorCodes.DATABASE_ERROR, 500, "Impossible de traiter votre demande.", e);
            }
        }
        if (orderNumber != null && !orderNumber.isBlank() && phone != null && !phone.isBlank()) {
            try {
                com.ecommerce.model.Order o = new com.ecommerce.dao.OrderDAO()
                    .findByNumberAndPhone(orderNumber.trim(), phone.trim());
                if (o == null) {
                    throw new AppException(ErrorCodes.PAYMENT_NOT_FOUND, 404, "Commande introuvable");
                }
                Integer session = com.ecommerce.service.CustomerIsolation.sessionOf(
                    com.ecommerce.util.CustomerAuth.validateCustomer(req));
                com.ecommerce.service.CustomerIsolation.requireOrderVisible(o, session);
                return refunds.getOrderRefundStatus(o.getId());
            } catch (SQLException e) {
                throw new AppException(ErrorCodes.DATABASE_ERROR, 500, "Impossible de traiter votre demande.", e);
            }
        }
        if (refundId != null && !refundId.isBlank()) {
            try {
                return refunds.getRefundStatus(refundId.trim());
            } catch (SQLException e) {
                throw new AppException(ErrorCodes.DATABASE_ERROR, 500, "Impossible de traiter votre demande.", e);
            }
        }
        throw new AppException(ErrorCodes.VALIDATION_ERROR, 400, "orderNumber+phone ou refundId requis");
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> readJson(HttpServletRequest req) throws IOException {
        String raw = readRaw(req);
        if (raw == null || raw.trim().isEmpty()) return new LinkedHashMap<>();
        try {
            Object o = JsonUtil.fromJson(raw, Map.class);
            return o instanceof Map ? (Map<String, Object>) o : new LinkedHashMap<>();
        } catch (Exception e) {
            return new LinkedHashMap<>();
        }
    }

    private static String readRaw(HttpServletRequest req) throws IOException {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader r = req.getReader()) {
            String line;
            while ((line = r.readLine()) != null) sb.append(line);
        }
        return sb.toString();
    }

    private static String str(Object o) {
        return o == null ? null : String.valueOf(o);
    }

    private static Integer num(Object o) {
        if (o == null) return null;
        if (o instanceof Number) return ((Number) o).intValue();
        try {
            return Integer.parseInt(String.valueOf(o).trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String firstPresent(Map<String, Object> body, String... keys) {
        for (String k : keys) {
            String v = str(body.get(k));
            if (v != null && !v.trim().isEmpty()) return v;
        }
        return null;
    }
}
