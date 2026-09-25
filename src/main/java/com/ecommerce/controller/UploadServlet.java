package com.ecommerce.controller;

import com.ecommerce.util.UploadUtil;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

/**
 * Sert les fichiers uploadés depuis le dossier persistant
 * ({@code MBOA_UPLOADS_DIR} ou {@code ~/mboa-style-uploads}).
 *
 * Mappé sur {@code /uploads/*} : toute URL {@code uploads/products/x.jpg}
 * ou {@code uploads/categories/y.png} est résolue ici, donc les images
 * survivent aux redeploys du WAR.
 */
@WebServlet("/uploads/*")
public class UploadServlet extends HttpServlet {

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        String pathInfo = req.getPathInfo(); // ex. /products/abc.jpg
        if (pathInfo == null || pathInfo.contains("..") || pathInfo.contains("\\")) {
            resp.sendError(HttpServletResponse.SC_BAD_REQUEST);
            return;
        }
        // "uploads" + pathInfo -> ex. uploads/products/abc.jpg
        File file = UploadUtil.resolve("uploads" + pathInfo);
        if (file == null) {
            // Repli : fichier encore présent dans le webapp déployé (anciens uploads).
            String realPath = getServletContext().getRealPath("/uploads" + pathInfo);
            if (realPath != null) {
                File legacy = new File(realPath);
                if (legacy.isFile() && legacy.canRead()) file = legacy;
            }
        }
        if (file == null) {
            resp.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }
        String contentType = getServletContext().getMimeType(file.getName());
        if (contentType == null) contentType = Files.probeContentType(file.toPath());
        if (contentType == null) contentType = "application/octet-stream";
        resp.setContentType(contentType);
        resp.setContentLengthLong(file.length());
        // Cache navigateur 7 jours (les noms de fichiers sont UUID, donc immuables).
        resp.setHeader("Cache-Control", "public, max-age=604800");
        Files.copy(file.toPath(), resp.getOutputStream());
    }
}
