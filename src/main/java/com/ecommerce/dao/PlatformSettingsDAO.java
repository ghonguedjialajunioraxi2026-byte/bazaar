package com.ecommerce.dao;

import com.ecommerce.config.DBConnection;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * Paramètres plateforme (table platform_settings) : valeurs métier
 * configurables SANS hardcoder (ex : frais PawaPay Payout en XAF).
 */
public class PlatformSettingsDAO {

    /** Frais PawaPay prélevés sur chaque Payout (XAF, 0 = non configuré). */
    public static final String PAWAPAY_PAYOUT_FEE = "pawapay.payout.fee";

    public String get(String key, String def) throws SQLException {
        String sql = "SELECT setting_value FROM platform_settings WHERE setting_key = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, key);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getString(1);
            }
        }
        return def;
    }

    public int getInt(String key, int def) throws SQLException {
        try {
            return Integer.parseInt(get(key, String.valueOf(def)).trim());
        } catch (Exception e) {
            return def;
        }
    }

    public void set(String key, String value) throws SQLException {
        String sql = "INSERT INTO platform_settings (setting_key, setting_value) VALUES (?, ?)"
            + " ON DUPLICATE KEY UPDATE setting_value = VALUES(setting_value)";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, key);
            ps.setString(2, value);
            ps.executeUpdate();
        }
    }

    public List<java.util.Map<String, String>> findAll() throws SQLException {
        List<java.util.Map<String, String>> list = new ArrayList<>();
        String sql = "SELECT setting_key, setting_value FROM platform_settings ORDER BY setting_key";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                java.util.Map<String, String> m = new java.util.LinkedHashMap<>();
                m.put("key", rs.getString(1));
                m.put("value", rs.getString(2));
                list.add(m);
            }
        }
        return list;
    }
}
