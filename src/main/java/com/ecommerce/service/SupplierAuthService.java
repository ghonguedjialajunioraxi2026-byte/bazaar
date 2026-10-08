package com.ecommerce.service;

import com.ecommerce.dao.SupplierDAO;
import com.ecommerce.dao.SupplierSessionDAO;
import com.ecommerce.model.Supplier;
import com.ecommerce.util.PasswordUtil;

import java.sql.SQLException;

public class SupplierAuthService {

    private final SupplierDAO supplierDAO = new SupplierDAO();
    private final SupplierSessionDAO sessionDAO = new SupplierSessionDAO();

    /**
     * Vérifie email + mot de passe SANS effet de bord (sans session,
     * sans last_login). Utilisé par l'étape 1 de la connexion en 2 étapes :
     * le last_login n'est mis à jour qu'après validation du code email.
     * Retourne le fournisseur si ok, sinon null.
     */
    public Supplier checkPassword(String email, String password) throws SQLException {
        if (email == null || password == null) return null;
        Supplier s = supplierDAO.findByEmail(email.trim().toLowerCase());
        if (s == null) return null;
        if (!s.isActive()) return null;
        String hash = s.getPasswordHash();
        if (hash == null || hash.isEmpty()) return null;
        if (!PasswordUtil.verify(password, hash)) return null;
        // Migration transparente vers PBKDF2 (comptes pré-Phase 15).
        if (PasswordUtil.needsRehash(hash)) {
            try {
                supplierDAO.updatePassword(s.getId(), PasswordUtil.hash(password));
            } catch (Exception ignored) {}
        }
        return s;
    }

    /**
     * Authentifie un fournisseur par email + mot de passe hasché.
     * Retourne le fournisseur si ok, sinon null.
     * (Conserve le comportement historique : connexion directe.
     * La route /login utilise désormais checkPassword + code email.)
     */
    public Supplier login(String email, String password) throws SQLException {
        Supplier s = checkPassword(email, password);
        if (s == null) return null;
        supplierDAO.updateLastLogin(s.getId());
        return s;
    }

    /** Marque la connexion comme effective (après validation du code email). */
    public void markLoggedIn(int supplierId) throws SQLException {
        supplierDAO.updateLastLogin(supplierId);
    }

    /** Durée de session : 30 jours + renouvellement glissant (voir validateToken).
     *  Le fournisseur reste connecté jusqu'à déconnexion explicite. */
    public static final int SESSION_HOURS = 720;

    public String createSession(int supplierId, String ip, String userAgent) throws SQLException {
        return sessionDAO.createSession(supplierId, ip, userAgent, SESSION_HOURS);
    }

    /**
     * Profil finalisé = nom de boutique, téléphone, ville et adresse renseignés.
     * Utilisé après une connexion Google pour rediriger vers l'écran
     * de complétion du profil si besoin.
     */
    public boolean isProfileComplete(Supplier s) {
        if (s == null) return false;
        return notBlank(s.getName()) && notBlank(s.getPhone())
                && notBlank(s.getCity()) && notBlank(s.getAddress());
    }

    private boolean notBlank(String v) {
        return v != null && !v.trim().isEmpty();
    }

    /**
     * Connexion / inscription via Google Sign-In.
     * - Si un compte existe avec cet email (ou google_id), on le connecte
     *   et on lie le google_id si ce n'est pas encore fait.
     * - Sinon on crée automatiquement la boutique (nom = nom Google).
     * Retourne le fournisseur connecté. Lève SQLException si compte désactivé.
     */
    public Supplier loginWithGoogle(String googleId, String email, String name, String picture) throws SQLException {
        Supplier s = supplierDAO.findByGoogleId(googleId);
        if (s == null && email != null) {
            s = supplierDAO.findByEmail(email.trim().toLowerCase());
        }
        if (s != null) {
            if (!s.isActive()) throw new SQLException("Ce compte fournisseur est désactivé");
            if (s.getGoogleId() == null || s.getGoogleId().isEmpty()) {
                supplierDAO.linkGoogleAccount(s.getId(), googleId, picture);
                s.setGoogleId(googleId);
                if ((s.getLogo() == null || s.getLogo().isEmpty()) && picture != null) {
                    s.setLogo(picture);
                }
            }
            supplierDAO.updateLastLogin(s.getId());
            return s;
        }
        // Création automatique du compte
        Supplier n = new Supplier();
        String shopName = (name == null || name.trim().isEmpty())
                ? email.substring(0, email.indexOf('@')) : name.trim();
        n.setName(shopName);
        n.setEmail(email.trim().toLowerCase());
        n.setContactName(name);
        // Photo facultative : photo Google si dispo, sinon visuel boutique réservé.
        n.setLogo((picture == null || picture.trim().isEmpty())
            ? com.ecommerce.util.AvatarDefaults.DEFAULT_SUPPLIER_LOGO : picture);
        n.setGoogleId(googleId);
        // Mot de passe aléatoire inutilisable : la connexion passe par Google
        n.setPasswordHash(PasswordUtil.hash("GOOGLE-" + googleId + "-" + System.nanoTime()));
        n.setActive(true);
        n.setCommissionRate(10.0);
        int id = supplierDAO.create(n);
        if (id <= 0) throw new SQLException("Impossible de créer le compte Google");
        return supplierDAO.findById(id);
    }

    public int validateToken(String token) throws SQLException {
        int id = sessionDAO.validateSession(token);
        if (id > 0) {
            // Renouvellement glissant (best-effort : n'invalide jamais une session valide).
            try { sessionDAO.refreshSession(token, SESSION_HOURS); } catch (Exception ignored) {}
        }
        return id;
    }

    public void logout(String token) throws SQLException {
        sessionDAO.invalidateSession(token);
    }
}
