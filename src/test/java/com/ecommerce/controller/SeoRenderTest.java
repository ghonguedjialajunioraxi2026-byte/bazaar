package com.ecommerce.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase 18 — Rendu SEO réel contre MySQL (données de développement).
 * Vérifie statuts, canonique, JSON-LD et absence de fuite privée.
 * Requiert la base locale (sinon tests ignorés proprement).
 */
class SeoRenderTest {

    private static boolean dbUp() {
        try (java.sql.Connection c =
                 com.ecommerce.config.DBConnection.getConnection()) {
            return c.isValid(2);
        } catch (Exception e) {
            return false;
        }
    }

    static class FakeResp {
        int status = 200;
        final Map<String, String> headers = new HashMap<>();
        final StringWriter body = new StringWriter();
        String redirect;
    }

    private FakeResp render(String servletPath, String pathInfo) throws Exception {
        FakeResp cap = new FakeResp();
        HttpServletRequest req = (HttpServletRequest) Proxy.newProxyInstance(
            getClass().getClassLoader(), new Class<?>[]{HttpServletRequest.class},
            (proxy, m, args) -> {
                switch (m.getName()) {
                    case "getServletPath": return servletPath;
                    case "getPathInfo": return pathInfo;
                    case "getScheme": return "https";
                    case "getServerName": return "mboastyle.cm";
                    case "getServerPort": return 443;
                    case "getContextPath": return "";
                    case "getRequestURI": return servletPath + (pathInfo == null ? "" : pathInfo);
                    default:
                        Class<?> rt = m.getReturnType();
                        if (rt == boolean.class) return false;
                        if (rt == int.class) return 0;
                        return null;
                }
            });
        HttpServletResponse resp = (HttpServletResponse) Proxy.newProxyInstance(
            getClass().getClassLoader(), new Class<?>[]{HttpServletResponse.class},
            (proxy, m, args) -> {
                switch (m.getName()) {
                    case "getWriter": return new PrintWriter(cap.body);
                    case "sendError": cap.status = (Integer) args[0]; return null;
                    case "setStatus": cap.status = (Integer) args[0]; return null;
                    case "setHeader": cap.headers.put((String) args[0], (String) args[1]); return null;
                    case "setContentType": case "setCharacterEncoding": return null;
                    default: return null;
                }
            });
        new SeoServlet().doGet(req, resp);
        return cap;
    }

    @Test
    void produitPublicRend200AvecJsonLdReel() throws Exception {
        org.junit.jupiter.api.Assumptions.assumeTrue(dbUp(), "MySQL locale requise");
        FakeResp r = render("/produit", "/45/monter-connecter");
        assertEquals(200, r.status, r.body.toString().substring(0, Math.min(200, r.body.toString().length())));
        String html = r.body.toString();
        assertTrue(html.contains("\"@type\":\"Product\""));
        assertTrue(html.contains("\"priceCurrency\":\"XAF\""));
        assertTrue(html.contains("\"price\":2000"));
        assertTrue(html.contains("<h1>"));
        assertTrue(html.contains("rel=\"canonical\""));
        assertTrue(html.contains("https://mboastyle.cm/produit/45/monter-connecter"));
        assertTrue(html.contains("BreadcrumbList"));
        // JSON-LD syntaxiquement valide (parse réel, pas de simple substring).
        String script = html.substring(
            html.indexOf("<script type=\"application/ld+json\">") + 35);
        script = script.substring(0, script.indexOf("</script>"));
        com.google.gson.JsonObject json =
            com.google.gson.JsonParser.parseString(script).getAsJsonObject();
        assertEquals("Product", json.get("@type").getAsString());
        assertEquals(2000, json.getAsJsonObject("offers").get("price").getAsInt());
        assertEquals("XAF",
            json.getAsJsonObject("offers").get("priceCurrency").getAsString());
        // Pas de données privées.
        assertFalse(html.contains("password"));
        assertFalse(html.toLowerCase().contains("commission"));
    }

    @Test
    void produitSansSlugRedirige301VersCanonique() throws Exception {
        org.junit.jupiter.api.Assumptions.assumeTrue(dbUp(), "MySQL locale requise");
        FakeResp r = render("/produit", "/45");
        assertEquals(301, r.status);
        assertEquals("https://mboastyle.cm/produit/45/monter-connecter", r.headers.get("Location"));
    }

    @Test
    void produitInexistantOuNonPublicRend404() throws Exception {
        org.junit.jupiter.api.Assumptions.assumeTrue(dbUp(), "MySQL locale requise");
        assertEquals(404, render("/produit", "/999999").status);
        assertEquals(404, render("/produit", "/nawak").status);
        assertEquals(404, render("/produit", null).status);
    }

    @Test
    void categorieRend200AvecItemList() throws Exception {
        org.junit.jupiter.api.Assumptions.assumeTrue(dbUp(), "MySQL locale requise");
        FakeResp r = render("/categorie", "/montre");
        assertEquals(200, r.status);
        assertTrue(r.body.toString().contains("\"@type\":\"CollectionPage\""));
        assertEquals(404, render("/categorie", "/n existe-pas").status);
    }

    @Test
    void boutiqueRend200SansDonneesPrivees() throws Exception {
        org.junit.jupiter.api.Assumptions.assumeTrue(dbUp(), "MySQL locale requise");
        FakeResp r = render("/boutique", "/9/rolexcameroun");
        assertEquals(200, r.status);
        String html = r.body.toString();
        assertTrue(html.contains("\"@type\":\"Store\""));
        assertFalse(html.contains("password_hash"));
        assertFalse(html.matches("(?s).*[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}.*")); // aucun email
        assertEquals(404, render("/boutique", "/999999").status);
    }
}
