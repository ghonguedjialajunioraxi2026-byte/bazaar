package com.ecommerce.util;

import com.ecommerce.dao.AdminDAO;
import com.ecommerce.dao.AdminSessionDAO;
import com.ecommerce.model.Admin;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

/**
 * Matrice de permissions administratives (Phase 14).
 *
 * Hiérarchie : GESTIONNAIRE < ADMIN < SUPER_ADMIN.
 * Le rôle est TOUJOURS résolu serveur (session → admin → rôle) ;
 * le frontend ne choisit jamais son rôle.
 *
 * Matrice réelle (adaptée aux capacités du projet) :
 * - GESTIONNAIRE : lecture + opérationnel (statuts commandes/expéditions,
 *   validation produits, modération avis, notifications). PAS d'argent
 *   (remboursement, vérification paiements, règles, rebuild), PAS de
 *   destructif (suppressions), PAS de diffusion large, PAS de gestion rôles.
 * - ADMIN : tout sauf gestion des rôles/comptes admin.
 * - SUPER_ADMIN : tout.
 */
public final class AdminRole {

    private AdminRole() {}

    public static final String SUPER_ADMIN = "SUPER_ADMIN";
    public static final String ADMIN = "ADMIN";
    public static final String GESTIONNAIRE = "GESTIONNAIRE";

    /** Actions sensibles (money / destructif / diffusion / rôles). */
    public static final String MONEY = "MONEY";           // remboursement, vérif/échec paiement, règles, rebuild
    public static final String DESTRUCTIVE = "DESTRUCTIVE"; // suppressions physiques
    public static final String BROADCAST = "BROADCAST";   // diffusion notifications multi-rôles
    public static final String ROLE_MGMT = "ROLE_MGMT";   // création/comptes admin

    public static int level(String role) {
        if (SUPER_ADMIN.equals(role)) return 3;
        if (ADMIN.equals(role)) return 2;
        if (GESTIONNAIRE.equals(role)) return 1;
        return 0;
    }

    /** Cœur testable pur : un rôle peut-il effectuer une action ? */
    public static boolean allows(String role, String action) {
        int lvl = level(role);
        if (lvl <= 0) return false;
        switch (action) {
            case MONEY:
            case DESTRUCTIVE:
            case BROADCAST:
                return lvl >= 2; // ADMIN+
            case ROLE_MGMT:
                return lvl >= 3; // SUPER_ADMIN seul
            default:
                return true; // lecture + opérationnel : tous les rôles
        }
    }

    /** Résout le rôle depuis la session (null si invalide). */
    public static String resolveRole(HttpServletRequest req) {
        try {
            int adminId = AdminAuth.validateAdmin(req);
            if (adminId <= 0) return null;
            Admin a = new AdminDAO().findById(adminId);
            return a == null ? null : a.getRole();
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Garde : exige un niveau minimum. Écrit 401/403 et retourne 0 si refusé,
     * sinon l'id admin. Ne fait jamais confiance au frontend.
     */
    public static int requireMin(HttpServletRequest req, HttpServletResponse resp, String minRole) throws IOException {
        int adminId = AdminAuth.requireAdmin(req, resp);
        if (adminId <= 0) return 0;
        String role;
        try {
            Admin a = new AdminDAO().findById(adminId);
            role = a == null ? null : a.getRole();
        } catch (Exception e) {
            role = null;
        }
        if (role == null || level(role) < level(minRole)) {
            resp.setStatus(403);
            resp.getWriter().write("{\"error\":\"Action non autorisée pour votre rôle\"}");
            return 0;
        }
        return adminId;
    }

    /** Garde par action sensible (MONEY/DESTRUCTIVE/BROADCAST/ROLE_MGMT). */
    public static int requireAction(HttpServletRequest req, HttpServletResponse resp, String action) throws IOException {
        int adminId = AdminAuth.requireAdmin(req, resp);
        if (adminId <= 0) return 0;
        String role;
        try {
            Admin a = new AdminDAO().findById(adminId);
            role = a == null ? null : a.getRole();
        } catch (Exception e) {
            role = null;
        }
        if (!allows(role, action)) {
            resp.setStatus(403);
            resp.getWriter().write("{\"error\":\"Action non autorisée pour votre rôle\"}");
            return 0;
        }
        return adminId;
    }

    /** Id admin sans écrire (0 si session invalide). */
    public static int validateAdminId(HttpServletRequest req) {
        return AdminAuth.validateAdmin(req);
    }
}
