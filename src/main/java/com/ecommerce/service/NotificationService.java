package com.ecommerce.service;

import com.ecommerce.dao.NotificationDAO;
import com.ecommerce.model.Notification;

import java.sql.SQLException;
import java.util.List;

/**
 * Service centralisé de notifications (Phase 13).
 *
 * Schéma : ÉVÉNEMENT MÉTIER -> NotificationService -> DAO -> DB -> UI.
 * Règles :
 * - Le SERVEUR déduit le destinataire de l'événement (jamais du frontend).
 * - Idempotence via dedup_key : 1 événement -> 1 notification transactionnelle.
 * - Textes générés depuis des données réelles (aucune fiction).
 * - Respect des préférences sauf catégories critiques (SECURITY).
 */
public class NotificationService {

    private final NotificationDAO dao;

    public NotificationService() {
        this.dao = new NotificationDAO();
    }

    /** Injection pour tests. */
    NotificationService(NotificationDAO dao) {
        this.dao = dao;
    }

    /**
     * Émet une notification de façon idempotente.
     * @param targetRole rôle destinataire (ADMIN, SUPER_ADMIN, GESTIONNAIRE, SUPPLIER...)
     * @param targetUserId id destinataire ou null (tous du rôle)
     * @param eventCode code de la nomenclature (ORDER_CREATED, ...)
     * @param title titre réel (max 200)
     * @param message message réel (max 2000)
     * @param entityType type d'entité (ORDER, PAYMENT, SHIPMENT, CONVERSATION, OFFER, PRODUCT)
     * @param entityId id d'entité ou null
     * @param actionUrl URL relative ou null (validée)
     * @param dedupKey clé logique unique (ex "ORDER:12:PAYMENT_SUCCEEDED") ou null
     * @return id créé, ou id existant si doublon (idempotence)
     */
    public int notify(String targetRole, Integer targetUserId,
                      String eventCode, String title, String message,
                      String entityType, Integer entityId,
                      String actionUrl, String dedupKey) throws SQLException {
        String code = NotificationType.isKnown(eventCode) ? eventCode : "SYSTEM";
        String category = NotificationType.categoryOf(code);

        // Préférences : SECURITY toujours envoyée, le reste peut être opt-out.
        if (!NotificationType.CRITICAL_CATEGORIES.contains(category)
                && !dao.isEnabled(targetRole, targetUserId, category)) {
            return 0;
        }

        // Idempotence : si la clé existe déjà, ne pas dupliquer.
        if (dedupKey != null && !dedupKey.trim().isEmpty()) {
            Integer existing = dao.findIdByDedup(dedupKey.trim());
            if (existing != null) return existing;
        }

        Notification n = new Notification();
        n.setTargetRole(targetRole);
        n.setTargetUserId(targetUserId);
        n.setType(category);
        n.setEventCode(code);
        n.setCategory(category);
        n.setTitle(NotificationType.clamp(title == null ? code : title, 200));
        n.setMessage(NotificationType.clamp(message == null ? "" : message, 2000));
        n.setPriority(NotificationType.defaultPriorityOf(code));
        n.setEntityType(entityType);
        n.setEntityId(entityId);
        n.setActionUrl(NotificationType.sanitizeActionUrl(actionUrl));
        if (n.getActionUrl() == null) n.setLink(null);
        else n.setLink(n.getActionUrl());
        n.setDedupKey(dedupKey == null ? null : dedupKey.trim());

        try {
            return dao.createFull(n);
        } catch (SQLException duplicate) {
            // Course concurrente sur uq_notif_dedup -> relire (idempotence).
            if (isDuplicateKey(duplicate) && n.getDedupKey() != null) {
                Integer existing = dao.findIdByDedup(n.getDedupKey());
                if (existing != null) return existing;
            }
            throw duplicate;
        }
    }

    /** Diffusion admin : même événement aux 3 rôles (clés dédupliquées par rôle). */
    public void notifyAdmins(String eventCode, String title, String message,
                             String entityType, Integer entityId,
                             String actionUrl, String dedupBase) throws SQLException {
        String[] roles = {"SUPER_ADMIN", "ADMIN", "GESTIONNAIRE"};
        for (String role : roles) {
            String key = dedupBase == null ? null : dedupBase + ":" + role;
            notify(role, null, eventCode, title, message, entityType, entityId, actionUrl, key);
        }
    }

    public void notifySupplier(int supplierId, String eventCode, String title, String message,
                               String entityType, Integer entityId,
                               String actionUrl, String dedupKey) throws SQLException {
        notify("SUPPLIER", supplierId, eventCode, title, message, entityType, entityId, actionUrl, dedupKey);
    }

    private boolean isDuplicateKey(SQLException e) {
        String m = e.getMessage() == null ? "" : e.getMessage();
        return m.contains("uq_notif_dedup") || m.contains("Duplicate entry");
    }

    // ---- Lecture déléguée (scope vérifié par l'appelant) ----

    public List<Notification> pageForRole(String role, String category, Boolean unreadOnly,
                                          String query, int limit, int offset) throws SQLException {
        return dao.findFiltered(role, category, unreadOnly, query, limit, offset);
    }
}
