package com.ecommerce.controller;

import com.ecommerce.model.Category;
import com.ecommerce.service.CategoryService;
import com.ecommerce.util.JsonUtil;
import com.ecommerce.util.UploadUtil;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.MultipartConfig;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.Part;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * API Catégories hiérarchiques.
 *
 * Endpoints REST :
 *   GET    /api/categories                 liste (rétrocompatible, ?action=list = sans compteurs)
 *   GET    /api/categories?search=&parent=&active=&status=&page=&limit=
 *   GET    /api/categories/tree            arborescence complète (?activeOnly=true)
 *   GET    /api/categories/roots           racines uniquement
 *   GET    /api/categories/12              détail
 *   GET    /api/categories/12/children     enfants directs
 *   GET    /api/categories/12/breadcrumb   fil d'Ariane
 *   POST   /api/categories                 création JSON (+ upload multipart ?file=)
 *   PUT    /api/categories                 modification JSON {id,...}
 *   PUT    /api/categories/12              modification JSON (id dans l'URL)
 *   PATCH  /api/categories/12/status       {active:true/false}
 *   DELETE /api/categories?id=12           suppression (rétrocompatible)
 *   DELETE /api/categories/12              suppression
 */
@WebServlet("/api/categories/*")
@MultipartConfig(
    fileSizeThreshold = 1024 * 1024,
    maxFileSize = 10 * 1024 * 1024,
    maxRequestSize = 20 * 1024 * 1024
)
public class CategoryServlet extends HttpServlet {
    private final CategoryService categoryService = new CategoryService();

    private String getUploadDir(HttpServletRequest req) {
        // Dossier persistant (survit aux redeploys) — voir UploadUtil.
        return UploadUtil.subDir(UploadUtil.SUBDIR_CATEGORIES).getAbsolutePath();
    }

    private String saveFile(Part part, String uploadDir) throws IOException {
        // uploadDir ignoré : UploadUtil.saveImage écrit dans le dossier persistant
        // et retourne l'URL stable "uploads/categories/xxx".
        // req non disponible ici : on passe null (pas de copie legacy).
        return UploadUtil.saveImage(part, UploadUtil.SUBDIR_CATEGORIES, null);
    }

    private String readBody(HttpServletRequest req) throws IOException {
        BufferedReader reader = req.getReader();
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) sb.append(line);
        return sb.toString();
    }

    private String[] pathParts(HttpServletRequest req) {
        String pathInfo = req.getPathInfo();
        if (pathInfo == null || pathInfo.equals("/")) return new String[0];
        String[] raw = pathInfo.split("/");
        java.util.List<String> parts = new java.util.ArrayList<>();
        for (String s : raw) if (s != null && !s.isEmpty()) parts.add(s);
        return parts.toArray(new String[0]);
    }

    // ==================== GET ====================

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");
        try {
            String[] parts = pathParts(req);

            if (parts.length == 1 && "tree".equals(parts[0])) {
                boolean activeOnly = "true".equals(req.getParameter("activeOnly"));
                resp.getWriter().write(JsonUtil.toJson(categoryService.getTree(activeOnly)));
                return;
            }
            if (parts.length == 1 && "roots".equals(parts[0])) {
                boolean activeOnly = "true".equals(req.getParameter("activeOnly"));
                resp.getWriter().write(JsonUtil.toJson(
                        activeOnly ? categoryService.getActive().stream().filter(Category::isRoot).toList()
                                   : categoryService.getRoots()));
                return;
            }
            if (parts.length == 2 && "children".equals(parts[1])) {
                int id = Integer.parseInt(parts[0]);
                resp.getWriter().write(JsonUtil.toJson(categoryService.getChildren(id)));
                return;
            }
            if (parts.length == 2 && "breadcrumb".equals(parts[1])) {
                int id = Integer.parseInt(parts[0]);
                resp.getWriter().write(JsonUtil.toJson(categoryService.getBreadcrumb(id)));
                return;
            }
            if (parts.length == 1 && parts[0].matches("\\d+")) {
                Category c = categoryService.getCategoryById(Integer.parseInt(parts[0]));
                if (c == null) {
                    resp.setStatus(404);
                    resp.getWriter().write("{\"error\":\"Catégorie introuvable\"}");
                } else {
                    resp.getWriter().write(JsonUtil.toJson(c));
                }
                return;
            }

            // Liste : filtres + pagination + recherche
            String action = req.getParameter("action");
            String search = req.getParameter("search");
            if (search == null) search = req.getParameter("q");
            String status = req.getParameter("status");
            Boolean active = null;
            if ("active".equalsIgnoreCase(status)) active = true;
            else if ("inactive".equalsIgnoreCase(status)) active = false;
            if (req.getParameter("active") != null) {
                active = "true".equals(req.getParameter("active")) || "1".equals(req.getParameter("active"));
            }
            Integer parent = parseIntOrNull(req.getParameter("parent"));
            if (req.getParameter("rootsOnly") != null
                    && ("true".equals(req.getParameter("rootsOnly")) || "1".equals(req.getParameter("rootsOnly")))) {
                parent = -1;
            }

            // Rétrocompatibilité : tableau simple par défaut
            boolean paginate = req.getParameter("page") != null || req.getParameter("limit") != null
                    || search != null || parent != null || active != null;

            if (!paginate) {
                List<Category> categories;
                if ("list".equals(action)) {
                    categories = categoryService.getAll();
                } else {
                    categories = categoryService.getAllWithCount();
                }
                resp.getWriter().write(JsonUtil.toJson(categories));
                return;
            }

            int page = parseInt(req.getParameter("page"), 1);
            int limit = parseInt(req.getParameter("limit"), 20);
            List<Category> data = categoryService.search(search, parent, active, page, limit);
            int total = categoryService.countSearch(search, parent, active);
            Map<String, Object> result = new HashMap<>();
            result.put("data", data);
            result.put("total", total);
            result.put("page", page);
            result.put("limit", limit);
            resp.getWriter().write(JsonUtil.toJson(result));
        } catch (Exception e) {
            resp.setStatus(500);
            com.ecommerce.util.ApiResponse.internalError(resp);
        }
    }

    // ==================== POST ====================

    @Override
    @SuppressWarnings("unchecked")
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");
        try {
            // Création / upload réservés à l'admin (lecture publique inchangée).
            if (com.ecommerce.util.AdminAuth.requireAdmin(req, resp) <= 0) return;
            String contentType = req.getContentType();
            if (contentType != null && contentType.contains("multipart/form-data")) {
                Part filePart = req.getPart("file");
                if (filePart == null || filePart.getSize() == 0) {
                    resp.setStatus(400);
                    resp.getWriter().write("{\"error\":\"Aucun fichier fourni\"}");
                    return;
                }
                String uploadDir = getUploadDir(req);
                String url = saveFile(filePart, uploadDir);
                resp.getWriter().write("{\"success\":true,\"url\":\"" + url + "\"}");
                return;
            }

            Map<String, Object> body = JsonUtil.fromJson(readBody(req), Map.class);

            // POST ?action=status — bascule / définit le statut (alternative à PATCH)
            String[] parts = pathParts(req);
            if ("status".equals(req.getParameter("action"))
                    || (parts.length == 2 && "status".equals(parts[1]))) {
                int id = parts.length == 2 ? Integer.parseInt(parts[0]) : intValue(body.get("id"), 0);
                Boolean active = boolValue(body.get("active"));
                if (active == null) {
                    categoryService.toggleActive(id);
                } else {
                    categoryService.setActive(id, active);
                }
                resp.getWriter().write("{\"success\":true}");
                return;
            }

            Category cat = categoryFromBody(body, 0);
            int id = categoryService.createCategory(cat);
            resp.getWriter().write("{\"id\":" + id + ",\"success\":true}");
        } catch (Exception e) {
            resp.setStatus(400);
            com.ecommerce.util.ApiResponse.internalError(resp);
        }
    }

    // ==================== PUT ====================

    @Override
    @SuppressWarnings("unchecked")
    protected void doPut(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");
        try {
            if (com.ecommerce.util.AdminAuth.requireAdmin(req, resp) <= 0) return;
            Map<String, Object> body = JsonUtil.fromJson(readBody(req), Map.class);
            String[] parts = pathParts(req);
            int id = parts.length == 1 && parts[0].matches("\\d+")
                    ? Integer.parseInt(parts[0])
                    : intValue(body.get("id"), 0);
            Category cat = categoryFromBody(body, id);
            categoryService.updateCategory(cat);
            resp.getWriter().write("{\"success\":true}");
        } catch (Exception e) {
            resp.setStatus(400);
            com.ecommerce.util.ApiResponse.internalError(resp);
        }
    }

    // ==================== PATCH (statut) ====================
    // HttpServlet ne définit pas doPatch : on route via service().

    @Override
    protected void service(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        if ("PATCH".equalsIgnoreCase(req.getMethod())) {
            handlePatch(req, resp);
        } else {
            super.service(req, resp);
        }
    }

    @SuppressWarnings("unchecked")
    protected void handlePatch(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");
        try {
            if (com.ecommerce.util.AdminAuth.requireAdmin(req, resp) <= 0) return;
            String[] parts = pathParts(req);
            if (parts.length != 2 || !"status".equals(parts[1])) {
                resp.setStatus(404);
                resp.getWriter().write("{\"error\":\"Utilisez PATCH /api/categories/{id}/status\"}");
                return;
            }
            int id = Integer.parseInt(parts[0]);
            Map<String, Object> body = JsonUtil.fromJson(readBody(req), Map.class);
            Boolean active = boolValue(body.get("active"));
            if (active == null) categoryService.toggleActive(id);
            else categoryService.setActive(id, active);
            resp.getWriter().write("{\"success\":true}");
        } catch (Exception e) {
            resp.setStatus(400);
            com.ecommerce.util.ApiResponse.internalError(resp);
        }
    }

    // ==================== DELETE ====================

    @Override
    protected void doDelete(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");
        try {
            if (com.ecommerce.util.AdminRole.requireAction(req, resp,
                    com.ecommerce.util.AdminRole.DESTRUCTIVE) <= 0) return;
            String[] parts = pathParts(req);
            int id;
            if (parts.length == 1 && parts[0].matches("\\d+")) {
                id = Integer.parseInt(parts[0]);
            } else {
                id = Integer.parseInt(req.getParameter("id"));
            }
            categoryService.deleteCategory(id);
            resp.getWriter().write("{\"success\":true}");
        } catch (Exception e) {
            resp.setStatus(400);
            com.ecommerce.util.ApiResponse.internalError(resp);
        }
    }

    // ==================== HELPERS ====================

    private Category categoryFromBody(Map<String, Object> body, int id) {
        Category cat = new Category();
        if (id > 0) cat.setId(id);
        else if (body.get("id") != null) cat.setId(intValue(body.get("id"), 0));
        cat.setName((String) body.get("name"));
        cat.setSlug((String) body.get("slug"));
        Object parent = body.get("parentId") != null ? body.get("parentId") : body.get("parent_id");
        cat.setParentId(parent == null ? null : intValue(parent, 0) == 0 ? null : intValue(parent, 0));
        cat.setDescription((String) body.get("description"));
        cat.setIcon((String) body.get("icon"));
        cat.setImage((String) body.get("image"));
        Object active = body.get("active") != null ? body.get("active") : body.get("is_active");
        cat.setActive(active == null || Boolean.TRUE.equals(toBoolean(active)));
        Object order = body.get("displayOrder") != null ? body.get("displayOrder") : body.get("display_order");
        cat.setDisplayOrder(order == null ? 0 : intValue(order, 0));
        return cat;
    }

    private int intValue(Object o, int def) {
        if (o == null) return def;
        if (o instanceof Number) return ((Number) o).intValue();
        try { return Integer.parseInt(String.valueOf(o)); } catch (NumberFormatException e) { return def; }
    }

    private Integer parseIntOrNull(String s) {
        if (s == null || s.isEmpty()) return null;
        try { return Integer.parseInt(s); } catch (NumberFormatException e) { return null; }
    }

    private int parseInt(String s, int def) {
        Integer v = parseIntOrNull(s);
        return v == null ? def : v;
    }

    private Boolean toBoolean(Object o) {
        if (o == null) return null;
        if (o instanceof Boolean) return (Boolean) o;
        String s = String.valueOf(o);
        if ("true".equalsIgnoreCase(s) || "1".equals(s)) return true;
        if ("false".equalsIgnoreCase(s) || "0".equals(s)) return false;
        return null;
    }

    private Boolean boolValue(Object o) {
        return toBoolean(o);
    }

    private String safe(String s) {
        return s == null ? "Erreur" : s.replace("\\", "\\\\").replace("\"", "'");
    }
}
