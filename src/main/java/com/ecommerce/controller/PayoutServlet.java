package com.ecommerce.controller;

import com.ecommerce.model.Supplier;
import com.ecommerce.model.SupplierPayment;
import com.ecommerce.pawapay.PawaPayConfig;
import com.ecommerce.pawapay.PawaPayPaymentService;
import com.ecommerce.pawapay.PawaPayPayoutService;
import com.ecommerce.service.FinanceJournalService;
import com.ecommerce.service.SupplierAuthService;
import com.ecommerce.service.SupplierService;
import com.ecommerce.util.AdminAuth;
import com.ecommerce.util.AdminRole;
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
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Versements fournisseurs PawaPay.
 *
 * - GET  /api/payouts/my             (fournisseur : journal + coordonnées)
 * - PUT  /api/payouts/settings       (fournisseur : provider + numéro MoMo)
 * - GET  /api/payouts/status         (fournisseur propriétaire ou admin)
 * - GET  /api/payouts/eligibility    (admin : contrôle PAYOUT BLOCKED)
 * - POST /api/payouts/process        (admin MONEY : initie/retry un payout)
 * - POST /api/payouts/resend         (admin MONEY : redemande le callback)
 * - POST /api/payouts/callback       (PawaPay : callback {payoutId,status})
 *
 * Sécurité : le client (acheteur) ne peut JAMAIS déclencher un payout ;
 * seuls l'éligibilité métier (commande livrée + réception confirmée +
 * aucun litige) ou un admin MONEY le permettent.
 */
@WebServlet("/api/payouts/*")
public class PayoutServlet extends HttpServlet {

    private final PawaPayPayoutService payouts = new PawaPayPayoutService();
    private final SupplierAuthService supplierAuth = new SupplierAuthService();
    private final SupplierService supplierService = new SupplierService();

    private Integer supplierIdOf(HttpServletRequest req) {
        try {
            String token = req.getHeader("Authorization");
            if (token != null && token.startsWith("Bearer ")) token = token.substring(7);
            else token = null;
            if (token == null || token.isBlank()) return null;
            int id = supplierAuth.validateToken(token);
            return id > 0 ? id : null;
        } catch (Exception e) {
            return null;
        }
    }

    private static String pathOf(HttpServletRequest req) {
        String pi = req.getPathInfo();
        return pi == null || pi.isEmpty() ? "/" : pi;
    }

    private static Map<String, Object> readJson(HttpServletRequest req) throws IOException {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader r = req.getReader()) {
            String line;
            while ((line = r.readLine()) != null) sb.append(line);
        }
        if (sb.toString().trim().isEmpty()) return new LinkedHashMap<>();
        try {
            Object o = JsonUtil.fromJson(sb.toString(), Map.class);
            if (o instanceof Map) return (Map<String, Object>) o;
        } catch (Exception ignored) {}
        return new LinkedHashMap<>();
    }

    private static String str(Object o) { return o == null ? null : String.valueOf(o); }

    private static int num(Object o, int def) {
        if (o instanceof Number) return ((Number) o).intValue();
        try { return Integer.parseInt(String.valueOf(o)); } catch (Exception e) { return def; }
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");
        String path = pathOf(req);
        try {
            // Journal financier du fournisseur connecté.
            if ("/my".equals(path)) {
                Integer sid = supplierIdOf(req);
                if (sid == null) {
                    ApiResponse.fail(resp, 401, ErrorCodes.AUTH_REQUIRED, "Non autorisé");
                    return;
                }
                Map<String, Object> out = new FinanceJournalService().forSupplier(sid);
                try {
                    Supplier s = supplierService.getProfile(sid);
                    Map<String, Object> settings = new LinkedHashMap<>();
                    String mtn = s == null ? null
                        : PawaPayPaymentService.normalizeMsisdn(s.getMtnPhone());
                    String orange = s == null ? null
                        : PawaPayPaymentService.normalizeMsisdn(s.getOrangePhone());
                    settings.put("mtnPhoneMasked", PawaPayPaymentService.maskPhone(mtn));
                    settings.put("orangePhoneMasked", PawaPayPaymentService.maskPhone(orange));
                    settings.put("mtnConfigured", mtn != null);
                    settings.put("orangeConfigured", orange != null);
                    settings.put("payoutConfigured", mtn != null && orange != null);
                    out.put("settings", settings);
                } catch (Exception ignored) {}
                ApiResponse.ok(resp, out);
                return;
            }
            // Statut d'un versement (fournisseur propriétaire ou admin).
            if ("/status".equals(path)) {
                Map<String, Object> view;
                String payoutId = req.getParameter("payoutId");
                int orderId = num(req.getParameter("orderId"), 0);
                Integer sid = supplierIdOf(req);
                boolean admin = sid == null && AdminAuth.validateAdmin(req) > 0;
                if (payoutId != null && !payoutId.isBlank()) {
                    if (!admin && !ownsPayout(sid, payoutId)) {
                        ApiResponse.fail(resp, 403, ErrorCodes.ACCESS_DENIED, "Versement inaccessible");
                        return;
                    }
                    view = payouts.getPayoutStatus(payoutId.trim());
                } else if (orderId > 0) {
                    if (!admin && !ownsOrder(sid, orderId)) {
                        ApiResponse.fail(resp, 403, ErrorCodes.ACCESS_DENIED, "Commande inaccessible");
                        return;
                    }
                    view = payouts.getOrderPayoutStatus(orderId);
                } else {
                    ApiResponse.fail(resp, 400, ErrorCodes.VALIDATION_ERROR, "payoutId ou orderId requis");
                    return;
                }
                ApiResponse.ok(resp, view);
                return;
            }
            // Éligibilité (admin : diagnostic PAYOUT BLOCKED).
            if ("/eligibility".equals(path)) {
                if (AdminAuth.validateAdmin(req) <= 0) {
                    ApiResponse.fail(resp, 401, ErrorCodes.AUTH_REQUIRED, "Non autorisé");
                    return;
                }
                int orderId = num(req.getParameter("orderId"), 0);
                if (orderId <= 0) {
                    ApiResponse.fail(resp, 400, ErrorCodes.VALIDATION_ERROR, "orderId requis");
                    return;
                }
                ApiResponse.ok(resp, payouts.checkEligibility(orderId));
                return;
            }
            ApiResponse.fail(resp, 404, ErrorCodes.RESOURCE_NOT_FOUND, "Route inconnue");
        } catch (AppException e) {
            ApiResponse.fromException(resp, e);
        } catch (Exception e) {
            ApiResponse.fail(resp, 500, ErrorCodes.INTERNAL_ERROR, "Impossible de traiter votre demande.");
        }
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");
        String path = pathOf(req);
        try {
            // Callback PawaPay (public, réconciliation serveur + idempotence).
            // Signatures (doc : Content-Digest, Signature, Signature-Input,
            // Signature-Date — incluses SEULEMENT si les callbacks signés
            // sont activés au dashboard PawaPay) : journalisées si présentes,
            // jamais inventées. Configuration actuelle : callbacks NON
            // configurés côté compte (ni signés ni URL) → la preuve reste
            // le polling serveur (GET /v2/payouts/{id}).
            if ("/callback".equals(path)) {
                if (req.getHeader("Signature") != null
                        || req.getHeader("Content-Digest") != null
                        || req.getHeader("Signature-Input") != null) {
                    getServletContext().log("PawaPay payout webhook: signed headers present");
                }                StringBuilder sb = new StringBuilder();
                try (BufferedReader r = req.getReader()) {
                    String line;
                    while ((line = r.readLine()) != null) sb.append(line);
                }
                Map<String, Object> payload;
                try {
                    payload = JsonUtil.fromJson(sb.toString(), Map.class);
                } catch (Exception e) {
                    resp.setStatus(400);
                    resp.getWriter().write("{\"error\":\"JSON invalide\"}");
                    return;
                }
                try {
                    resp.getWriter().write(JsonUtil.toJson(payouts.handleCallback(payload, sb.toString())));
                } catch (AppException ae) {
                    if (ae.getHttpStatus() == 400) ApiResponse.fromException(resp, ae);
                    else resp.getWriter().write("{\"success\":true,\"deferred\":true}");
                }
                return;
            }
            // Déclenchement admin (MONEY) : initie ou réessaie le payout.
            if ("/process".equals(path)) {
                int adminId = AdminRole.requireAction(req, resp, AdminRole.MONEY);
                if (adminId <= 0) return;
                Map<String, Object> body = readJson(req);
                int orderId = num(body.get("orderId"), 0);
                if (orderId <= 0) {
                    ApiResponse.fail(resp, 400, ErrorCodes.VALIDATION_ERROR, "orderId requis");
                    return;
                }
                try {
                    PawaPayPayoutService.PayoutOutcome outcome = payouts.initiateOrRetry(orderId);
                    Map<String, Object> out = new LinkedHashMap<>();
                    out.put("success", true);
                    out.put("duplicate", outcome.duplicate);
                    out.put("payout", PawaPayPayoutService.maskedView(outcome.payout));
                    resp.setStatus(outcome.duplicate ? 200 : 201);
                    resp.getWriter().write(JsonUtil.toJson(out));
                } catch (AppException ae) {
                    ApiResponse.fromException(resp, ae);
                }
                return;
            }
            // Redemande le callback final (admin MONEY).
            if ("/resend".equals(path)) {
                int adminId = AdminRole.requireAction(req, resp, AdminRole.MONEY);
                if (adminId <= 0) return;
                Map<String, Object> body = readJson(req);
                try {
                    resp.getWriter().write(JsonUtil.toJson(
                        payouts.resendCallback(str(body.get("payoutId")))));
                } catch (AppException ae) {
                    ApiResponse.fromException(resp, ae);
                }
                return;
            }
            ApiResponse.fail(resp, 404, ErrorCodes.RESOURCE_NOT_FOUND, "Route inconnue");
        } catch (Exception e) {
            ApiResponse.fail(resp, 500, ErrorCodes.INTERNAL_ERROR, "Impossible de traiter votre demande.");
        }
    }

    @Override
    protected void doPut(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");
        try {
            if (!"/settings".equals(pathOf(req))) {
                ApiResponse.fail(resp, 404, ErrorCodes.RESOURCE_NOT_FOUND, "Route inconnue");
                return;
            }
            Integer sid = supplierIdOf(req);
            if (sid == null) {
                ApiResponse.fail(resp, 401, ErrorCodes.AUTH_REQUIRED, "Non autorisé");
                return;
            }
            Map<String, Object> body = readJson(req);
            // Les deux numéros sont exigés à l'activation ; la modification
            // ultérieure (profil) accepte un seul opérateur. Chaque numéro
            // fourni doit être retapé à l'identique (anti-erreur, vérifié
            // aussi côté serveur). Normalisation serveur.
            boolean hasMtn = body.get("mtnPhone") != null || body.get("mtnPhoneConfirm") != null;
            boolean hasOrange = body.get("orangePhone") != null || body.get("orangePhoneConfirm") != null;
            if (!hasMtn && !hasOrange) {
                ApiResponse.fail(resp, 400, ErrorCodes.VALIDATION_ERROR, "Numéros requis");
                return;
            }
            String mtn = null, orange = null;
            if (hasMtn) {
                mtn = PawaPayPaymentService.normalizeMsisdn(str(body.get("mtnPhone")));
                String mtnConfirm = PawaPayPaymentService.normalizeMsisdn(str(body.get("mtnPhoneConfirm")));
                if (mtn == null || mtnConfirm == null || !mtn.equals(mtnConfirm)) {
                    ApiResponse.fail(resp, 400, ErrorCodes.VALIDATION_ERROR,
                        "Numéros MTN différents ou invalides (retapez à l'identique)");
                    return;
                }
            }
            if (hasOrange) {
                orange = PawaPayPaymentService.normalizeMsisdn(str(body.get("orangePhone")));
                String orangeConfirm = PawaPayPaymentService.normalizeMsisdn(str(body.get("orangePhoneConfirm")));
                if (orange == null || orangeConfirm == null || !orange.equals(orangeConfirm)) {
                    ApiResponse.fail(resp, 400, ErrorCodes.VALIDATION_ERROR,
                        "Numéros Orange différents ou invalides (retapez à l'identique)");
                    return;
                }
            }
            if (mtn != null && mtn.equals(orange)) {
                ApiResponse.fail(resp, 400, ErrorCodes.VALIDATION_ERROR,
                    "Les numéros MTN et Orange doivent être différents");
                return;
            }
            // Garde : si l'un est fourni avec l'autre déjà configuré identique.
            try {
                com.ecommerce.model.Supplier cur =
                    new com.ecommerce.dao.SupplierDAO().findById(sid);
                String curMtn = cur == null ? null
                    : PawaPayPaymentService.normalizeMsisdn(cur.getMtnPhone());
                String curOrange = cur == null ? null
                    : PawaPayPaymentService.normalizeMsisdn(cur.getOrangePhone());
                String finalMtn = mtn != null ? mtn : curMtn;
                String finalOrange = orange != null ? orange : curOrange;
                if (finalMtn != null && finalMtn.equals(finalOrange)) {
                    ApiResponse.fail(resp, 400, ErrorCodes.VALIDATION_ERROR,
                        "Les numéros MTN et Orange doivent être différents");
                    return;
                }
            } catch (java.sql.SQLException e) {
                ApiResponse.fail(resp, 500, ErrorCodes.DATABASE_ERROR, "Impossible de traiter votre demande.");
                return;
            }
            new com.ecommerce.dao.SupplierDAO().updatePayoutPhones(sid, mtn, orange);
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("success", true);
            out.put("mtnPhoneMasked", PawaPayPaymentService.maskPhone(mtn));
            out.put("orangePhoneMasked", PawaPayPaymentService.maskPhone(orange));
            resp.getWriter().write(JsonUtil.toJson(out));
        } catch (java.sql.SQLException e) {
            ApiResponse.fail(resp, 500, ErrorCodes.DATABASE_ERROR, "Impossible de traiter votre demande.");
        } catch (Exception e) {
            ApiResponse.fail(resp, 500, ErrorCodes.INTERNAL_ERROR, "Impossible de traiter votre demande.");
        }
    }

    private boolean ownsPayout(Integer supplierId, String payoutId) {
        if (supplierId == null) return false;
        try {
            SupplierPayment sp = new com.ecommerce.dao.PayoutDAO().findByPayoutId(payoutId.trim());
            return sp != null && sp.getSupplierId() == supplierId;
        } catch (Exception e) {
            return false;
        }
    }

    private boolean ownsOrder(Integer supplierId, int orderId) {
        if (supplierId == null) return false;
        try {
            com.ecommerce.model.Order o = new com.ecommerce.dao.OrderDAO().findById(orderId);
            return o != null && o.getSupplierId() == supplierId;
        } catch (Exception e) {
            return false;
        }
    }
}
