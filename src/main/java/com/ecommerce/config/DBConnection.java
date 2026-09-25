package com.ecommerce.config;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class DBConnection {

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
