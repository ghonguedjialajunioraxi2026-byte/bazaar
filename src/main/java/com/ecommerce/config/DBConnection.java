package com.ecommerce.config;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class DBConnection {

    // Secrets via environnement (DB_URL/DB_USER/DB_PASSWORD).
    // Les replis ci-dessous sont DEV LOCAL UNIQUEMENT : en production,
    // définir les variables d'environnement (voir docs/security.md).
    private static final boolean USING_DEFAULTS =
        System.getenv("DB_URL") == null && System.getenv("DB_PASSWORD") == null;

    private static final String URL = System.getenv().getOrDefault("DB_URL",
        "jdbc:mysql://localhost:3306/mboa_style"
        + "?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Africa/Douala");

    private static final String USER = System.getenv().getOrDefault("DB_USER", "root");
    private static final String PASSWORD = System.getenv().getOrDefault("DB_PASSWORD", "TonMotDePasse123");

    static {
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
        } catch (ClassNotFoundException e) {
            throw new RuntimeException("Driver MySQL introuvable : ajoute mysql-connector-j au classpath", e);
        }
        if (USING_DEFAULTS) {
            // Aucun secret imprimé : simple avertissement de configuration.
            System.err.println("[SECURITE] DB_URL/DB_PASSWORD non définis : repli DEV local utilisé. "
                + "En production, définissez les variables d'environnement.");
        }
    }

    private DBConnection() {}

    public static Connection getConnection() throws SQLException {
        return DriverManager.getConnection(URL, USER, PASSWORD);
    }

    public static void main(String[] args) {
        try (Connection c = getConnection()) {
            if (c.isValid(2)) {
                System.out.println("Connexion OK -> base : " + c.getCatalog());
            }
        } catch (SQLException e) {
            System.err.println("Échec de connexion : " + e.getMessage());
        }
    }
}
