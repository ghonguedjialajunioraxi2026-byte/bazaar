package com.ecommerce.util;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * Vérification des jetons Google Sign-In (Google Identity Services)
 * pour l'espace fournisseur.
 *
 * Le CLIENT_ID est conservé en dur dans le code afin que
 * l'authentification Google reste fonctionnelle en permanence.
 * Il peut toutefois être surchargé via la variable d'environnement
 * GOOGLE_CLIENT_ID si besoin.
 */
public class GoogleAuthUtil {

    public static final String GOOGLE_CLIENT_ID = System.getenv().getOrDefault(
            "GOOGLE_CLIENT_ID",
            "748209712105-k04jfq3re9gvc1scmfj6djr9vg2uhfd7.apps.googleusercontent.com");

    private static final String TOKENINFO_URL = "https://oauth2.googleapis.com/tokeninfo?id_token=";

    public static class GoogleUser {
        public String sub;
        public String email;
        public String name;
        public String picture;
        public boolean emailVerified;
    }

    /**
     * Vérifie un ID token Google auprès de Google et retourne
     * le profil de l'utilisateur. Lève IOException si invalide.
     */
    public static GoogleUser verify(String idToken) throws IOException {
        if (idToken == null || idToken.trim().isEmpty()) {
            throw new IOException("Jeton Google manquant");
        }
        String url = TOKENINFO_URL + URLEncoder.encode(idToken.trim(), StandardCharsets.UTF_8);
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setConnectTimeout(8000);
        conn.setReadTimeout(8000);
        conn.setRequestMethod("GET");

        int status = conn.getResponseCode();
        if (status != 200) {
            throw new IOException("Jeton Google invalide ou expiré (HTTP " + status + ")");
        }

        StringBuilder sb = new StringBuilder();
        try (BufferedReader br = new BufferedReader(
                new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = br.readLine()) != null) sb.append(line);
        } finally {
            conn.disconnect();
        }

        Map<String, Object> info = JsonUtil.fromJson(sb.toString(), Map.class);

        // Le token doit avoir été émis pour NOTRE application
        Object aud = info.get("aud");
        if (aud == null || !GOOGLE_CLIENT_ID.equals(String.valueOf(aud))) {
            throw new IOException("Jeton Google non destiné à cette application");
        }

        // Expiration
        Object exp = info.get("exp");
        if (exp != null) {
            try {
                long expSec = Long.parseLong(String.valueOf(exp));
                if (expSec * 1000L < System.currentTimeMillis()) {
                    throw new IOException("Jeton Google expiré");
                }
            } catch (NumberFormatException ignored) {}
        }

        GoogleUser user = new GoogleUser();
        user.sub = str(info.get("sub"));
        Object email = info.get("email");
        user.email = email == null ? null : String.valueOf(email).trim().toLowerCase();
        user.name = str(info.get("name"));
        user.picture = str(info.get("picture"));
        user.emailVerified = "true".equalsIgnoreCase(str(info.get("email_verified")));

        if (user.sub == null || user.sub.isEmpty() || user.email == null || user.email.isEmpty()) {
            throw new IOException("Jeton Google incomplet (email manquant)");
        }
        if (!user.emailVerified) {
            throw new IOException("L'adresse email Google n'est pas vérifiée");
        }
        return user;
    }

    private static String str(Object o) {
        return o == null ? null : String.valueOf(o);
    }
}
