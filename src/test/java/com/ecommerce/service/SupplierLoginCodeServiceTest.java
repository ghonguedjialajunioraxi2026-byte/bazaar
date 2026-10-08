package com.ecommerce.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Code à 6 caractères (chiffres) — connexion fournisseur en 2 étapes.
 * Aucune DB, aucun SMTP : service en mémoire uniquement.
 */
class SupplierLoginCodeServiceTest {

    private SupplierLoginCodeService svc;

    @BeforeEach
    void setup() {
        svc = new SupplierLoginCodeService();
    }

    @Test
    void codeGenereAleatoire6Chiffres() {
        String c1 = svc.requestCode(1, "fournisseur@bazaar.cm");
        assertNotNull(c1);
        assertTrue(c1.matches("\\d{6}"), "le code doit faire 6 chiffres, reçu : " + c1);

        // Chaque demande génère un code (aléatoire, usage unique).
        String c2 = svc.requestCode(1, "fournisseur@bazaar.cm");
        assertTrue(c2.matches("\\d{6}"));
        // Un seul code actif par email : l'ancien est invalidé (sauf collision 1/1M).
        if (!c1.equals(c2)) {
            assertEquals(SupplierLoginCodeService.VerifyResult.INVALID,
                svc.verify("fournisseur@bazaar.cm", c1));
        }
    }

    @Test
    void deuxUtilisateursOntDesCodesIndependants() {
        String ca = svc.requestCode(1, "a@bazaar.cm");
        String cb = svc.requestCode(2, "b@bazaar.cm");
        assertTrue(ca.matches("\\d{6}"));
        assertTrue(cb.matches("\\d{6}"));
        // Le code de A ne passe pas pour B (sauf collision 1/1M, quasi impossible).
        if (!ca.equals(cb)) {
            assertEquals(SupplierLoginCodeService.VerifyResult.INVALID,
                svc.verify("b@bazaar.cm", ca));
        }
        assertEquals(SupplierLoginCodeService.VerifyResult.OK, svc.verify("a@bazaar.cm", ca));
        assertEquals(2, svc.pendingSupplierId("b@bazaar.cm"));
    }

    @Test
    void verificationOkPuisUsageUnique() {
        String code = svc.requestCode(7, "vendeur@bazaar.cm");
        assertEquals(7, svc.pendingSupplierId("vendeur@bazaar.cm"));
        assertEquals(SupplierLoginCodeService.VerifyResult.OK,
            svc.verify("vendeur@bazaar.cm", code));
        // Usage unique : le même code ne repasse pas.
        assertEquals(SupplierLoginCodeService.VerifyResult.NO_CODE,
            svc.verify("vendeur@bazaar.cm", code));
    }

    @Test
    void codeIncorrectEtLimiteEssais() {
        svc.requestCode(3, "faux@bazaar.cm");
        for (int i = 0; i < SupplierLoginCodeService.MAX_ATTEMPTS - 1; i++) {
            assertEquals(SupplierLoginCodeService.VerifyResult.INVALID,
                svc.verify("faux@bazaar.cm", "000000"));
        }
        assertEquals(SupplierLoginCodeService.VerifyResult.TOO_MANY_ATTEMPTS,
            svc.verify("faux@bazaar.cm", "000000"));
    }

    @Test
    void sansCodeDemandeAccesRefuse() {
        assertEquals(SupplierLoginCodeService.VerifyResult.NO_CODE,
            svc.verify("inconnu@bazaar.cm", "123456"));
        assertEquals(0, svc.pendingSupplierId("inconnu@bazaar.cm"));
    }

    @Test
    void masquageEmailAntiEnumeration() {
        assertEquals("f***@bazaar.cm", SupplierLoginCodeService.maskEmail("fournisseur@bazaar.cm"));
        assertEquals("***", SupplierLoginCodeService.maskEmail("pas-un-email"));
        assertEquals("***", SupplierLoginCodeService.maskEmail(null));
    }

    @Test
    void gabaritEmailContientCodeEtAlerte() {
        String body = EmailService.supplierLoginCodeBody("Ma Boutique", "482913");
        assertTrue(body.contains("482913"));
        assertTrue(body.contains("en train d'être utilisé"));
        assertTrue(body.contains("10 minutes"));
        assertTrue(body.contains("BAZAAR"));
        assertTrue(EmailService.supplierLoginCodeSubject().toLowerCase().contains("connexion"));
    }
}
