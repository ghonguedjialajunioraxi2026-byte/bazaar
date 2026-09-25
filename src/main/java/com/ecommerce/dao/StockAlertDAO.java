package com.ecommerce.dao;

import com.ecommerce.config.DBConnection;

import java.sql.*;

public class StockAlertDAO {

    public void log(int productId, int oldStock, int newStock, String action) throws SQLException {
        String sql = "INSERT INTO stock_alerts (product_id, old_stock, new_stock, action) VALUES (?, ?, ?, ?)";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, productId);
            ps.setInt(2, oldStock);
            ps.setInt(3, newStock);
            ps.setString(4, action);
            ps.executeUpdate();
        }
    }
}
