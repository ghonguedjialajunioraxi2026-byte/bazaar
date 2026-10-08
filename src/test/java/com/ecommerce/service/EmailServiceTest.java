package com.ecommerce.service;

import com.ecommerce.pawapay.PawaPayConfig;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Emails BAZAAR : validation, gabarits, garde sans DB ni SMTP.
 * Aucun envoi réel, aucun secret.
 */
class EmailServiceTest {

    @Test
    void emailsValidesEtInvalides() {
        assertTrue(EmailService.isValidEmail("client@exemple.com"));
        assertTrue(EmailService.isValidEmail("a.b+tag@bazaar.cm"));
        assertFalse(EmailService.isValidEmail(null));
        assertFalse(EmailService.isValidEmail("pas-un-email"));
        assertFalse(EmailService.isValidEmail("a@b"));
        assertFalse(EmailService.isValidEmail(""));
    }

    @Test
    void destinataireInvalideRefuseSansDb() {
        EmailService svc = new EmailService();
        assertEquals(-1, svc.queueAndSend("pas-un-email", "Sujet", "<p>x</p>", "TEST", null));
        assertEquals(-1, svc.queueAndSend(null, "Sujet", "<p>x</p>", "TEST", null));
        assertEquals(-1, svc.queueAndSend("a@b.cm", "  ", "<p>x</p>", "TEST", null));
    }

    @Test
    void gabaritsCommandeEtPaiement() {
        String body = EmailService.orderCreatedBody("Amina", "CMD-2026-000123", 77000);
        assertTrue(body.contains("CMD-2026-000123"));
        assertTrue(body.contains("77 000 XAF"));
        assertTrue(body.contains("BAZAAR"));

        String ok = EmailService.paymentSucceededBody("Amina", "CMD-2026-000123", 77000);
        assertTrue(ok.contains("confirmé"));
        assertTrue(EmailService.paymentSucceededSubject("CMD-1").contains("CMD-1"));

        String ko = EmailService.paymentFailedBody("Amina", "CMD-2026-000123");
        assertTrue(ko.contains("CMD-2026-000123"));

        String ship = EmailService.shipmentBody("Amina", "CMD-1", "expédiée", "TRK-1");
        assertTrue(ship.contains("TRK-1"));

        String msg = EmailService.messageReceivedBody("Fashion Store", "Bonjour <b>!");
        assertFalse(msg.contains("<b>!"), "le contenu utilisateur doit être échappé");
        assertTrue(msg.contains("Fashion Store"));
    }

    @Test
    void gabaritsSansSecret() {
        String all = EmailService.orderCreatedBody("A", "CMD-1", 1000)
            + EmailService.paymentSucceededBody("A", "CMD-1", 1000)
            + EmailService.refundBody("A", "CMD-1");
        assertFalse(all.contains("SMTP_PASS"));
        assertFalse(all.contains("Bearer"));
    }

    @Test
    void operateursCheckoutDepuisConfig() {
        List<Map<String, Object>> providers = PawaPayConfig.providers();
        assertEquals(2, providers.size());
        assertEquals("MTN_MOMO_CMR", providers.get(0).get("provider"));
        assertEquals("ORANGE_CMR", providers.get(1).get("provider"));
        for (Map<String, Object> p : providers) {
            assertEquals("XAF", p.get("currency"));
            assertEquals("CMR", p.get("country"));
        }
    }
}
