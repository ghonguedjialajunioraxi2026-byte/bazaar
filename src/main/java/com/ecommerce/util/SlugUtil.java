package com.ecommerce.util;

import java.text.Normalizer;
import java.util.Locale;

/**
 * Génération de slugs URL propres, avec gestion des accents français.
 *
 * Exemples :
 *   "Montres & Accessoires"    -> "montres-accessoires"
 *   "Électronique & High-Tech" -> "electronique-high-tech"
 */
public final class SlugUtil {

    private SlugUtil() {}

    public static String toSlug(String input) {
        if (input == null) return "";
        // Décompose les caractères accentués (é -> e + accent) puis supprime les accents
        String s = Normalizer.normalize(input.trim(), Normalizer.Form.NFD)
                .replaceAll("\\p{InCombiningDiacriticalMarks}+", "");
        // Cas particuliers non couverts par la décomposition
        s = s.replace('œ', 'o').replace('Œ', 'o')
             .replace('æ', 'a').replace('Æ', 'a')
             .replace('ß', 's').replace('ø', 'o');
        s = s.toLowerCase(Locale.FRENCH).replaceAll("[’']", "");
        // Tout ce qui n'est pas alphanumérique devient un tiret
        s = s.replaceAll("[^a-z0-9]+", "-").replaceAll("^-+|-+$", "");
        return s.isEmpty() ? "categorie" : s;
    }
}
