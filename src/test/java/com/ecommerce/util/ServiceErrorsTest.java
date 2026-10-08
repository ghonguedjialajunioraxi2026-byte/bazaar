package com.ecommerce.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Garde-fous CRUD dashboard : messages métier exposés, technique masquée. */
class ServiceErrorsTest {

    @Test
    void messagesMetiersDetectes() {
        assertTrue(ServiceErrors.isBusinessMessage(
            "Suppression impossible : 3 produit(s) sont rattachés.",
            "Suppression impossible", "Catégorie introuvable"));
        assertTrue(ServiceErrors.isBusinessMessage(
            "Ce produit ne peut pas être supprimé car il est lié à des commandes existantes.",
            "Ce produit", "Produit introuvable"));
        assertFalse(ServiceErrors.isBusinessMessage(
            "Cannot delete or update a parent row: a foreign key constraint fails",
            "Suppression impossible", "Ce produit"));
        assertFalse(ServiceErrors.isBusinessMessage(null, "Suppression impossible"));
        assertFalse(ServiceErrors.isBusinessMessage("x"));
    }

    @Test
    void echappementJson() {
        assertEquals("a'b", ServiceErrors.escape("a\"b"));
        assertFalse(ServiceErrors.escape("x\ny").contains("\n"));
    }

    @Test
    void booleenTolerant() {
        assertEquals(Boolean.TRUE, ServiceErrors.toBoolean(true));
        assertEquals(Boolean.FALSE, ServiceErrors.toBoolean("false"));
        assertEquals(Boolean.TRUE, ServiceErrors.toBoolean("1"));
        assertEquals(Boolean.FALSE, ServiceErrors.toBoolean(0));
        assertNull(ServiceErrors.toBoolean(null));
        assertNull(ServiceErrors.toBoolean("peut-être"));
    }

    @Test
    void identifiantSur() {
        assertEquals(12, ServiceErrors.toId(12));
        assertEquals(12, ServiceErrors.toId("12"));
        assertNull(ServiceErrors.toId(null));
        assertNull(ServiceErrors.toId("abc"));
        assertNull(ServiceErrors.toId(0));
        assertNull(ServiceErrors.toId(-3));
    }
}
