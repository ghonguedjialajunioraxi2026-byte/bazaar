package com.ecommerce.util;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.Part;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

/**
 * Stockage persistant des fichiers uploadés (produits, catégories, logos).
 *
 * Problème résolu : les servlets écrivaient dans {@code getRealPath("/")},
 * c'est-à-dire DANS le dossier déployé de Tomcat. À chaque redeploy du WAR,
 * Tomcat efface ce dossier et toutes les images uploadées étaient perdues
 * (images produits 404 après chaque mise à jour).
 *
 * Désormais les fichiers sont écrits dans un dossier PERSISTANT :
 *   1. variable d'environnement MBOA_UPLOADS_DIR si définie,
 *   2. sinon {@code <user.home>/mboa-style-uploads}.
 *
 * Les URLs restent stables : {@code uploads/products/xxx.jpg} et sont
 * servies par {@code UploadServlet} (mappé sur {@code /uploads/*}).
 * Une copie de compatibilité est aussi écrite dans le webapp déployé
 * quand c'est possible (accès direct sans passer par le servlet).
 */
public final class UploadUtil {

    private UploadUtil() {}

    public static final String SUBDIR_PRODUCTS = "products";
    public static final String SUBDIR_CATEGORIES = "categories";

    /** Racine persistante (créée si absente). */
    public static File rootDir() {
        String configured = System.getenv("MBOA_UPLOADS_DIR");
        File root;
        if (configured != null && !configured.isBlank()) {
            root = new File(configured);
        } else {
            root = new File(System.getProperty("user.home"), "mboa-style-uploads");
        }
        if (!root.exists()) root.mkdirs();
        return root;
    }

    /** Dossier persistant d'une sous-catégorie (créé si absent). */
    public static File subDir(String subdir) {
        File dir = new File(rootDir(), subdir);
        if (!dir.exists()) dir.mkdirs();
        return dir;
    }

    /**
     * Sauvegarde un {@link Part} uploadé dans le dossier persistant.
     *
     * @param part   partie multipart (fichier)
     * @param subdir sous-dossier ("products" ou "categories")
     * @param req    requête (pour la copie de compatibilité vers le webapp)
     * @return URL relative stable, ex. {@code uploads/products/abc123.jpg}
     */
    public static String saveImage(Part part, String subdir, HttpServletRequest req) throws IOException {
        String ext = extOf(part);
        String filename = UUID.randomUUID().toString().replace("-", "") + ext;
        Path target = Paths.get(subDir(subdir).getAbsolutePath(), filename);
        try (InputStream in = part.getInputStream()) {
            Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
        }
        // Copie de compatibilité dans le webapp déployé (best-effort).
        try {
            String realPath = req != null ? req.getServletContext().getRealPath("/") : null;
            if (realPath != null) {
                File legacyDir = new File(realPath, "uploads/" + subdir);
                if (!legacyDir.exists()) legacyDir.mkdirs();
                Files.copy(target, Paths.get(legacyDir.getAbsolutePath(), filename),
                        StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (Exception ignored) {
            // Le dossier persistant + UploadServlet suffisent.
        }
        return "uploads/" + subdir + "/" + filename;
    }

    /** Résout un chemin relatif {@code uploads/...} vers le fichier persistant. */
    public static File resolve(String relativeUrl) {
        if (relativeUrl == null) return null;
        String clean = relativeUrl.startsWith("/") ? relativeUrl.substring(1) : relativeUrl;
        if (!clean.startsWith("uploads/")) return null;
        // Anti path-traversal.
        if (clean.contains("..") || clean.contains("\\")) return null;
        File f = new File(rootDir(), clean.substring("uploads/".length()));
        return (f.isFile() && f.canRead()) ? f : null;
    }

    private static String extOf(Part part) {
        String ext = "";
        String cd = part.getHeader("content-disposition");
        if (cd != null) {
            for (String token : cd.split(";")) {
                if (token.trim().startsWith("filename")) {
                    String fname = token.substring(token.indexOf('=') + 1).trim().replace("\"", "");
                    int dot = fname.lastIndexOf('.');
                    if (dot >= 0) ext = fname.substring(dot).toLowerCase();
                }
            }
        }
        if (!ext.matches("(?i)\\.(jpg|jpeg|png|gif|webp|svg)")) ext = ".jpg";
        return ext;
    }
}
