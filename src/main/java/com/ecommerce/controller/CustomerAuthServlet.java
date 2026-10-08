package com.ecommerce.controller;

import com.ecommerce.dao.CustomerDAO;
import com.ecommerce.dao.CustomerSessionDAO;
import com.ecommerce.model.Customer;
import com.ecommerce.service.EmailService;
import com.ecommerce.util.CustomerAuth;
import com.ecommerce.util.GoogleAuthUtil;
import com.ecommerce.util.JsonUtil;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.MultipartConfig;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.Part;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Authentification client BAZAAR (compte léger nom + email, sans mot de passe).
 *
 * - POST /api/customer/login   {name, email, avatar?} → session 30 jours
 *   (avatar : URL d'image facultative, visuel réservé si absente)
 * - POST /api/customer/avatar  (Bearer, multipart file) → photo de profil
 * - POST /api/customer/google  {idToken|credential} → session (vérifié tokeninfo)
 * - GET  /api/customer/me      (Bearer) → identité
 * - POST /api/customer/logout  (Bearer) → invalidation réelle
 * - GET  /api/customer/google-client-id → client ID effectif (public)
 *
 * Token : header Authorization Bearer uniquement. Réponses : jamais de
 * secret (id + nom + email + avatar uniquement).
 */
@MultipartConfig(
    fileSizeThreshold = 1024 * 1024,
    maxFileSize = 10 * 1024 * 1024,
    maxRequestSize = 12 * 1024 * 1024
)
@WebServlet(urlPatterns = {"/api/customer/login", "/api/customer/google",
        "/api/customer/google-client-id", "/api/customer/me", "/api/customer/logout",
        "/api/customer/avatar"})
public class CustomerAuthServlet extends HttpServlet {

    /** Sessions client : 30 jours (parcours d'achat sans friction). */
    private static final int SESSION_HOURS = 30 * 24;

    private final CustomerDAO customerDAO = new CustomerDAO();
    private final CustomerSessionDAO sessionDAO = new CustomerSessionDAO();

    private static String extractToken(HttpServletRequest req) {
        String token = req.getHeader("Authorization");
        if (token != null && token.startsWith("Bearer ")) return token.substring(7);
        return null;
    }

    private static Map<String, Object> customerView(Customer c) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", c.getId());
        m.put("name", c.getName());
        m.put("email", c.getEmail());
        // Toujours renseigné : photo réelle ou visuel client réservé.
        m.put("avatar", com.ecommerce.util.AvatarDefaults.customerAvatarOrDefault(c.getAvatar()));
        return m;
    }

    private void writeAuth(HttpServletResponse resp, String token, Customer c) throws IOException {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("success", true);
        out.put("token", token);
        out.put("customer", customerView(c));
        resp.getWriter().write(JsonUtil.toJson(out));
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");
        String uri = req.getRequestURI();
        try {
            if (uri.endsWith("/api/customer/google-client-id")) {
                resp.getWriter().write("{\"clientId\":\"" + GoogleAuthUtil.GOOGLE_CLIENT_ID + "\"}");
                return;
            }
            if (uri.endsWith("/api/customer/me")) {
                int customerId = CustomerAuth.validateCustomer(req);
                if (customerId <= 0) {
                    resp.setStatus(401);
                    resp.getWriter().write("{\"success\":false,\"message\":\"Authentification requise\"}");
                    return;
                }
                Customer c = customerDAO.findById(customerId);
                if (c == null) {
                    resp.setStatus(401);
                    resp.getWriter().write("{\"success\":false,\"message\":\"Session invalide\"}");
                    return;
                }
                Map<String, Object> out = new LinkedHashMap<>();
                out.put("success", true);
                out.put("customer", customerView(c));
                resp.getWriter().write(JsonUtil.toJson(out));
                return;
            }
        } catch (Exception e) {
            resp.setStatus(500);
            resp.getWriter().write("{\"success\":false,\"message\":\"Impossible de traiter votre demande.\"}");
            return;
        }
        resp.setStatus(404);
        resp.getWriter().write("{\"success\":false,\"message\":\"Route inconnue\"}");
    }

    @Override
    @SuppressWarnings("unchecked")
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");
        String uri = req.getRequestURI();
        try {
            // Photo de profil (multipart, authentifié). AVANT toute lecture
            // du body : getReader() invaliderait getParts().
            if (uri.endsWith("/api/customer/avatar")) {
                handleAvatarUpload(req, resp);
                return;
            }
            StringBuilder sb = new StringBuilder();
            try (BufferedReader r = req.getReader()) {
                String line;
                while ((line = r.readLine()) != null) sb.append(line);
            }
            Map<String, Object> body = new LinkedHashMap<>();
            if (sb.length() > 0) {
                try {
                    Object o = JsonUtil.fromJson(sb.toString(), Map.class);
                    if (o instanceof Map) body = (Map<String, Object>) o;
                } catch (Exception ignored) {}
            }

            // Déconnexion : invalidation RÉELLE de la session.
            if (uri.endsWith("/api/customer/logout")) {
                sessionDAO.invalidateSession(extractToken(req));
                resp.getWriter().write("{\"success\":true}");
                return;
            }

            // Google : vérification réelle du idToken (jamais de clic = succès).
            if (uri.endsWith("/api/customer/google")) {
                Object tok = body.get("idToken") != null ? body.get("idToken") : body.get("credential");
                if (tok == null || String.valueOf(tok).trim().isEmpty()) {
                    resp.setStatus(400);
                    resp.getWriter().write("{\"success\":false,\"message\":\"Jeton Google requis\"}");
                    return;
                }
                GoogleAuthUtil.GoogleUser gu;
                try {
                    gu = GoogleAuthUtil.verify(String.valueOf(tok).trim());
                } catch (Exception e) {
                    resp.setStatus(401);
                    resp.getWriter().write("{\"success\":false,\"message\":\"Jeton Google invalide ou expiré\"}");
                    return;
                }
                Customer c = customerDAO.findByGoogleSub(gu.sub);
                if (c == null) {
                    c = customerDAO.findByEmail(gu.email);
                    if (c != null) {
                        try { customerDAO.linkGoogle(c.getId(), gu.sub); } catch (Exception ignored) {}
                        // Photo Google adoptée si le compte n'en a pas encore.
                        if ((c.getAvatar() == null || c.getAvatar().trim().isEmpty())
                                && com.ecommerce.util.AvatarDefaults.isAcceptableImageUrl(gu.picture)) {
                            try { customerDAO.updateAvatar(c.getId(), gu.picture); c.setAvatar(gu.picture); } catch (Exception ignored) {}
                        }
                    } else {
                        String gAvatar = com.ecommerce.util.AvatarDefaults.isAcceptableImageUrl(gu.picture) ? gu.picture : null;
                        int id = customerDAO.createWithGoogle(gu.name, gu.email, gu.sub, gAvatar);
                        c = customerDAO.findById(id);
                    }
                }
                if (c == null) {
                    resp.setStatus(500);
                    resp.getWriter().write("{\"success\":false,\"message\":\"Impossible de traiter votre demande.\"}");
                    return;
                }
                String token = sessionDAO.createSession(c.getId(),
                    req.getRemoteAddr(), req.getHeader("User-Agent"), SESSION_HOURS);
                writeAuth(resp, token, c);
                return;
            }

            // Login nom + email (sans mot de passe, par design).
            // avatar : URL d'image FACULTATIVE (sinon visuel réservé).
            if (uri.endsWith("/api/customer/login")) {
                String name = body.get("name") == null ? "" : String.valueOf(body.get("name")).trim();
                String email = body.get("email") == null ? "" : String.valueOf(body.get("email")).trim();
                String avatar = body.get("avatar") == null ? null : String.valueOf(body.get("avatar")).trim();
                // Anti-abus : même garde que le login fournisseur (création de
                // session = effet sensible, email déclaratif sans preuve).
                String attemptKey = com.ecommerce.service.LoginAttemptService.key(
                    "customer", email.isEmpty() ? "?" : email, req.getRemoteAddr());
                if (com.ecommerce.service.LoginAttemptService.get().isBlocked(attemptKey)) {
                    resp.setStatus(429);
                    resp.getWriter().write("{\"success\":false,\"message\":\"Trop de tentatives. Réessayez dans 15 minutes.\"}");
                    return;
                }
                // Chaque connexion compte (pas d'échec possible ici : email
                // déclaratif) : 5 connexions / 15 min / (email, IP) max.
                com.ecommerce.service.LoginAttemptService.get().recordFailure(attemptKey);
                if (avatar != null && !com.ecommerce.util.AvatarDefaults.isAcceptableImageUrl(avatar)) {
                    avatar = null;
                }
                if (name.isEmpty()) {
                    resp.setStatus(400);
                    resp.getWriter().write("{\"success\":false,\"message\":\"Votre nom est requis\"}");
                    return;
                }
                if (!EmailService.isValidEmail(email)) {
                    resp.setStatus(400);
                    resp.getWriter().write("{\"success\":false,\"message\":\"Adresse email invalide\"}");
                    return;
                }
                Customer c = customerDAO.findByEmail(email);
                if (c == null) {
                    int id = customerDAO.create(name, email, avatar);
                    c = customerDAO.findById(id);
                } else {
                    if (!c.getName().equals(name)) {
                        try { customerDAO.updateName(c.getId(), name); c.setName(name); } catch (Exception ignored) {}
                    }
                    // Photo ajoutée après coup : adoptée si le compte n'en a pas.
                    if (avatar != null && (c.getAvatar() == null || c.getAvatar().trim().isEmpty())) {
                        try { customerDAO.updateAvatar(c.getId(), avatar); c.setAvatar(avatar); } catch (Exception ignored) {}
                    }
                }
                if (c == null) {
                    resp.setStatus(500);
                    resp.getWriter().write("{\"success\":false,\"message\":\"Impossible de traiter votre demande.\"}");
                    return;
                }
                String token = sessionDAO.createSession(c.getId(),
                    req.getRemoteAddr(), req.getHeader("User-Agent"), SESSION_HOURS);
                writeAuth(resp, token, c);
                return;
            }
        } catch (Exception e) {
            resp.setStatus(500);
            resp.getWriter().write("{\"success\":false,\"message\":\"Impossible de traiter votre demande.\"}");
            return;
        }
        resp.setStatus(404);
        resp.getWriter().write("{\"success\":false,\"message\":\"Route inconnue\"}");
    }

    /**
     * Upload de la photo de profil client (multipart {@code file}, Bearer).
     * Appelée AVANT toute lecture du body JSON (getReader invaliderait getParts).
     */
    private void handleAvatarUpload(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        int customerId = CustomerAuth.validateCustomer(req);
        if (customerId <= 0) {
            resp.setStatus(401);
            resp.getWriter().write("{\"success\":false,\"message\":\"Authentification requise\"}");
            return;
        }
        String avatar = null;
        try {
            for (Part part : req.getParts()) {
                String ct = part.getContentType();
                if (("file".equals(part.getName())
                        || (ct != null && ct.startsWith("image/")))
                        && part.getSize() > 0) {
                    avatar = com.ecommerce.util.UploadUtil.saveImage(
                        part, com.ecommerce.util.UploadUtil.SUBDIR_AVATARS, req);
                    break;
                }
            }
        } catch (Exception e) {
            resp.setStatus(400);
            resp.getWriter().write("{\"success\":false,\"message\":\"Photo invalide (JPG, PNG, GIF ou WebP, 10 Mo max).\"}");
            return;
        }
        if (avatar == null) {
            resp.setStatus(400);
            resp.getWriter().write("{\"success\":false,\"message\":\"Aucune photo reçue.\"}");
            return;
        }
        try {
            customerDAO.updateAvatar(customerId, avatar);
        } catch (Exception ignored) {}
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("success", true);
        out.put("avatar", avatar);
        resp.getWriter().write(JsonUtil.toJson(out));
    }
}
