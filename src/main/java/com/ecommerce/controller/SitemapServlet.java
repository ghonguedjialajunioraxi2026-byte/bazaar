package com.ecommerce.controller;

import com.ecommerce.dao.CategoryDAO;
import com.ecommerce.dao.ProductDAO;
import com.ecommerce.dao.SupplierDAO;
import com.ecommerce.model.Category;
import com.ecommerce.model.Product;
import com.ecommerce.model.Supplier;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.List;

/**
 * Sitemap XML dynamique (Phase 18).
 * Uniquement des URLs publiques RÉELLES et résolvables (pages SeoServlet) :
 * accueil, catégories actives, produits publics, boutiques actives.
 * Exclut : hash-routes SPA (non crawlables), recherche, panier, comptes,
 * messagerie, dashboards, produits non publics.
 * Volume actuel faible : un seul fichier (pas de sitemap index).
 */
@WebServlet("/sitemap.xml")
public class SitemapServlet extends HttpServlet {

    private final ProductDAO productDAO = new ProductDAO();
    private final CategoryDAO categoryDAO = new CategoryDAO();
    private final SupplierDAO supplierDAO = new SupplierDAO();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        resp.setContentType("application/xml");
        resp.setCharacterEncoding("UTF-8");
        try {
            String base = SeoServlet.baseUrl(req);
            StringBuilder sb = new StringBuilder();
            sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
            sb.append("<urlset xmlns=\"http://www.sitemaps.org/schemas/sitemap/0.9\">\n");

            addUrl(sb, base + "/", "1.0", "daily");

            try {
                for (Category c : categoryDAO.findActive()) {
                    if (c.getSlug() != null && !c.getSlug().isEmpty()) {
                        addUrl(sb, base + "/categorie/" + c.getSlug(), "0.8", "weekly");
                    }
                }
            } catch (Exception ignored) {}

            try {
                for (Product p : productDAO.findAllPublic()) {
                    if (!SeoServlet.isPublicProduct(p.getStatus())) continue;
                    String url = base + "/produit/" + p.getId()
                        + (p.getSlug() != null && !p.getSlug().isEmpty() ? "/" + p.getSlug() : "");
                    addUrl(sb, url, "0.9", "weekly");
                    if (sb.length() > 8 * 1024 * 1024) break; // garde-fou ~8 Mo
                }
            } catch (Exception ignored) {}

            try {
                List<Supplier> suppliers = supplierDAO.findActive();
                for (Supplier s : suppliers) {
                    addUrl(sb, base + "/boutique/" + s.getId() + "/"
                        + SeoServlet.slugify(s.getName()), "0.7", "weekly");
                }
            } catch (Exception e) {
                // findActive() indisponible : repli silencieux (sitemap partiel OK).
            }

            sb.append("</urlset>");
            resp.getWriter().write(sb.toString());
        } catch (Exception e) {
            resp.setStatus(500);
            resp.getWriter().write("<?xml version=\"1.0\" encoding=\"UTF-8\"?><error>Erreur interne</error>");
        }
    }

    private void addUrl(StringBuilder sb, String loc, String priority, String changeFreq) {
        sb.append("  <url>\n");
        sb.append("    <loc>").append(escapeXml(loc)).append("</loc>\n");
        sb.append("    <changefreq>").append(changeFreq).append("</changefreq>\n");
        sb.append("    <priority>").append(priority).append("</priority>\n");
        sb.append("  </url>\n");
    }

    private static String escapeXml(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
