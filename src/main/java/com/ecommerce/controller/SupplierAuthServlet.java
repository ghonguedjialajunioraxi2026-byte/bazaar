package com.ecommerce.controller;

import com.ecommerce.model.Supplier;
import com.ecommerce.service.EmailService;
import com.ecommerce.service.LoginAttemptService;
import com.ecommerce.service.SupplierAuthService;
import com.ecommerce.service.SupplierLoginCodeService;
import com.ecommerce.service.SupplierService;
import com.ecommerce.util.GoogleAuthUtil;
import com.ecommerce.util.JsonUtil;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.BufferedReader;
import java.io.IOException;
import java.sql.SQLException;
import java.util.Map;

@WebServlet(urlPatterns = {"/api/supplier/login", "/api/supplier/register", "/api/supplier/google",
        "/api/supplier/google-client-id", "/api/supplier/verify-code", "/api/supplier/resend-code"})
public class SupplierAuthServlet extends HttpServlet {

    /**
     * Expose le Client ID Google effectif (source unique de vérité).
     * Public : le bouton Google du frontend le récupère au chargement,
     * avec repli sur la constante en dur si besoin.
     */
    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");
        if (req.getRequestURI().endsWith("/api/supplier/google-client-id")) {
            resp.getWriter().write("{\"clientId\":\"" + GoogleAuthUtil.GOOGLE_CLIENT_ID + "\"}");
            return;
        }
        resp.setStatus(404);
        resp.getWriter().write("{\"success\":false,\"message\":\"Route inconnue\"}");
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");
        SupplierAuthService authService = new SupplierAuthService();
        SupplierService supplierService = new SupplierService();
        try {
            BufferedReader reader = req.getReader();
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) sb.append(line);
            Map<String, String> body = JsonUtil.fromJson(sb.toString(), Map.class);
            if (body == null) body = new java.util.HashMap<>();

            // Vérification du code à 6 chiffres (étape 2).
            if (req.getRequestURI().endsWith("/api/supplier/verify-code")) {
                handleVerifyCode(req, resp, body, authService, supplierService);
                return;
            }

            // Renvoi du code à 6 chiffres.
            if (req.getRequestURI().endsWith("/api/supplier/resend-code")) {
                handleResendCode(req, resp, body, authService);
                return;
            }

            // Connexion via Google Sign-In (email déjà vérifié par Google : pas de code).
            if (req.getRequestURI().endsWith("/api/supplier/google")) {
                String idToken = body.get("idToken");
                if (idToken == null) idToken = body.get("credential");
                GoogleAuthUtil.GoogleUser gu;
                try {
                    gu = GoogleAuthUtil.verify(idToken);
                } catch (Exception e) {
                    resp.setStatus(401);
                    resp.getWriter().write("{\"success\":false,\"message\":\"Impossible de traiter votre demande.\"}");
                    return;
                }
                Supplier gs;
                try {
                    gs = authService.loginWithGoogle(gu.sub, gu.email, gu.name, gu.picture);
                } catch (SQLException e) {
                    resp.setStatus(403);
                    resp.getWriter().write("{\"success\":false,\"message\":\"Impossible de traiter votre demande.\"}");
                    return;
                }
                String gtoken = authService.createSession(gs.getId(), req.getRemoteAddr(), req.getHeader("User-Agent"));
                resp.getWriter().write(authJson(gtoken, supplierService.getProfile(gs.getId())));
                return;
            }

            // Inscription -> crée le compte PUIS exige la vérification email (code à 6 chiffres).
            if (req.getRequestURI().endsWith("/api/supplier/register")) {
                String email = body.get("email");
                String password = body.get("password");
                String name = body.get("name");
                String contactName = body.get("contactName");
                String phone = body.get("phone");
                String whatsapp = body.get("whatsapp") != null ? body.get("whatsapp") : phone;
                String city = body.get("city");
                String address = body.get("address");
                String description = body.get("description");

                if (email == null || email.trim().isEmpty()
                        || password == null || password.length() < 6) {
                    resp.setStatus(400);
                    resp.getWriter().write("{\"success\":false,\"message\":\"Email valide et mot de passe (min 6 caractères) requis\"}");
                    return;
                }
                if (name == null || name.trim().isEmpty()) {
                    resp.setStatus(400);
                    resp.getWriter().write("{\"success\":false,\"message\":\"Le nom de la boutique est obligatoire\"}");
                    return;
                }

                Supplier s = new Supplier();
                s.setName(name.trim());
                s.setEmail(email.trim());
                s.setContactName(contactName);
                s.setPhone(phone);
                s.setWhatsapp(whatsapp);
                s.setCity(city);
                s.setAddress(address);
                s.setDescription(description);

                Supplier created;
                try {
                    created = supplierService.register(s, password);
                } catch (SQLException e) {
                    resp.setStatus(409);
                    resp.getWriter().write("{\"success\":false,\"message\":\"Impossible de traiter votre demande.\"}");
                    return;
                }

                // Compte créé : on notifie l'adresse et on exige le code avant tout accès.
                sendLoginCode(created);
                resp.getWriter().write(verificationJson(created.getEmail()));
                return;
            }

            // ---- ÉTAPE 1 : email + mot de passe -> envoi du code à 6 chiffres ----
            String email = body.get("email");
            String password = body.get("password");

            // Anti brute-force : 5 échecs → 15 min (clé email + IP).
            String attemptKey = LoginAttemptService.key(
                "supplier", email, req.getRemoteAddr());
            if (LoginAttemptService.get().isBlocked(attemptKey)) {
                resp.setStatus(429);
                resp.getWriter().write("{\"success\":false,\"message\":\"Trop de tentatives. Réessayez dans 15 minutes.\"}");
                return;
            }

            Supplier s = authService.checkPassword(email, password);
            if (s == null) {
                LoginAttemptService.get().recordFailure(attemptKey);
                resp.setStatus(401);
                resp.getWriter().write("{\"success\":false,\"message\":\"Email ou mot de passe incorrect\"}");
                return;
            }
            LoginAttemptService.get().recordSuccess(attemptKey);

            // Mot de passe OK : l'adresse est notifiée + code à saisir (pas de session avant ça).
            sendLoginCode(s);
            resp.getWriter().write(verificationJson(s.getEmail()));
        } catch (Exception e) {
            resp.setStatus(500);
            resp.getWriter().write("{\"success\":false,\"message\":\"Impossible de traiter votre demande.\"}");
        }
    }

    // ------------------------------------------------------------------
    // Étape 2 : vérification du code
    // ------------------------------------------------------------------

    private void handleVerifyCode(HttpServletRequest req, HttpServletResponse resp,
            Map<String, String> body, SupplierAuthService authService,
            SupplierService supplierService) throws IOException {
        String email = body.get("email");
        String code = body.get("code");
        if (email == null || email.trim().isEmpty() || code == null || code.trim().isEmpty()) {
            resp.setStatus(400);
            resp.getWriter().write("{\"success\":false,\"message\":\"Email et code à 6 chiffres requis\"}");
            return;
        }
        String attemptKey = LoginAttemptService.key("supplier-code", email, req.getRemoteAddr());
        if (LoginAttemptService.get().isBlocked(attemptKey)) {
            resp.setStatus(429);
            resp.getWriter().write("{\"success\":false,\"message\":\"Trop de tentatives. Demandez un nouveau code dans 15 minutes.\"}");
            return;
        }
        SupplierLoginCodeService.VerifyResult r =
            SupplierLoginCodeService.get().verify(email, code);
        switch (r) {
            case OK:
                break;
            case EXPIRED:
                resp.setStatus(410);
                resp.getWriter().write("{\"success\":false,\"message\":\"Code expiré. Demandez un nouveau code.\"}");
                return;
            case TOO_MANY_ATTEMPTS:
                LoginAttemptService.get().recordFailure(attemptKey);
                resp.setStatus(429);
                resp.getWriter().write("{\"success\":false,\"message\":\"Trop d'essais. Demandez un nouveau code.\"}");
                return;
            case NO_CODE:
                resp.setStatus(400);
                resp.getWriter().write("{\"success\":false,\"message\":\"Aucun code en attente. Reconnectez-vous pour recevoir un code.\"}");
                return;
            default:
                LoginAttemptService.get().recordFailure(attemptKey);
                resp.setStatus(401);
                resp.getWriter().write("{\"success\":false,\"message\":\"Code incorrect. Vérifiez l'email reçu.\"}");
                return;
        }
        LoginAttemptService.get().recordSuccess(attemptKey);
        Supplier s;
        try {
            s = new com.ecommerce.dao.SupplierDAO().findByEmail(email.trim().toLowerCase());
        } catch (SQLException e) {
            resp.setStatus(500);
            resp.getWriter().write("{\"success\":false,\"message\":\"Impossible de traiter votre demande.\"}");
            return;
        }
        if (s == null || !s.isActive()) {
            resp.setStatus(401);
            resp.getWriter().write("{\"success\":false,\"message\":\"Compte introuvable ou désactivé\"}");
            return;
        }
        try {
            authService.markLoggedIn(s.getId());
            String token = authService.createSession(s.getId(), req.getRemoteAddr(), req.getHeader("User-Agent"));
            resp.getWriter().write(authJson(token, supplierService.getProfile(s.getId())));
        } catch (Exception e) {
            resp.setStatus(500);
            resp.getWriter().write("{\"success\":false,\"message\":\"Impossible de traiter votre demande.\"}");
        }
    }

    private void handleResendCode(HttpServletRequest req, HttpServletResponse resp,
            Map<String, String> body, SupplierAuthService authService) throws IOException {
        String email = body.get("email");
        if (email == null || email.trim().isEmpty()) {
            resp.setStatus(400);
            resp.getWriter().write("{\"success\":false,\"message\":\"Email requis\"}");
            return;
        }
        String attemptKey = LoginAttemptService.key("supplier-resend", email, req.getRemoteAddr());
        if (LoginAttemptService.get().isBlocked(attemptKey)) {
            resp.setStatus(429);
            resp.getWriter().write("{\"success\":false,\"message\":\"Trop de demandes. Réessayez dans 15 minutes.\"}");
            return;
        }
        Supplier s;
        try {
            s = new com.ecommerce.dao.SupplierDAO().findByEmail(email.trim().toLowerCase());
        } catch (SQLException e) {
            resp.setStatus(500);
            resp.getWriter().write("{\"success\":false,\"message\":\"Impossible de traiter votre demande.\"}");
            return;
        }
        // Anti-énumération : même réponse que le compte existe ou non.
        if (s == null || !s.isActive()) {
            resp.getWriter().write(verificationJson(email));
            return;
        }
        long wait = SupplierLoginCodeService.get().resendWaitSeconds(email);
        if (wait > 0) {
            resp.setStatus(429);
            resp.getWriter().write("{\"success\":false,\"message\":\"Patientez " + wait
                + " s avant de redemander un code.\",\"retryIn\":" + wait + "}");
            return;
        }
        String code = SupplierLoginCodeService.get().resendCode(s.getId(), s.getEmail());
        if (code == null) {
            resp.setStatus(429);
            resp.getWriter().write("{\"success\":false,\"message\":\"Patientez quelques instants avant de redemander un code.\"}");
            return;
        }
        LoginAttemptService.get().recordSuccess(attemptKey);
        sendCodeEmail(s, code);
        resp.getWriter().write(verificationJson(s.getEmail()));
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    /** Génère le code, l'envoie par email (best-effort, jamais bloquant). */
    private void sendLoginCode(Supplier s) {
        String code = SupplierLoginCodeService.get().requestCode(s.getId(), s.getEmail());
        if (code == null) return;
        sendCodeEmail(s, code);
    }

    private void sendCodeEmail(Supplier s, String code) {
        try {
            String shop = s.getName() == null ? "" : s.getName();
            new EmailService().queueAndSend(s.getEmail(),
                EmailService.supplierLoginCodeSubject(),
                EmailService.supplierLoginCodeBody(shop, code),
                "SUPPLIER_LOGIN_CODE", null);
        } catch (Exception ignored) {}
    }

    /** Réponse étape 1 : pas de session, le frontend affiche l'écran du code. */
    private String verificationJson(String email) {
        String e = email == null ? "" : email.trim().toLowerCase();
        long expiresIn = SupplierLoginCodeService.get().expiresInSeconds(e);
        if (expiresIn <= 0) expiresIn = 600;
        return "{\"success\":true,\"requiresVerification\":true"
            + ",\"email\":\"" + safe(e) + "\""
            + ",\"maskedEmail\":\"" + safe(SupplierLoginCodeService.maskEmail(e)) + "\""
            + ",\"expiresIn\":" + expiresIn
            + ",\"message\":\"Un code à 6 chiffres a été envoyé à votre adresse email.\"}";
    }

    private String safe(String s) {
        return s == null ? "" : s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    /**
     * Réponse d'authentification unifiée, avec indicateur de profil finalisé.
     * profileComplete=false -> le frontend redirige vers l'écran
     * "Finaliser ma boutique" (nom, ville, téléphone, adresse).
     */
    private String authJson(String token, Supplier s) {
        boolean complete = new SupplierAuthService().isProfileComplete(s);
        return "{\"success\":true,\"token\":\"" + token
                + "\",\"profileComplete\":" + complete
                + ",\"supplier\":{\"id\":" + s.getId()
                + ",\"name\":\"" + safe(s.getName())
                + "\",\"email\":\"" + safe(s.getEmail())
                + "\",\"phone\":\"" + safe(s.getPhone())
                + "\",\"city\":\"" + safe(s.getCity())
                + "\",\"address\":\"" + safe(s.getAddress())
                + "\",\"logo\":\"" + safe(s.getLogo()) + "\"}}";
    }
}
