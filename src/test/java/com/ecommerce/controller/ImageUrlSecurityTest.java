package com.ecommerce.controller;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase 15 — Les URL d'images externes stockées sont validées serveur :
 * http(s) uniquement, jamais de schéma actif.
 */
class ImageUrlSecurityTest {

    @Test
    void urlsSainesAcceptees() {
        assertTrue(ProductImageServlet.isSafeImageUrl("https://cdn.example.com/p.jpg"));
        assertTrue(ProductImageServlet.isSafeImageUrl("http://cdn.example.com/p.png"));
        assertTrue(ProductImageServlet.isSafeImageUrl("/uploads/products/abc123.jpg"));
        assertTrue(ProductImageServlet.isSafeImageUrl("uploads/products/abc.jpg"));
    }

    @Test
    void urlsDangereusesRefusees() {
        assertFalse(ProductImageServlet.isSafeImageUrl("javascript:alert(1)"));
        assertFalse(ProductImageServlet.isSafeImageUrl("JaVaScRiPt:alert(1)"));
        assertFalse(ProductImageServlet.isSafeImageUrl("data:text/html,<script>alert(1)</script>"));
        assertFalse(ProductImageServlet.isSafeImageUrl("data:image/svg+xml,<svg onload=alert(1)>"));
        assertFalse(ProductImageServlet.isSafeImageUrl("ftp://evil.example/x.jpg"));
        assertFalse(ProductImageServlet.isSafeImageUrl("//evil.example/x.jpg"));
        assertFalse(ProductImageServlet.isSafeImageUrl("https://example.com/a\"onerror=\"alert(1)"));
        assertFalse(ProductImageServlet.isSafeImageUrl("https://example.com/a b.jpg"));
        assertFalse(ProductImageServlet.isSafeImageUrl(null));
        assertFalse(ProductImageServlet.isSafeImageUrl("   "));
    }
}
