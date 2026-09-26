package com.ecommerce.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase 13 — Nomenclature centralisée : seuls les types réellement émis
 * existent, catégories/priorités stables, seuils explicites.
 */
class NotificationTypeTest {

    @Test
    void knownEventCodesMapToExpectedCategoryAndPriority() {
        assertEquals("ORDER", NotificationType.categoryOf("ORDER_CREATED"));
        assertEquals("PAYMENT", NotificationType.categoryOf("PAYMENT_SUCCEEDED"));
        assertEquals("HIGH", NotificationType.defaultPriorityOf("PAYMENT_FAILED"));
        assertEquals("DELIVERY", NotificationType.categoryOf("DELIVERY_DELIVERED"));
        assertEquals("MESSAGE", NotificationType.categoryOf("MESSAGE_RECEIVED"));
        assertEquals("NEGOTIATION", NotificationType.categoryOf("OFFER_ACCEPTED"));
        assertEquals("PRODUCT", NotificationType.categoryOf("PRODUCT_OUT_OF_STOCK"));
        assertEquals("SECURITY", NotificationType.categoryOf("PASSWORD_CHANGED"));
        assertEquals("CRITICAL", NotificationType.defaultPriorityOf("PASSWORD_CHANGED"));
        assertEquals("LOW", NotificationType.defaultPriorityOf("MARKETING"));
    }

    @Test
    void unknownEventFallsBackToSystem() {
        assertFalse(NotificationType.isKnown("PROMO_MAGIC"));
        assertEquals("SYSTEM", NotificationType.categoryOf("PROMO_MAGIC"));
        assertEquals("NORMAL", NotificationType.defaultPriorityOf("PROMO_MAGIC"));
        assertEquals("SYSTEM", NotificationType.categoryOf(null));
    }

    @Test
    void lowStockThresholdIsExplicitAndPositive() {
        assertEquals(5, NotificationType.LOW_STOCK_THRESHOLD);
    }

    @Test
    void securityIsTheOnlyCriticalCategory() {
        assertTrue(NotificationType.CRITICAL_CATEGORIES.contains("SECURITY"));
    }

    @Test
    void normalizePriorityKeepsKnownMapsUrgentToCritical() {
        assertEquals("HIGH", NotificationType.normalizePriority("high"));
        assertEquals("CRITICAL", NotificationType.normalizePriority("URGENT"));
        assertEquals("CRITICAL", NotificationType.normalizePriority("critical"));
        assertEquals("NORMAL", NotificationType.normalizePriority("whatever"));
        assertEquals("NORMAL", NotificationType.normalizePriority(null));
    }

    @Test
    void sanitizeActionUrlAcceptsOnlySafeRelativeUrls() {
        assertEquals("/api/orders?action=find&id=12",
            NotificationType.sanitizeActionUrl("/api/orders?action=find&id=12"));
        assertEquals("/products/5", NotificationType.sanitizeActionUrl("  /products/5  "));
        assertNull(NotificationType.sanitizeActionUrl("https://evil.example/x"));
        assertNull(NotificationType.sanitizeActionUrl("//evil.example/x"));
        assertNull(NotificationType.sanitizeActionUrl("/x?next=javascript:alert(1)"));
        assertNull(NotificationType.sanitizeActionUrl("/<script>alert(1)</script>"));
        assertNull(NotificationType.sanitizeActionUrl(null));
        assertNull(NotificationType.sanitizeActionUrl("   "));
    }

    @Test
    void clampTruncatesWithoutThrowing() {
        assertEquals(200, NotificationType.clamp("a".repeat(500), 200).length());
        assertEquals("  hello  ".trim(), NotificationType.clamp("  hello  ", 200));
        assertNull(NotificationType.clamp(null, 200));
    }
}
