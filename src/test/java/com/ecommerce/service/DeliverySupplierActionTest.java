package com.ecommerce.service;

import com.ecommerce.service.DeliveryFlowService.SupplierAction;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Machine état -> action fournisseur (cartes inline, sans DÉTAIL).
 * Couvre les 5 cas du workflow + gardes négatives. Pure (sans DB).
 */
class DeliverySupplierActionTest {

    @Test
    void cas1PaiementConfirmeNonDemarreeAfficheStart() {
        assertEquals(SupplierAction.START,
            DeliveryFlowService.supplierAction("CONFIRMEE", true, false, false, false, null));
        assertEquals(SupplierAction.START,
            DeliveryFlowService.supplierAction("EN_PREPARATION", true, false, false, false, null));
        assertEquals(SupplierAction.START,
            DeliveryFlowService.supplierAction("EN_LIVRAISON", true, false, false, false, null));
    }

    @Test
    void cas1RefuseSiPaiementNonConfirmeOuStatutIneligible() {
        assertEquals(SupplierAction.NONE,
            DeliveryFlowService.supplierAction("CONFIRMEE", false, false, false, false, null));
        assertEquals(SupplierAction.NONE,
            DeliveryFlowService.supplierAction("EN_ATTENTE", true, false, false, false, null));
        assertEquals(SupplierAction.NONE,
            DeliveryFlowService.supplierAction("LIVREE", true, false, false, false, null));
        assertEquals(SupplierAction.NONE,
            DeliveryFlowService.supplierAction("ANNULEE", true, false, false, false, null));
    }

    @Test
    void cas2DemarreeSansReceptionAttendClient() {
        assertEquals(SupplierAction.WAIT_CLIENT,
            DeliveryFlowService.supplierAction("EN_LIVRAISON", true, true, false, false, null));
    }

    @Test
    void cas3ReceptionConfirmeeAfficheLivre() {
        assertEquals(SupplierAction.LIVRE,
            DeliveryFlowService.supplierAction("EN_LIVRAISON", true, true, true, false, null));
    }

    @Test
    void livreRefuseSiDepartNonConfirme() {
        // Incohérence impossible via API (confirm-receipt exige le départ) :
        // l'interface ne doit pas proposer LIVRÉ.
        assertEquals(SupplierAction.NONE,
            DeliveryFlowService.supplierAction("EN_LIVRAISON", true, false, true, false, null));
    }

    @Test
    void cas4ValideePayoutEnCours() {
        assertEquals(SupplierAction.PAYOUT,
            DeliveryFlowService.supplierAction("LIVREE", true, true, true, true, null));
        assertEquals(SupplierAction.PAYOUT,
            DeliveryFlowService.supplierAction("LIVREE", true, true, true, true, "PENDING"));
        assertEquals(SupplierAction.PAYOUT,
            DeliveryFlowService.supplierAction("LIVREE", true, true, true, true, "PROCESSING"));
        assertEquals(SupplierAction.PAYOUT,
            DeliveryFlowService.supplierAction("LIVREE", true, true, true, true, "FAILED"));
    }

    @Test
    void cas5ValideePayoutTermine() {
        assertEquals(SupplierAction.DONE,
            DeliveryFlowService.supplierAction("LIVREE", true, true, true, true, "COMPLETED"));
        assertEquals(SupplierAction.DONE,
            DeliveryFlowService.supplierAction("LIVREE", true, true, true, true, "PAID"));
    }

    @Test
    void annuleeJamaisDAction() {
        assertEquals(SupplierAction.NONE,
            DeliveryFlowService.supplierAction("ANNULEE", true, true, true, true, "PAID"));
        assertEquals(SupplierAction.NONE,
            DeliveryFlowService.supplierAction("ANNULEE", true, false, false, false, null));
    }
}
