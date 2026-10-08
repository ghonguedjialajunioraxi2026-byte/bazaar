package com.ecommerce.controller;

import com.ecommerce.model.Product;
import com.ecommerce.service.ProductService;
import com.ecommerce.dao.ProductDAO;
import com.ecommerce.dao.StockAlertDAO;
import com.ecommerce.util.JsonUtil;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

@WebServlet("/api/products")
public class ProductServlet extends HttpServlet {
    private final ProductService productService = new ProductService();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");

        try {
            String action = req.getParameter("action");

            if ("popular".equals(action)) {
                int limit = parseIntParam(req, "limit", 8);
                List<Product> products = productService.getPopular(limit);
                resp.getWriter().write(JsonUtil.toJson(products));
            } else if ("new".equals(action)) {
                int limit = parseIntParam(req, "limit", 4);
                List<Product> products = productService.getNew(limit);
                resp.getWriter().write(JsonUtil.toJson(products));
            } else if ("promo".equals(action)) {
                int limit = parseIntParam(req, "limit", 8);
                List<Product> products = productService.getPromo(limit);
                resp.getWriter().write(JsonUtil.toJson(products));
            } else if ("topRated".equals(action)) {
                int limit = parseIntParam(req, "limit", 8);
                List<Product> products = productService.getTopRated(limit);
                resp.getWriter().write(JsonUtil.toJson(products));
            } else if ("recommended".equals(action)) {
                int limit = parseIntParam(req, "limit", 8);
                List<Product> products = productService.getRecommended(limit);
                resp.getWriter().write(JsonUtil.toJson(products));
            } else if ("search".equals(action)) {
                String q = req.getParameter("q");
                if (q != null && !q.trim().isEmpty()) {
                    q = q.trim();
                    if (q.length() > 100) q = q.substring(0, 100);
                    List<Product> products = productService.search(q);
                    resp.getWriter().write(JsonUtil.toJson(products));
                } else {
                    resp.getWriter().write("[]");
                }
            } else if ("byCategory".equals(action)) {
                // Produits d'une catégorie + toutes ses sous-catégories
                int categoryId = parseIntParam(req, "categoryId", 0);
                if (categoryId <= 0) {
                    resp.getWriter().write("[]");
                } else {
                    java.util.List<Integer> ids =
                            new com.ecommerce.service.CategoryService().getSubtreeIds(categoryId);
                    List<Product> products = new ProductDAO().findPublicByCategoryIds(ids);
                    resp.getWriter().write(JsonUtil.toJson(products));
                }
            } else if ("filter".equals(action)) {
                String category = req.getParameter("category");
                String brand = req.getParameter("brand");
                String badge = req.getParameter("badge");
                Integer minPrice = parseIntParamOrNull(req, "minPrice");
                Integer maxPrice = parseIntParamOrNull(req, "maxPrice");
                Boolean inStock = "true".equals(req.getParameter("inStock"));
                String sort = req.getParameter("sort");
                // Bornes serveur (Phase 20) : page/limit hostiles => 500 (OFFSET négatif).
                int page = com.ecommerce.util.Pagination.clampPage(parseIntParam(req, "page", 1));
                int limit = com.ecommerce.util.Pagination.clampLimit(parseIntParam(req, "limit", 12), 50);

                List<Product> products = productService.filter(category, brand, badge, minPrice, maxPrice, inStock, sort, page, limit);
                int total = productService.countFiltered(category, brand, badge, minPrice, maxPrice, inStock);

                Map<String, Object> result = new HashMap<>();
                result.put("products", products);
                result.put("total", total);
                resp.getWriter().write(JsonUtil.toJson(result));
            } else {
                ProductDAO dao = new ProductDAO();
                String token = req.getHeader("Authorization");
                if (token != null && token.startsWith("Bearer ")) token = token.substring(7);
                boolean isAdmin = new com.ecommerce.dao.AdminSessionDAO().validateSession(token) > 0;
                List<Product> products = isAdmin ? dao.findAll() : dao.findAllPublic();
                resp.getWriter().write(JsonUtil.toJson(products));
            }
        } catch (Exception e) {
            resp.setStatus(500);
            com.ecommerce.util.ApiResponse.internalError(resp);
        }
    }

    private int parseIntParam(HttpServletRequest req, String param, int defaultVal) {
        try {
            String val = req.getParameter(param);
            if (val != null) return Integer.parseInt(val);
        } catch (NumberFormatException ignored) {}
        return defaultVal;
    }

    private Integer parseIntParamOrNull(HttpServletRequest req, String param) {
        try {
            String val = req.getParameter(param);
            if (val != null && !val.isEmpty()) return Integer.parseInt(val);
        } catch (NumberFormatException ignored) {}
        return null;
    }

    @Override
    protected void doPut(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");
        try {
            // Modification réservée à l'admin (lecture publique inchangée).
            if (com.ecommerce.util.AdminAuth.requireAdmin(req, resp) <= 0) return;
            BufferedReader reader = req.getReader();
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) sb.append(line);
            Map<String, Object> body = JsonUtil.fromJson(sb.toString(), Map.class);

            String action = (String) body.get("action");
            ProductDAO productDAO = new ProductDAO();

            if ("updateStock".equals(action)) {
                int id = ((Number) body.get("id")).intValue();
                int newStock = ((Number) body.get("stock")).intValue();
                StockAlertDAO alertDAO = new StockAlertDAO();
                Product p = productDAO.findById(id);
                int oldStock = p != null ? p.getStock() : 0;
                productDAO.updateStock(id, newStock);
                alertDAO.log(id, oldStock, newStock, "ADMIN_UPDATE");
            } else {
                Product p = new Product();
                p.setId(((Number) body.get("id")).intValue());
                p.setName((String) body.get("name"));
                p.setBrand((String) body.get("brand"));
                p.setReference((String) body.get("reference"));
                if (p.getReference() == null || p.getReference().isEmpty()) p.setReference(generateReference(p.getName(), p.getBrand()));
                p.setCategoryId(body.get("categoryId") != null ? ((Number) body.get("categoryId")).intValue() : 4);
                p.setPrice(body.get("price") != null ? ((Number) body.get("price")).intValue() : 0);
                p.setStock(body.get("stock") != null ? ((Number) body.get("stock")).intValue() : 0);
                p.setDescription((String) body.get("description"));
                p.setBadge((String) body.get("badge"));
                if (body.get("oldPrice") != null) p.setOldPrice(((Number) body.get("oldPrice")).intValue());
                if (body.get("discount") != null) p.setDiscount(((Number) body.get("discount")).intValue());
                if (body.get("supplierId") != null) p.setSupplierId(((Number) body.get("supplierId")).intValue());
                if (body.get("promoStart") != null) p.setPromoStart((String) body.get("promoStart"));
                if (body.get("promoEnd") != null) p.setPromoEnd((String) body.get("promoEnd"));
                productDAO.update(p);
            }
            resp.getWriter().write("{\"success\":true}");
        } catch (Exception e) {
            resp.setStatus(500);
            com.ecommerce.util.ApiResponse.internalError(resp);
        }
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");
        try {
            if (com.ecommerce.util.AdminAuth.requireAdmin(req, resp) <= 0) return;
            BufferedReader reader = req.getReader();
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) sb.append(line);
            Map<String, Object> body = JsonUtil.fromJson(sb.toString(), Map.class);

            Product p = new Product();
            p.setName((String) body.get("name"));
            p.setBrand((String) body.get("brand"));
            p.setReference((String) body.get("reference"));
            if (p.getReference() == null || p.getReference().isEmpty()) p.setReference(generateReference(p.getName(), p.getBrand()));
            p.setCategoryId(body.get("categoryId") != null ? ((Number) body.get("categoryId")).intValue() : 4);
            p.setPrice(body.get("price") != null ? ((Number) body.get("price")).intValue() : 0);
            p.setStock(body.get("stock") != null ? ((Number) body.get("stock")).intValue() : 0);
            p.setDescription((String) body.get("description"));
            p.setBadge((String) body.get("badge"));
            if (body.get("oldPrice") != null) p.setOldPrice(((Number) body.get("oldPrice")).intValue());
            if (body.get("discount") != null) p.setDiscount(((Number) body.get("discount")).intValue());
            if (body.get("supplierId") != null) p.setSupplierId(((Number) body.get("supplierId")).intValue());
            if (body.get("promoStart") != null) p.setPromoStart((String) body.get("promoStart"));
            if (body.get("promoEnd") != null) p.setPromoEnd((String) body.get("promoEnd"));

            ProductDAO productDAO = new ProductDAO();
            int id = productDAO.create(p);
            resp.getWriter().write("{\"success\":true,\"id\":" + id + "}");
        } catch (Exception e) {
            resp.setStatus(500);
            com.ecommerce.util.ApiResponse.internalError(resp);
        }
    }

    @Override
    protected void doDelete(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");
        try {
            if (com.ecommerce.util.AdminRole.requireAction(req, resp,
                    com.ecommerce.util.AdminRole.DESTRUCTIVE) <= 0) return;
            int id = parseIntParam(req, "id", 0);
            if (id <= 0) {
                resp.setStatus(400);
                resp.getWriter().write("{\"error\":\"Identifiant produit requis\"}");
                return;
            }
            ProductDAO productDAO = new ProductDAO();
            productDAO.delete(id);
            resp.getWriter().write("{\"success\":true}");
        } catch (Exception e) {
            com.ecommerce.util.ServiceErrors.send(resp, e,
                "Ce produit ne peut pas être supprimé",
                "Produit introuvable");
        }
    }

    private String generateReference(String name, String brand) {
        String base = "";
        if (brand != null) {
            for (String part : brand.toUpperCase().replaceAll("[^A-Z0-9 ]", " ").split("\\s+")) {
                if (!part.isEmpty()) base += part.charAt(0);
            }
        }
        if (base.isEmpty()) base = "PRD";
        if (name != null) {
            String n = name.toUpperCase().replaceAll("[^A-Z0-9 ]", " ").trim();
            if (!n.isEmpty()) {
                String first = n.split("\\s+")[0];
                base += "-" + (first.length() > 8 ? first.substring(0, 8) : first);
            }
        }
        return base + "-" + String.format("%04d", new Random().nextInt(10000));
    }
}
