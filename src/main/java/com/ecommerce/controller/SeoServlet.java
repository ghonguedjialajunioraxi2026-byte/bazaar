package com.ecommerce.controller;

import com.ecommerce.dao.CategoryDAO;
import com.ecommerce.dao.ProductDAO;
import com.ecommerce.dao.ReviewDAO;
import com.ecommerce.dao.SupplierDAO;
import com.ecommerce.model.Category;
import com.ecommerce.model.Product;
import com.ecommerce.model.Review;
import com.ecommerce.model.Supplier;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Pages SEO server-rendered (Phase 18).
 *
 * La boutique est une SPA (routes `#/...` invisibles aux crawlers) :
 * ce servlet expose le même contenu RÉEL en HTML sémantique sur des URLs
 * propres et canoniques. Aucune donnée fictive : tout vient de MySQL
 * (produits/catégories/boutiques publics uniquement).
 *
 * URLs :
 *   /produit/<id>[-<slug>]     (301 vers le canonique si slug absent/erroné)
 *   /categorie/<slug>
 *   /boutique/<id>[-<slug>]
 * Produit supprimé ou non public → 404 réelle (jamais de redirection accueil).
 * Rupture de stock → page 200 conservée, Offer OutOfStock (jamais de fausse dispo).
 */
@WebServlet(urlPatterns = {"/produit/*", "/categorie/*", "/boutique/*"})
public class SeoServlet extends HttpServlet {

    private final ProductDAO productDAO = new ProductDAO();
    private final CategoryDAO categoryDAO = new CategoryDAO();
    private final SupplierDAO supplierDAO = new SupplierDAO();
    private final ReviewDAO reviewDAO = new ReviewDAO();

    /** Statuts visibles publiquement (même règle que la boutique). */
    static boolean isPublicProduct(String status) {
        return "ACTIVE".equals(status) || "APPROVED".equals(status);
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        resp.setContentType("text/html");
        resp.setCharacterEncoding("UTF-8");
        try {
            String servletPath = req.getServletPath(); // /produit | /categorie | /boutique
            String pathInfo = req.getPathInfo();       // /123-slug | /slug | /12-slug
            String base = baseUrl(req);
            if ("/produit".equals(servletPath)) {
                renderProduct(req, resp, base, pathInfo);
            } else if ("/categorie".equals(servletPath)) {
                renderCategory(req, resp, base, pathInfo);
            } else if ("/boutique".equals(servletPath)) {
                renderShop(req, resp, base, pathInfo);
            } else {
                resp.sendError(HttpServletResponse.SC_NOT_FOUND);
            }
        } catch (Exception e) {
            resp.sendError(HttpServletResponse.SC_NOT_FOUND);
        }
    }

    // ---------------- PRODUIT ----------------

    private void renderProduct(HttpServletRequest req, HttpServletResponse resp, String base, String pathInfo)
            throws Exception {
        int id = leadingId(pathInfo);
        if (id <= 0) {
            resp.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }
        Product p = productDAO.findById(id);
        if (p == null || !isPublicProduct(p.getStatus())) {
            resp.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }
        String canonical = base + "/produit/" + p.getId()
            + (p.getSlug() != null && !p.getSlug().isEmpty() ? "/" + p.getSlug() : "");
        String want = base + (req.getRequestURI() == null ? "" : req.getRequestURI());
        if (!want.equals(canonical)) {
            resp.setStatus(HttpServletResponse.SC_MOVED_PERMANENTLY);
            resp.setHeader("Location", canonical);
            return;
        }

        Category cat = null;
        try {
            if (p.getCategoryId() > 0) cat = categoryDAO.findById(p.getCategoryId());
        } catch (Exception ignored) {}
        Supplier sup = null;
        try {
            if (p.getSupplierId() > 0) {
                Supplier s = supplierDAO.findById(p.getSupplierId());
                if (s != null && s.isActive()) sup = s;
            }
        } catch (Exception ignored) {}
        List<Review> reviews = new ArrayList<>();
        try {
            reviews = reviewDAO.findByProduct(p.getId()); // approuvés seuls
        } catch (Exception ignored) {}

        String title = p.getName() + " — MBOA STYLE";
        String desc = metaDescription(productSummary(p));
        String img = firstImage(base, p.getImages());
        boolean inStock = p.getStock() > 0;

        StringBuilder json = new StringBuilder();
        json.append("{\"@context\":\"https://schema.org\",\"@type\":\"Product\",");
        json.append("\"name\":").append(jsonStr(p.getName())).append(",");
        if (img != null) json.append("\"image\":[").append(jsonStr(img)).append("],");
        json.append("\"description\":").append(jsonStr(textOr(p.getDescription(), p.getName()))).append(",");
        if (p.getBrand() != null && !p.getBrand().isEmpty()) {
            json.append("\"brand\":{\"@type\":\"Brand\",\"name\":").append(jsonStr(p.getBrand())).append("},");
        }
        if (p.getReference() != null && !p.getReference().isEmpty()) {
            json.append("\"sku\":").append(jsonStr(p.getReference())).append(",");
        }
        json.append("\"offers\":{\"@type\":\"Offer\",\"url\":").append(jsonStr(canonical)).append(",");
        json.append("\"priceCurrency\":\"XAF\",\"price\":").append(p.getPrice()).append(",");
        json.append("\"availability\":\"https://schema.org/")
            .append(inStock ? "InStock" : "OutOfStock").append("\"}");
        if (!reviews.isEmpty()) {
            double avg = 0;
            for (Review r : reviews) avg += r.getRating();
            avg = Math.round((avg / reviews.size()) * 10.0) / 10.0;
            json.append(",\"aggregateRating\":{\"@type\":\"AggregateRating\",\"ratingValue\":")
                .append(avg).append(",\"reviewCount\":").append(reviews.size()).append("}");
            json.append(",\"review\":[");
            for (int i = 0; i < reviews.size() && i < 10; i++) {
                Review r = reviews.get(i);
                if (i > 0) json.append(",");
                json.append("{\"@type\":\"Review\",\"author\":{\"@type\":\"Person\",\"name\":")
                    .append(jsonStr(r.getFullname())).append("},\"reviewRating\":{\"@type\":\"Rating\",\"ratingValue\":")
                    .append(r.getRating()).append("},\"reviewBody\":").append(jsonStr(r.getComment() == null ? "" : r.getComment())).append("}");
            }
            json.append("]");
        }
        json.append(",\"breadcrumb\":{\"@type\":\"BreadcrumbList\",\"itemListElement\":[");
        json.append(breadcrumbJson(base, cat, p.getName(), canonical));
        json.append("]}}");

        StringBuilder body = new StringBuilder();
        body.append("<nav aria-label=\"Fil d'Ariane\"><a href=\"").append(base).append("/\">Accueil</a>");
        if (cat != null) {
            body.append(" › <a href=\"").append(base).append("/categorie/").append(escAttr(cat.getSlug()))
                .append("\">").append(esc(cat.getName())).append("</a>");
        }
        body.append(" › <span>").append(esc(p.getName())).append("</span></nav>");
        body.append("<h1>").append(esc(p.getName())).append("</h1>");
        if (img != null) {
            body.append("<img src=\"").append(escAttr(img)).append("\" alt=\"").append(escAttr(p.getName())).append("\">");
        }
        body.append("<p>Prix : <strong>").append(formatPrice(p.getPrice())).append(" FCFA</strong>");
        if (p.getOldPrice() != null && p.getOldPrice() > p.getPrice()) {
            body.append(" <s>").append(formatPrice(p.getOldPrice())).append(" FCFA</s>");
        }
        body.append("</p>");
        body.append("<p>Disponibilité : ").append(inStock
            ? "En stock" : "Rupture de stock temporaire").append("</p>");
        if (p.getBrand() != null && !p.getBrand().isEmpty()) {
            body.append("<p>Marque : ").append(esc(p.getBrand())).append("</p>");
        }
        if (p.getDescription() != null && !p.getDescription().trim().isEmpty()) {
            body.append("<h2>Description</h2><p>").append(esc(p.getDescription())).append("</p>");
        }
        if (sup != null) {
            body.append("<h2>Vendeur</h2><p><a href=\"").append(base).append("/boutique/")
                .append(sup.getId()).append("/").append(escAttr(slugify(sup.getName())))
                .append("\">").append(esc(sup.getName())).append("</a>");
            if (sup.getCity() != null && !sup.getCity().isEmpty()) {
                body.append(" — ").append(esc(sup.getCity()));
            }
            body.append("</p>");
        }
        if (!reviews.isEmpty()) {
            body.append("<h2>Avis clients (").append(String.valueOf(reviews.size())).append(")</h2><ul>");
            for (Review r : reviews) {
                body.append("<li><strong>").append(esc(r.getFullname())).append("</strong> — ")
                    .append(r.getRating()).append("/5");
                if (r.getComment() != null && !r.getComment().isEmpty()) {
                    body.append(" : ").append(esc(r.getComment()));
                }
                body.append("</li>");
            }
            body.append("</ul>");
        }
        // Similaires : même catégorie, publics, hors soi-même (liens internes réels).
        try {
            List<Product> similars = productDAO.findPublicByCategoryIds(
                cat != null ? java.util.Collections.singletonList(cat.getId())
                    : java.util.Collections.singletonList(p.getCategoryId()));
            StringBuilder sim = new StringBuilder();
            int n = 0;
            for (Product s : similars) {
                if (s.getId() == p.getId() || !isPublicProduct(s.getStatus())) continue;
                if (n++ >= 8) break;
                sim.append("<li><a href=\"").append(base).append("/produit/").append(s.getId());
                if (s.getSlug() != null && !s.getSlug().isEmpty()) sim.append("/").append(escAttr(s.getSlug()));
                sim.append("\">").append(esc(s.getName())).append(" — ")
                    .append(formatPrice(s.getPrice())).append(" FCFA</a></li>");
            }
            if (n > 0) body.append("<h2>Produits similaires</h2><ul>").append(sim).append("</ul>");
        } catch (Exception ignored) {}
        body.append("<p><a href=\"").append(base).append("/#/produit/").append(String.valueOf(p.getId()))
            .append("\">Voir et commander sur MBOA STYLE</a></p>");

        writePage(resp, title, desc, canonical, img, "product", json.toString(), body.toString());
    }

    // ---------------- CATÉGORIE ----------------

    private void renderCategory(HttpServletRequest req, HttpServletResponse resp, String base, String pathInfo)
            throws Exception {
        String slug = slugOf(pathInfo);
        if (slug == null) {
            resp.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }
        Category cat = categoryDAO.findBySlug(slug);
        if (cat == null || !cat.isActive()) {
            resp.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }
        String canonical = base + "/categorie/" + cat.getSlug();
        List<Category> children = new ArrayList<>();
        try {
            children = categoryDAO.findChildren(cat.getId());
        } catch (Exception ignored) {}
        List<Integer> subtree = new ArrayList<>();
        try {
            subtree = new com.ecommerce.service.CategoryService().getSubtreeIds(cat.getId());
        } catch (Exception ignored) {
            subtree.add(cat.getId());
        }
        List<Product> products = new ArrayList<>();
        try {
            for (Product p : productDAO.findPublicByCategoryIds(subtree)) {
                if (isPublicProduct(p.getStatus())) products.add(p);
                if (products.size() >= 100) break;
            }
        } catch (Exception ignored) {}

        String title = cat.getName() + " — MBOA STYLE";
        String desc = metaDescription(cat.getDescription() != null && !cat.getDescription().trim().isEmpty()
            ? cat.getDescription()
            : "Produits " + cat.getName() + " sur MBOA STYLE : vendeurs vérifiés, paiement et livraison au Cameroun.");
        String img = firstImage(base, null);

        StringBuilder json = new StringBuilder();
        json.append("{\"@context\":\"https://schema.org\",\"@type\":\"CollectionPage\",");
        json.append("\"name\":").append(jsonStr(cat.getName())).append(",");
        json.append("\"url\":").append(jsonStr(canonical));
        if (!products.isEmpty()) {
            json.append(",\"mainEntity\":{\"@type\":\"ItemList\",\"numberOfItems\":").append(products.size())
                .append(",\"itemListElement\":[");
            for (int i = 0; i < products.size() && i < 50; i++) {
                Product p = products.get(i);
                if (i > 0) json.append(",");
                json.append("{\"@type\":\"ListItem\",\"position\":").append(i + 1)
                    .append(",\"url\":").append(jsonStr(base + "/produit/" + p.getId()
                        + (p.getSlug() != null && !p.getSlug().isEmpty() ? "/" + p.getSlug() : "")))
                    .append(",\"name\":").append(jsonStr(p.getName())).append("}");
            }
            json.append("]}");
        }
        json.append(",\"breadcrumb\":{\"@type\":\"BreadcrumbList\",\"itemListElement\":[");
        json.append(breadcrumbJson(base, null, cat.getName(), canonical));
        json.append("]}}");

        StringBuilder body = new StringBuilder();
        body.append("<nav aria-label=\"Fil d'Ariane\"><a href=\"").append(base).append("/\">Accueil</a> › <span>")
            .append(esc(cat.getName())).append("</span></nav>");
        body.append("<h1>").append(esc(cat.getName())).append("</h1>");
        if (cat.getDescription() != null && !cat.getDescription().trim().isEmpty()) {
            body.append("<p>").append(esc(cat.getDescription())).append("</p>");
        }
        StringBuilder visibleKids = new StringBuilder();
        for (Category k : children) {
            if (k.isActive()) {
                visibleKids.append("<li><a href=\"").append(base).append("/categorie/")
                    .append(escAttr(k.getSlug())).append("\">").append(esc(k.getName())).append("</a></li>");
            }
        }
        if (visibleKids.length() > 0) {
            body.append("<h2>Sous-catégories</h2><ul>").append(visibleKids).append("</ul>");
        }
        if (!products.isEmpty()) {
            body.append("<h2>Produits (").append(String.valueOf(products.size())).append(")</h2><ul>");
            for (Product p : products) {
                body.append("<li><a href=\"").append(base).append("/produit/").append(p.getId());
                if (p.getSlug() != null && !p.getSlug().isEmpty()) body.append("/").append(escAttr(p.getSlug()));
                body.append("\">").append(esc(p.getName())).append(" — ")
                    .append(formatPrice(p.getPrice())).append(" FCFA</a></li>");
            }
            body.append("</ul>");
        } else {
            body.append("<p>Aucun produit disponible pour le moment dans cette catégorie.</p>");
        }

        writePage(resp, title, desc, canonical, img, "website", json.toString(), body.toString());
    }

    // ---------------- BOUTIQUE ----------------

    private void renderShop(HttpServletRequest req, HttpServletResponse resp, String base, String pathInfo)
            throws Exception {
        int id = leadingId(pathInfo);
        if (id <= 0) {
            resp.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }
        Supplier s = supplierDAO.findById(id);
        if (s == null || !s.isActive()) {
            resp.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }
        String canonical = base + "/boutique/" + s.getId() + "/" + slugify(s.getName());
        String want = base + (req.getRequestURI() == null ? "" : req.getRequestURI());
        if (!want.equals(canonical)) {
            resp.setStatus(HttpServletResponse.SC_MOVED_PERMANENTLY);
            resp.setHeader("Location", canonical);
            return;
        }
        List<Product> products = new ArrayList<>();
        try {
            for (Product p : productDAO.findBySupplier(s.getId())) {
                if (isPublicProduct(p.getStatus())) products.add(p);
                if (products.size() >= 100) break;
            }
        } catch (Exception ignored) {}

        String title = s.getName() + " — Boutique MBOA STYLE";
        String desc = metaDescription(s.getDescription() != null && !s.getDescription().trim().isEmpty()
            ? s.getDescription()
            : "Boutique " + s.getName() + " sur MBOA STYLE"
                + (s.getCity() != null && !s.getCity().isEmpty() ? " (" + s.getCity() + ")" : "")
                + " : produits et commande en ligne.");
        String img = firstImage(base, s.getLogo() == null ? null
            : java.util.Collections.singletonList(s.getLogo()));

        StringBuilder json = new StringBuilder();
        json.append("{\"@context\":\"https://schema.org\",\"@type\":\"Store\",");
        json.append("\"name\":").append(jsonStr(s.getName())).append(",");
        json.append("\"url\":").append(jsonStr(canonical));
        if (s.getCity() != null && !s.getCity().isEmpty()) {
            json.append(",\"address\":{\"@type\":\"PostalAddress\",\"addressLocality\":")
                .append(jsonStr(s.getCity())).append(",\"addressCountry\":\"CM\"}");
        }
        json.append("}");

        StringBuilder body = new StringBuilder();
        body.append("<nav aria-label=\"Fil d'Ariane\"><a href=\"").append(base).append("/\">Accueil</a> › <span>")
            .append(esc(s.getName())).append("</span></nav>");
        body.append("<h1>").append(esc(s.getName())).append("</h1>");
        if (s.getCity() != null && !s.getCity().isEmpty()) {
            body.append("<p>").append(esc(s.getCity())).append(", Cameroun</p>");
        }
        if (s.getDescription() != null && !s.getDescription().trim().isEmpty()) {
            body.append("<p>").append(esc(s.getDescription())).append("</p>");
        }
        if (!products.isEmpty()) {
            body.append("<h2>Produits de la boutique (").append(String.valueOf(products.size())).append(")</h2><ul>");
            for (Product p : products) {
                body.append("<li><a href=\"").append(base).append("/produit/").append(p.getId());
                if (p.getSlug() != null && !p.getSlug().isEmpty()) body.append("/").append(escAttr(p.getSlug()));
                body.append("\">").append(esc(p.getName())).append(" — ")
                    .append(formatPrice(p.getPrice())).append(" FCFA</a></li>");
            }
            body.append("</ul>");
        } else {
            body.append("<p>Aucun produit disponible pour le moment dans cette boutique.</p>");
        }
        body.append("<p><a href=\"").append(base).append("/#/boutique/").append(String.valueOf(s.getId()))
            .append("\">Visiter la boutique sur MBOA STYLE</a></p>");

        writePage(resp, title, desc, canonical, img, "website", json.toString(), body.toString());
    }

    // ---------------- RENDU ----------------

    private void writePage(HttpServletResponse resp, String title, String desc, String canonical,
                           String img, String ogType, String jsonLd, String body) throws IOException {
        StringBuilder h = new StringBuilder();
        h.append("<!DOCTYPE html><html lang=\"fr\"><head><meta charset=\"UTF-8\">");
        h.append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">");
        h.append("<title>").append(esc(title)).append("</title>");
        h.append("<meta name=\"description\" content=\"").append(escAttr(desc)).append("\">");
        h.append("<link rel=\"canonical\" href=\"").append(escAttr(canonical)).append("\">");
        h.append("<meta property=\"og:title\" content=\"").append(escAttr(title)).append("\">");
        h.append("<meta property=\"og:description\" content=\"").append(escAttr(desc)).append("\">");
        h.append("<meta property=\"og:url\" content=\"").append(escAttr(canonical)).append("\">");
        h.append("<meta property=\"og:type\" content=\"").append(ogType).append("\">");
        h.append("<meta property=\"og:locale\" content=\"fr_CM\">");
        if (img != null) h.append("<meta property=\"og:image\" content=\"").append(escAttr(img)).append("\">");
        h.append("<meta name=\"twitter:card\" content=\"summary_large_image\">");
        h.append("<meta name=\"twitter:title\" content=\"").append(escAttr(title)).append("\">");
        h.append("<meta name=\"twitter:description\" content=\"").append(escAttr(desc)).append("\">");
        h.append("<script type=\"application/ld+json\">").append(jsonLd).append("</script>");
        h.append("</head><body><main>").append(body).append("</main>");
        h.append("<footer><p><a href=\"/\">MBOA STYLE — Marketplace au Cameroun</a></p></footer>");
        h.append("</body></html>");
        resp.getWriter().write(h.toString());
    }

    // ---------------- HELPERS (testables) ----------------

    static String baseUrl(HttpServletRequest req) {
        String base = req.getScheme() + "://" + req.getServerName();
        int port = req.getServerPort();
        if (port != 80 && port != 443) base += ":" + port;
        String ctx = req.getContextPath();
        if (ctx != null && !ctx.isEmpty()) base += ctx;
        return base;
    }

    static int leadingId(String pathInfo) {
        if (pathInfo == null) return 0;
        String s = pathInfo.startsWith("/") ? pathInfo.substring(1) : pathInfo;
        int end = 0;
        while (end < s.length() && Character.isDigit(s.charAt(end))) end++;
        if (end == 0) return 0;
        try {
            return Integer.parseInt(s.substring(0, end));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    static String slugOf(String pathInfo) {
        if (pathInfo == null) return null;
        String s = pathInfo.startsWith("/") ? pathInfo.substring(1) : pathInfo;
        int q = s.indexOf('?');
        if (q >= 0) s = s.substring(0, q);
        if (s.isEmpty() || s.contains("/")) return null;
        return s;
    }

    static String slugify(String name) {
        if (name == null) return "boutique";
        String s = name.trim().toLowerCase()
            .replaceAll("[^a-z0-9\\s-]", "")
            .replaceAll("[\\s_]+", "-")
            .replaceAll("-+", "-");
        if (s.isEmpty()) return "boutique";
        return s.length() > 80 ? s.substring(0, 80) : s;
    }

    static String metaDescription(String text) {
        if (text == null) return "MBOA STYLE — Marketplace au Cameroun.";
        String t = text.trim().replaceAll("\\s+", " ");
        if (t.length() <= 155) return t;
        return t.substring(0, 152) + "...";
    }

    static String productSummary(Product p) {
        StringBuilder s = new StringBuilder(p.getName());
        s.append(" — ").append(formatPrice(p.getPrice())).append(" FCFA");
        if (p.getBrand() != null && !p.getBrand().isEmpty()) s.append(" (").append(p.getBrand()).append(")");
        if (p.getStock() > 0) s.append(", en stock");
        s.append(" — MBOA STYLE");
        return s.toString();
    }

    static String firstImage(String base, List<String> images) {
        if (images != null) {
            for (String u : images) {
                if (u == null || u.trim().isEmpty()) continue;
                String t = u.trim();
                if (t.startsWith("http://") || t.startsWith("https://")) return t;
                if (t.startsWith("/")) return base + t;
                return base + "/" + t;
            }
        }
        return null;
    }

    static String formatPrice(int price) {
        return String.format("%,d", price).replace(',', ' ');
    }

    static String breadcrumbJson(String base, Category cat, String leafName, String leafUrl) {
        StringBuilder b = new StringBuilder();
        b.append("{\"@type\":\"ListItem\",\"position\":1,\"name\":\"Accueil\",\"item\":")
            .append(jsonStr(base + "/")).append("}");
        int pos = 2;
        if (cat != null) {
            b.append(",{\"@type\":\"ListItem\",\"position\":").append(pos++)
                .append(",\"name\":").append(jsonStr(cat.getName()))
                .append(",\"item\":").append(jsonStr(base + "/categorie/" + cat.getSlug())).append("}");
        }
        b.append(",{\"@type\":\"ListItem\",\"position\":").append(pos)
            .append(",\"name\":").append(jsonStr(leafName))
            .append(",\"item\":").append(jsonStr(leafUrl)).append("}");
        return b.toString();
    }

    static String textOr(String v, String fallback) {
        return v != null && !v.trim().isEmpty() ? v.trim() : fallback;
    }

    /** Échappement HTML texte (XSS : noms/descriptions fournisseurs). */
    static String esc(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    static String escAttr(String s) {
        if (s == null) return "";
        return esc(s).replace("\"", "&quot;");
    }

    static String jsonStr(String s) {
        if (s == null) return "\"\"";
        return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"")
            .replace("\n", " ").replace("\r", " ").replace("\t", " ") + "\"";
    }
}
