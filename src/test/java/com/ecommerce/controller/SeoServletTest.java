package com.ecommerce.controller;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase 18 — Helpers SEO purs : parsing d'URLs, canonique, échappement.
 * Aucune DB requise (les rendus complets sont testés en HTTP, docs/seo.md).
 */
class SeoServletTest {

    @Test
    void leadingIdExtraitLIdentifiantTechnique() {
        assertEquals(123, SeoServlet.leadingId("/123"));
        assertEquals(123, SeoServlet.leadingId("/123-sneakers-air-urban"));
        assertEquals(7, SeoServlet.leadingId("7"));
        assertEquals(0, SeoServlet.leadingId(null));
        assertEquals(0, SeoServlet.leadingId("/"));
        assertEquals(0, SeoServlet.leadingId("/slug-sans-id"));
        assertEquals(0, SeoServlet.leadingId("/produit/123"));
    }

    @Test
    void slugCategorieStrictSansSlash() {
        assertEquals("montre", SeoServlet.slugOf("/montre"));
        assertNull(SeoServlet.slugOf(null));
        assertNull(SeoServlet.slugOf("/"));
        assertNull(SeoServlet.slugOf("/a/b")); // traversal-like refusé
    }

    @Test
    void metaDescriptionBorneeSansCouperUnMot() {
        assertTrue(SeoServlet.metaDescription("a".repeat(500)).length() <= 155);
        assertEquals("Court.", SeoServlet.metaDescription("Court."));
        assertTrue(SeoServlet.metaDescription(null).contains("MBOA STYLE"));
    }

    @Test
    void echappementNeutraliseLeXssStocke() {
        String evil = "<script>alert(1)</script><img src=x onerror=alert(2)>";
        assertFalse(SeoServlet.esc(evil).contains("<script>"));
        assertFalse(SeoServlet.esc(evil).contains("<img"));
        assertTrue(SeoServlet.escAttr("a\"b").contains("&quot;"));
        String json = SeoServlet.jsonStr("a\"b\\c\nd");
        assertTrue(json.startsWith("\"") && json.endsWith("\""));
        assertFalse(json.contains("\n"));
    }

    @Test
    void firstImageNeCreeJamaisDUrlFictive() {
        assertNull(SeoServlet.firstImage("https://mboastyle.cm", null));
        assertNull(SeoServlet.firstImage("https://mboastyle.cm", Collections.emptyList()));
        assertEquals("https://cdn.example.com/p.jpg",
            SeoServlet.firstImage("https://mboastyle.cm",
                Arrays.asList("", "https://cdn.example.com/p.jpg")));
        assertEquals("https://mboastyle.cm/uploads/products/a.jpg",
            SeoServlet.firstImage("https://mboastyle.cm",
                Collections.singletonList("uploads/products/a.jpg")));
    }

    @Test
    void produitsNonPublicsExclus() {
        assertTrue(SeoServlet.isPublicProduct("ACTIVE"));
        assertTrue(SeoServlet.isPublicProduct("APPROVED"));
        assertFalse(SeoServlet.isPublicProduct("PENDING"));
        assertFalse(SeoServlet.isPublicProduct("REJECTED"));
        assertFalse(SeoServlet.isPublicProduct("INACTIVE"));
        assertFalse(SeoServlet.isPublicProduct(null));
    }

    @Test
    void slugifyUrlSafe() {
        assertEquals("sneakers-air-urban", SeoServlet.slugify("Sneakers Air Urban"));
        assertEquals("boutique", SeoServlet.slugify("!!!"));
        assertFalse(SeoServlet.slugify("Chaussures & Sacs").contains("&"));
    }
}
