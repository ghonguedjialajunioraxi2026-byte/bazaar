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
     * Authentifie un fournisseur par email + mot de passe hasché.
     * Retourne le fournisseur si ok, sinon null.
     */
    public Supplier login(String email, String password) throws SQLException {
        if (email == null || password == null) return null;
        Supplier s = supplierDAO.findByEmail(email.trim().toLowerCase());
        if (s == null) return null;
        if (!s.isActive()) return null;
        String hash = s.getPasswordHash();
        if (hash == null || hash.isEmpty()) return null;
        if (!PasswordUtil.verify(password, hash)) return null;
        supplierDAO.updateLastLogin(s.getId());
        return s;
    }

    public String createSession(int supplierId, String ip, String userAgent) throws SQLException {
        return sessionDAO.createSession(supplierId, ip, userAgent, 12);
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
        n.setLogo(picture);
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
        return sessionDAO.validateSession(token);
    }

    public void logout(String token) throws SQLException {
        sessionDAO.invalidateSession(token);
    }
}
