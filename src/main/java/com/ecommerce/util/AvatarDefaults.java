package com.ecommerce.util;

/**
 * Photos de profil par défaut BAZAAR.
 *
 * Règle produit : la photo est FACULTATIVE partout.
 * - Fournisseur : sans logo de boutique, le compte reçoit
 *   {@link #DEFAULT_SUPPLIER_LOGO} (visuel boutique réservé).
 * - Client : sans photo, le compte reçoit {@link #DEFAULT_CLIENT_AVATAR}
 *   (visuel client réservé).
 *
 * Les fichiers vivent dans {@code src/main/webapp/assets/img/} donc ils
 * sont embarqués dans le WAR et toujours disponibles (aucune dépendance
 * externe, aucun appel réseau).
 */
public final class AvatarDefaults {

    private AvatarDefaults() {}

    /** Logo réservé aux boutiques sans photo. */
    public static final String DEFAULT_SUPPLIER_LOGO = "assets/img/default-supplier.svg";

    /** Avatar réservé aux clients sans photo. */
    public static final String DEFAULT_CLIENT_AVATAR = "assets/img/default-client.svg";

    /** Logo boutique à afficher : le logo s'il existe, sinon le réservé. */
    public static String supplierLogoOrDefault(String logo) {
        if (logo == null || logo.trim().isEmpty()) return DEFAULT_SUPPLIER_LOGO;
        return logo;
    }

    /** Avatar client à afficher : l'avatar s'il existe, sinon le réservé. */
    public static String customerAvatarOrDefault(String avatar) {
        if (avatar == null || avatar.trim().isEmpty()) return DEFAULT_CLIENT_AVATAR;
        return avatar;
    }

    /**
     * URL d'image acceptable pour un avatar/logo fourni par l'utilisateur.
     * Accepte : chemins internes {@code uploads/...}, {@code assets/...}
     * et URLs http(s). Refuse : vide, trop long, javascript:, data:, HTML.
     */
    public static boolean isAcceptableImageUrl(String url) {
        if (url == null) return false;
        String u = url.trim();
        if (u.isEmpty() || u.length() > 500) return false;
        if (u.contains("<") || u.contains(">") || u.contains("\"")
                || u.contains("..") || u.contains("\\")) return false;
        String low = u.toLowerCase();
        if (low.startsWith("javascript:") || low.startsWith("data:")
                || low.startsWith("vbscript:")) return false;
        return low.startsWith("uploads/") || low.startsWith("/uploads/")
            || low.startsWith("assets/") || low.startsWith("/assets/")
            || low.startsWith("http://") || low.startsWith("https://");
    }
}
