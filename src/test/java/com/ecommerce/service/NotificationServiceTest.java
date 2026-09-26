package com.ecommerce.service;

import com.ecommerce.dao.NotificationDAO;
import com.ecommerce.model.Notification;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase 13 — Idempotence, préférences et sanitization du service central.
 * DAO simulé en mémoire (aucune base requise).
 */
class NotificationServiceTest {

    /** Faux DAO : simule dedup_key unique + préférences, sans base. */
    static class FakeDAO extends NotificationDAO {
        final Map<String, Integer> byDedup = new HashMap<>();
        final Map<String, Boolean> prefs = new HashMap<>();
        final AtomicInteger seq = new AtomicInteger(100);
        int created = 0;
        boolean failFirstCreateWithDuplicate = false;
        boolean firstCreateDone = false;

        @Override
        public Integer findIdByDedup(String dedupKey) {
            if (failFirstCreateWithDuplicate && !firstCreateDone) return null; // course : pas encore visible
            return byDedup.get(dedupKey);
        }

        @Override
        public int createFull(Notification n) throws SQLException {
            if (failFirstCreateWithDuplicate && !firstCreateDone) {
                firstCreateDone = true;
                // Un concurrent a gagné : la clé existe maintenant.
                int winner = seq.incrementAndGet();
                byDedup.put(n.getDedupKey(), winner);
                throw new SQLException("Duplicate entry 'x' for key 'uq_notif_dedup'");
            }
            if (n.getDedupKey() != null && byDedup.containsKey(n.getDedupKey())) {
                throw new SQLException("Duplicate entry 'x' for key 'uq_notif_dedup'");
            }
            int id = seq.incrementAndGet();
            if (n.getDedupKey() != null) byDedup.put(n.getDedupKey(), id);
            created++;
            return id;
        }

        @Override
        public boolean isEnabled(String role, Integer userId, String category) {
            return prefs.getOrDefault(role + "|" + userId + "|" + category, true);
        }
    }

    @Test
    void duplicatePaymentWebhookCreatesSingleNotification() throws SQLException {
        FakeDAO dao = new FakeDAO();
        NotificationService svc = new NotificationService(dao);
        int first = svc.notify("SUPPLIER", 7, "PAYMENT_SUCCEEDED", "Paiement confirmé",
            "Commande CMD-2026-1.", "PAYMENT", 11, null, "ORDER:1:PAYMENT_SUCCEEDED");
        int second = svc.notify("SUPPLIER", 7, "PAYMENT_SUCCEEDED", "Paiement confirmé",
            "Commande CMD-2026-1.", "PAYMENT", 11, null, "ORDER:1:PAYMENT_SUCCEEDED");
        int third = svc.notify("SUPPLIER", 7, "PAYMENT_SUCCEEDED", "Paiement confirmé",
            "Commande CMD-2026-1.", "PAYMENT", 11, null, "ORDER:1:PAYMENT_SUCCEEDED");
        assertEquals(first, second);
        assertEquals(first, third);
        assertEquals(1, dao.created);
    }

    @Test
    void concurrentDuplicateIsResolvedToExistingId() throws SQLException {
        FakeDAO dao = new FakeDAO();
        dao.failFirstCreateWithDuplicate = true;
        NotificationService svc = new NotificationService(dao);
        int id = svc.notify("SUPPLIER", 7, "DELIVERY_DELIVERED", "Livrée",
            "Commande livrée.", "SHIPMENT", 3, null, "SHIPMENT:3:DELIVERY_DELIVERED");
        assertTrue(id > 100);
        assertEquals(0, dao.created); // le gagnant concurrent compte, pas de doublon local
    }

    @Test
    void distinctEventsCreateDistinctNotifications() throws SQLException {
        FakeDAO dao = new FakeDAO();
        NotificationService svc = new NotificationService(dao);
        int a = svc.notify("SUPPLIER", 7, "ORDER_CREATED", "Commande 1", "C1",
            "ORDER", 1, null, "ORDER:1:SUPPLIER");
        int b = svc.notify("SUPPLIER", 7, "ORDER_CREATED", "Commande 2", "C2",
            "ORDER", 2, null, "ORDER:2:SUPPLIER");
        assertNotEquals(a, b);
        assertEquals(2, dao.created);
    }

    @Test
    void marketingOptOutIsHonoredButSecurityIsForced() throws SQLException {
        FakeDAO dao = new FakeDAO();
        dao.prefs.put("SUPPLIER|7|MARKETING", false);
        dao.prefs.put("SUPPLIER|7|SECURITY", false); // tentative : doit être ignorée
        NotificationService svc = new NotificationService(dao);
        assertEquals(0, svc.notify("SUPPLIER", 7, "MARKETING", "Promo", "-20%",
            null, null, null, "MKT:1"));
        assertTrue(svc.notify("SUPPLIER", 7, "PASSWORD_CHANGED", "Mot de passe modifié",
            "Changement détecté.", "SUPPLIER", 7, null, null) > 0);
    }

    @Test
    void maliciousActionUrlIsNeutralized() throws SQLException {
        final Notification[] last = new Notification[1];
        FakeDAO dao = new FakeDAO() {
            @Override
            public int createFull(Notification n) {
                last[0] = n;
                return 1;
            }
        };
        NotificationService svc = new NotificationService(dao);
        svc.notify("SUPER_ADMIN", null, "SYSTEM", "Info <script>alert(1)</script>", "x",
            null, null, "javascript:alert(1)", null);
        assertNull(last[0].getActionUrl());
        assertNull(last[0].getLink());
    }

    @Test
    void notifyAdminsFanOutToThreeRolesWithDistinctKeys() throws SQLException {
        FakeDAO dao = new FakeDAO();
        NotificationService svc = new NotificationService(dao);
        svc.notifyAdmins("ORDER_CREATED", "Nouvelle commande", "CMD-1",
            "ORDER", 1, null, "ORDER:1:CREATED");
        assertEquals(3, dao.created);
        assertTrue(dao.byDedup.containsKey("ORDER:1:CREATED:SUPER_ADMIN"));
        assertTrue(dao.byDedup.containsKey("ORDER:1:CREATED:ADMIN"));
        assertTrue(dao.byDedup.containsKey("ORDER:1:CREATED:GESTIONNAIRE"));
    }
}
