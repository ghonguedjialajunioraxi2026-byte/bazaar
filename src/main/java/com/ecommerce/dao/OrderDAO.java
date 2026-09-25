package com.ecommerce.dao;

import com.ecommerce.config.DBConnection;
import com.ecommerce.model.Order;
import com.ecommerce.model.OrderItem;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class OrderDAO {

    public int create(Order order) throws SQLException {
        String sqlOrder = "INSERT INTO orders (order_number, fullname, phone, city, district, address, comment, delivery_mode, payment_method, total, supplier_id, status) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'EN_ATTENTE')";
        String sqlItem = "INSERT INTO order_items (order_id, product_id, size, color, qty, unit_price) VALUES (?, ?, ?, ?, ?, ?)";

        try (Connection conn = DBConnection.getConnection()) {
            conn.setAutoCommit(false);
            try {
                // Generate order number: CMD-2026-XXXXX
                String orderNumber = generateOrderNumber(conn);

                PreparedStatement psOrder = conn.prepareStatement(sqlOrder, Statement.RETURN_GENERATED_KEYS);
                psOrder.setString(1, orderNumber);
                psOrder.setString(2, order.getFullname());
                psOrder.setString(3, order.getPhone());
                psOrder.setString(4, order.getCity());
                psOrder.setString(5, order.getDistrict());
                psOrder.setString(6, order.getAddress());
                psOrder.setString(7, order.getComment());
                psOrder.setString(8, order.getDeliveryMode() != null ? order.getDeliveryMode() : "Standard");
                psOrder.setString(9, order.getPaymentMethod() != null ? order.getPaymentMethod() : "WHATSAPP");
                psOrder.setInt(10, order.getTotal());
                if (order.getSupplierId() > 0) {
                    psOrder.setInt(11, order.getSupplierId());
                } else {
                    psOrder.setNull(11, Types.INTEGER);
                }
                psOrder.executeUpdate();

                ResultSet keys = psOrder.getGeneratedKeys();
                int orderId = 0;
                if (keys.next()) orderId = keys.getInt(1);

                order.setOrderNumber(orderNumber);

                if (order.getItems() != null) {
                    PreparedStatement psItem = conn.prepareStatement(sqlItem);
                    String sqlDecrement = "UPDATE products SET stock = stock - ? WHERE id = ? AND stock >= ?";
                    PreparedStatement psDec = conn.prepareStatement(sqlDecrement);
                    for (OrderItem item : order.getItems()) {
                        psItem.setInt(1, orderId);
                        psItem.setInt(2, item.getProductId());
                        psItem.setString(3, item.getSize());
                        psItem.setString(4, item.getColor());
                        psItem.setInt(5, item.getQty());
                        psItem.setInt(6, item.getUnitPrice());
                        psItem.addBatch();

                        psDec.setInt(1, item.getQty());
                        psDec.setInt(2, item.getProductId());
                        psDec.setInt(3, item.getQty());
                        psDec.executeUpdate();

                        // Check low stock
                        try {
                            ProductDAO pDao = new ProductDAO();
                            var product = pDao.findById(item.getProductId());
                            if (product != null && product.getStock() <= 5 && product.getStock() > 0) {
                                new NotificationDAO().notifyLowStock(product.getName(), product.getStock());
                            }
                        } catch (Exception ignored) {}
                    }
                    psItem.executeBatch();
                }

                conn.commit();

                // Send notifications (after commit)
                try {
                    NotificationDAO notifDAO = new NotificationDAO();
                    notifDAO.notifyNewOrder(orderId, orderNumber, order.getFullname());
                } catch (Exception ignored) {}

                return orderId;
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            }
        }
    }

    private String generateOrderNumber(Connection conn) throws SQLException {
        int year = java.util.Calendar.getInstance().get(java.util.Calendar.YEAR);
        String prefix = "CMD-" + year + "-";
        String sql = "SELECT MAX(order_number) FROM orders WHERE order_number LIKE ?";
        PreparedStatement ps = conn.prepareStatement(sql);
        ps.setString(1, prefix + "%");
        ResultSet rs = ps.executeQuery();
        int seq = 1;
        if (rs.next() && rs.getString(1) != null) {
            String last = rs.getString(1);
            String numPart = last.substring(prefix.length());
            try { seq = Integer.parseInt(numPart) + 1; } catch (NumberFormatException ignored) {}
        }
        return prefix + String.format("%06d", seq);
    }

    public List<Order> findAll() throws SQLException {
        List<Order> list = new ArrayList<>();
        String sql = "SELECT * FROM orders ORDER BY created_at DESC";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) list.add(mapRow(rs));
        }
        return list;
    }

    public Order findById(int id) throws SQLException {
        Order order = null;
        String sqlOrder = "SELECT * FROM orders WHERE id = ?";
        String sqlItems = "SELECT * FROM order_items WHERE order_id = ?";

        try (Connection conn = DBConnection.getConnection()) {
            PreparedStatement ps = conn.prepareStatement(sqlOrder);
            ps.setInt(1, id);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                order = mapRow(rs);
                List<OrderItem> items = new ArrayList<>();
                PreparedStatement psItems = conn.prepareStatement(sqlItems);
                psItems.setInt(1, id);
                ResultSet rsItems = psItems.executeQuery();
                while (rsItems.next()) {
                    OrderItem item = new OrderItem();
                    item.setId(rsItems.getInt("id"));
                    item.setOrderId(rsItems.getInt("order_id"));
                    item.setProductId(rsItems.getInt("product_id"));
                    item.setSize(rsItems.getString("size"));
                    item.setColor(rsItems.getString("color"));
                    item.setQty(rsItems.getInt("qty"));
                    item.setUnitPrice(rsItems.getInt("unit_price"));
                    items.add(item);
                }
                order.setItems(items);
            }
        }
        return order;
    }

    public void updateStatus(int id, String status) throws SQLException {
        String sql = "UPDATE orders SET status = ? WHERE id = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, status);
            ps.setInt(2, id);
            ps.executeUpdate();
        }
    }

    public void delete(int id) throws SQLException {
        // Restore stock first
        List<OrderItem> items = findById(id).getItems();
        if (items != null) {
            String sqlRestore = "UPDATE products SET stock = stock + ? WHERE id = ?";
            try (Connection conn = DBConnection.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sqlRestore)) {
                for (OrderItem item : items) {
                    ps.setInt(1, item.getQty());
                    ps.setInt(2, item.getProductId());
                    ps.executeUpdate();
                }
            }
        }
        String sqlItems = "DELETE FROM order_items WHERE order_id = ?";
        String sqlOrder = "DELETE FROM orders WHERE id = ?";
        try (Connection conn = DBConnection.getConnection()) {
            conn.setAutoCommit(false);
            try {
                PreparedStatement psi = conn.prepareStatement(sqlItems);
                psi.setInt(1, id);
                psi.executeUpdate();
                PreparedStatement pso = conn.prepareStatement(sqlOrder);
                pso.setInt(1, id);
                pso.executeUpdate();
                conn.commit();
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            }
        }
    }

    public List<Order> search(String query) throws SQLException {
        List<Order> list = new ArrayList<>();
        String sql = "SELECT * FROM orders WHERE order_number LIKE ? OR fullname LIKE ? OR phone LIKE ? "
            + "ORDER BY created_at DESC";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            String q = "%" + query + "%";
            ps.setString(1, q);
            ps.setString(2, q);
            ps.setString(3, q);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) list.add(mapRow(rs));
            }
        }
        return list;
    }

    public List<Order> findByStatus(String status) throws SQLException {
        List<Order> list = new ArrayList<>();
        String sql = "SELECT * FROM orders WHERE status = ? ORDER BY created_at DESC";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, status);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) list.add(mapRow(rs));
            }
        }
        return list;
    }

    public List<Order> findBySupplier(int supplierId) throws SQLException {
        List<Order> list = new ArrayList<>();
        String sql = "SELECT * FROM orders WHERE supplier_id = ? ORDER BY created_at DESC";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, supplierId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) list.add(mapRow(rs));
            }
        }
        return list;
    }

    private Order mapRow(ResultSet rs) throws SQLException {
        Order o = new Order();
        o.setId(rs.getInt("id"));
        o.setOrderNumber(rs.getString("order_number"));
        o.setFullname(rs.getString("fullname"));
        o.setPhone(rs.getString("phone"));
        o.setCity(rs.getString("city"));
        o.setDistrict(rs.getString("district"));
        o.setAddress(rs.getString("address"));
        o.setComment(rs.getString("comment"));
        o.setDeliveryMode(rs.getString("delivery_mode"));
        try { o.setPaymentMethod(rs.getString("payment_method")); } catch (SQLException e) { o.setPaymentMethod("WHATSAPP"); }
        o.setTotal(rs.getInt("total"));
        o.setSupplierId(rs.getInt("supplier_id"));
        o.setStatus(rs.getString("status"));
        o.setCreatedAt(rs.getString("created_at"));
        return o;
    }
}
