package com.ecommerce.controller;

import com.ecommerce.dao.ProductImageDAO;
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
import java.util.List;
import java.util.Map;

@WebServlet("/api/product-images")
@MultipartConfig(
    fileSizeThreshold = 1024 * 1024,
    maxFileSize = 10 * 1024 * 1024,
    maxRequestSize = 20 * 1024 * 1024
)
public class ProductImageServlet extends HttpServlet {
    private final ProductImageDAO productImageDAO = new ProductImageDAO();

    private String getUploadDir(HttpServletRequest req) {
        // Dossier persistant (survit aux redeploys) — voir UploadUtil.
        return UploadUtil.subDir(UploadUtil.SUBDIR_PRODUCTS).getAbsolutePath();
    }

    private String saveFile(Part part, String uploadDir) throws IOException {
        // Écrit dans le dossier persistant, URL stable "uploads/products/xxx".
        return UploadUtil.saveImage(part, UploadUtil.SUBDIR_PRODUCTS, null);
    }

    /** N'accepte que les images servies en http(s) ou les chemins internes. */
    static boolean isSafeImageUrl(String url) {
        if (url == null) return false;
        String u = url.trim();
        if (u.isEmpty() || u.length() > 500) return false;
        String low = u.toLowerCase();
        if (low.contains("<") || low.contains(">") || low.contains("\"")
                || low.contains(" ") || low.contains("\\")) return false;
        if (low.contains("javascript:") || low.contains("data:")
                || low.contains("vbscript:")) return false;
        if (u.startsWith("/")) return !u.startsWith("//");
        if (low.contains("://")) {
            return low.startsWith("http://") || low.startsWith("https://");
        }
        return true; // chemin relatif interne (uploads/...)
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");
        try {
            String idStr = req.getParameter("id");
            String productIdStr = req.getParameter("productId");

            if (idStr != null) {
                int imageId = Integer.parseInt(idStr);
                resp.getWriter().write("{\"id\":" + imageId + "}");
                return;
            }

            int productId = Integer.parseInt(productIdStr);
            boolean details = "true".equals(req.getParameter("details"));
            if (details) {
                resp.getWriter().write(JsonUtil.toJson(productImageDAO.findDetailsByProduct(productId)));
            } else {
                resp.getWriter().write(JsonUtil.toJson(productImageDAO.findByProduct(productId)));
            }
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
            // Upload réservé à l'admin (lecture publique inchangée).
            if (com.ecommerce.util.AdminAuth.requireAdmin(req, resp) <= 0) return;
            String contentType = req.getContentType();
            if (contentType != null && contentType.contains("multipart/form-data")) {
                // File upload mode
                Part filePart = req.getPart("file");
                int productId = Integer.parseInt(req.getParameter("productId"));
                boolean isPrimary = "true".equals(req.getParameter("isPrimary"));

                if (filePart == null || filePart.getSize() == 0) {
                    resp.setStatus(400);
                    resp.getWriter().write("{\"error\":\"No file uploaded\"}");
                    return;
                }

                String uploadDir = getUploadDir(req);
                String url = saveFile(filePart, uploadDir);
                productImageDAO.addImage(productId, url, isPrimary);
                resp.getWriter().write("{\"success\":true,\"url\":\"" + url + "\"}");
            } else {
                // JSON mode (URL)
                BufferedReader reader = req.getReader();
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) sb.append(line);
                Map<String, Object> body = JsonUtil.fromJson(sb.toString(), Map.class);

                int productId = ((Number) body.get("productId")).intValue();
                String url = (String) body.get("url");
                boolean isPrimary = body.get("isPrimary") != null && (Boolean) body.get("isPrimary");

                // URL externe : http(s) uniquement, jamais javascript:/data:.
                if (!isSafeImageUrl(url)) {
                    resp.setStatus(400);
                    resp.getWriter().write("{\"error\":\"URL d'image invalide\"}");
                    return;
                }

                productImageDAO.addImage(productId, url, isPrimary);
                resp.getWriter().write("{\"success\":true,\"url\":\"" + url + "\"}");
            }
        } catch (java.io.IOException ioe) {
            // Upload refusé (format, contenu, taille) : erreur client, pas 500.
            resp.setStatus(400);
            resp.getWriter().write("{\"error\":\"Image invalide ou refusée\"}");
        } catch (Exception e) {
            resp.setStatus(500);
            com.ecommerce.util.ApiResponse.internalError(resp);
        }
    }

    @Override
    protected void doPut(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");
        try {
            if (com.ecommerce.util.AdminAuth.requireAdmin(req, resp) <= 0) return;
            BufferedReader reader = req.getReader();
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) sb.append(line);
            Map<String, Object> body = JsonUtil.fromJson(sb.toString(), Map.class);

            String action = (String) body.get("action");
            if ("setPrimary".equals(action)) {
                int productId = ((Number) body.get("productId")).intValue();
                int imageId = ((Number) body.get("imageId")).intValue();
                productImageDAO.setPrimary(productId, imageId);
                resp.getWriter().write("{\"success\":true}");
            } else {
                resp.setStatus(400);
                resp.getWriter().write("{\"error\":\"Unknown action\"}");
            }
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
            if (com.ecommerce.util.AdminAuth.requireAdmin(req, resp) <= 0) return;
            int id = Integer.parseInt(req.getParameter("id"));
            productImageDAO.removeImage(id);
            resp.getWriter().write("{\"success\":true}");
        } catch (Exception e) {
            resp.setStatus(500);
            com.ecommerce.util.ApiResponse.internalError(resp);
        }
    }
}
