package com.ecommerce.dao;

import com.ecommerce.config.DBConnection;
import com.ecommerce.model.Order;
import com.ecommerce.model.OrderItem;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class OrderDAO {

    public int create(Order order) throws SQLException {
        // Concurrence : deux commandes simultanées peuvent tirer le même
        // numéro (MAX+1). Contrainte UNIQUE + rejeu (rollback sûr : tout est
        // transactionnel, notifications dédupliquées par clé).
        SQLException last = null;
        for (int attempt = 0; attempt < 3; attempt++) {
            try {
                return createOnce(order);
            } catch (SQLException e) {
                if (isDuplicateOrderNumber(e)) {
                    last = e;
                    continue;
                }
                throw e;
            }
        }
        throw last;
    }

    private static boolean isDuplicateOrderNumber(SQLException e) {
        if (!"23000".equals(e.getSQLState())) return false;
        String m = e.getMessage() == null ? "" : e.getMessage();
        return m.contains("order_number") || m.contains("uq_orders_number");
    }

    private int createOnce(Order order) throws SQLException {
        String sqlOrder = "INSERT INTO orders (order_number, fullname, phone, city, district, address, comment, delivery_mode, payment_method, total, supplier_id, status) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'EN_ATTENTE')";
        String sqlItem = "INSERT INTO order_items (order_id, product_id, product_name, size, color, qty, unit_price, offer_id) VALUES (?, ?, ?, ?, ?, ?, ?, ?)";

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
                        psItem.setString(3, item.getProductName());
                        psItem.setString(4, item.getSize());
                        psItem.setString(5, item.getColor());
                        psItem.setInt(6, item.getQty());
                        psItem.setInt(7, item.getUnitPrice());
                        if (item.getOfferId() != null) psItem.setInt(8, item.getOfferId());
                        else psItem.setNull(8, Types.INTEGER);
                        psItem.addBatch();

                        psDec.setInt(1, item.getQty());
                        psDec.setInt(2, item.getProductId());
                        psDec.setInt(3, item.getQty());
                        // Sécurité anti-survente (course) : 0 ligne = stock épuisé entre-temps.
                        int decremented = psDec.executeUpdate();
                        if (decremented == 0) {
                            throw new SQLException("STOCK_INSUFFISANT: stock épuisé pour l'un des produits");
                        }

                        // Check low stock
                        try {
                            ProductDAO pDao = new ProductDAO();
                            var product = pDao.findById(item.getProductId());
                            if (product != null) {
                                com.ecommerce.service.NotificationService notifs =
                                    new com.ecommerce.service.NotificationService();
                                String pname = product.getName();
                                if (product.getStock() <= 0) {
                                    // Rupture réelle (stock à zéro après décrément).
                                    notifs.notifyAdmins("PRODUCT_OUT_OF_STOCK",
                                        "Rupture de stock : " + pname,
                                        "« " + pname + " » est épuisé.",
                                        "PRODUCT", product.getId(), null,
                                        "PRODUCT:" + product.getId() + ":OUT");
                                    if (product.getSupplierId() > 0) {
                                        notifs.notifySupplier(product.getSupplierId(), "PRODUCT_OUT_OF_STOCK",
                                            "Rupture de stock : " + pname,
                                            "« " + pname + " » est épuisé.",
                                            "PRODUCT", product.getId(), null,
                                            "PRODUCT:" + product.getId() + ":OUT:SUP" + product.getSupplierId());
                                    }
                                } else if (product.getStock() <= com.ecommerce.service.NotificationType.LOW_STOCK_THRESHOLD) {
                                    notifs.notifyAdmins("PRODUCT_LOW_STOCK",
                                        "Stock faible : " + pname,
                                        "Il ne reste que " + product.getStock() + " unité(s) en stock pour « " + pname + " ».",
                                        "PRODUCT", product.getId(), null,
                                        "PRODUCT:" + product.getId() + ":LOW");
                                    if (product.getSupplierId() > 0) {
                                        notifs.notifySupplier(product.getSupplierId(), "PRODUCT_LOW_STOCK",
                                            "Stock faible : " + pname,
                                            "Il ne reste que " + product.getStock() + " unité(s) en stock pour « " + pname + " ».",
                                            "PRODUCT", product.getId(), null,
                                            "PRODUCT:" + product.getId() + ":LOW:SUP" + product.getSupplierId());
                                    }
                                }
                            }
                        } catch (Exception ignored) {}
                    }
                    psItem.executeBatch();
                }

                // Ligne paiement EN_ATTENTE, atomique avec la commande (Phase 11).
                // Montant = total serveur ; méthode validée côté service.
                try (PreparedStatement psPay = conn.prepareStatement(
                        "INSERT INTO payments (order_id, method, provider, amount, currency, idempotence_key, status) VALUES (?, ?, ?, ?, 'FCFA', ?, 'EN_ATTENTE')")) {
                    psPay.setInt(1, orderId);
                    psPay.setString(2, order.getPaymentMethod() != null ? order.getPaymentMethod() : "PAIEMENT_LIVRAISON");
                    String method = order.getPaymentMethod() != null ? order.getPaymentMethod() : "PAIEMENT_LIVRAISON";
                    psPay.setString(3, "PAIEMENT_LIVRAISON".equals(method) ? "COD"
                        : ("MOBILE_MONEY_MTN".equals(method) || "MOBILE_MONEY_ORANGE".equals(method) ? "MANUAL_MOMO" : "LEGACY"));
                    psPay.setInt(4, order.getTotal());
                    psPay.setString(5, "PAY-" + orderId);
                    psPay.executeUpdate();
                }

                conn.commit();

                // Send notifications (after commit)
                try {
                    com.ecommerce.service.NotificationService notifs =
                        new com.ecommerce.service.NotificationService();
                    // Admins : nouvelle commande (idempotent par commande).
                    notifs.notifyAdmins("ORDER_CREATED",
                        "Nouvelle commande reçue",
                        "Commande " + orderNumber + " de " + order.getFullname()
                            + " (" + order.getTotal() + " FCFA).",
                        "ORDER", orderId, "/api/orders?action=find&id=" + orderId,
                        "ORDER:" + orderId + ":CREATED");
                    // Fournisseur propriétaire : commande à préparer.
                    if (order.getSupplierId() > 0) {
                        notifs.notifySupplier(order.getSupplierId(), "ORDER_CREATED",
                            "Nouvelle commande à préparer",
                            "Commande " + orderNumber + " (" + order.getTotal() + " FCFA).",
                            "ORDER", orderId, null,
                            "ORDER:" + orderId + ":SUPPLIER");
                    }
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
        String sql = "SELECT * FROM orders ORDER BY created_at DESC LIMIT 500";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) list.add(mapRow(rs));
        }
        return list;
    }

    public Order findById(int id) throws SQLException {
        Order order = null;
        String sqlOrder = "SELECT o.*, py.status AS payment_status FROM orders o LEFT JOIN payments py ON py.order_id = o.id WHERE o.id = ?";
        // product_name snapshot prioritaire, nom actuel en repli (lignes pré-migration)
        String sqlItems = "SELECT oi.*, p.name AS current_name FROM order_items oi LEFT JOIN products p ON p.id = oi.product_id WHERE oi.order_id = ?";

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
                    String snap = rsItems.getString("product_name");
                    item.setProductName(snap != null ? snap : rsItems.getString("current_name"));
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

    /** Commandes d'un invité identifié par son téléphone (secret partagé, documenté). */
    public List<Order> findByPhone(String phone) throws SQLException {
        List<Order> list = new ArrayList<>();
        String sql = "SELECT o.*, py.status AS payment_status FROM orders o LEFT JOIN payments py ON py.order_id = o.id WHERE o.phone = ? ORDER BY o.created_at DESC";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, phone);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Order o = mapRow(rs);
                    o.setItems(itemsOf(conn, o.getId()));
                    list.add(o);
                }
            }
        }
        return list;
    }

    /** Détail invité : numéro + téléphone requis (pas d'IDOR par ID seul). */
    public Order findByNumberAndPhone(String number, String phone) throws SQLException {
        String sql = "SELECT o.*, py.status AS payment_status FROM orders o LEFT JOIN payments py ON py.order_id = o.id WHERE o.order_number = ? AND o.phone = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, number);
            ps.setString(2, phone);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    Order o = mapRow(rs);
                    o.setItems(itemsOf(conn, o.getId()));
                    return o;
                }
            }
        }
        return null;
    }

    private List<OrderItem> itemsOf(Connection conn, int orderId) throws SQLException {
        List<OrderItem> items = new ArrayList<>();
        String sql = "SELECT oi.*, p.name AS current_name FROM order_items oi LEFT JOIN products p ON p.id = oi.product_id WHERE oi.order_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, orderId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    OrderItem item = new OrderItem();
                    item.setId(rs.getInt("id"));
                    item.setOrderId(rs.getInt("order_id"));
                    item.setProductId(rs.getInt("product_id"));
                    String snap = rs.getString("product_name");
                    item.setProductName(snap != null ? snap : rs.getString("current_name"));
                    item.setSize(rs.getString("size"));
                    item.setColor(rs.getString("color"));
                    item.setQty(rs.getInt("qty"));
                    item.setUnitPrice(rs.getInt("unit_price"));
                    items.add(item);
                }
            }
        }
        return items;
    }

    /**
     * Annulation invitée : EN_ATTENTE uniquement, stock restauré, atomique.
     * @return true si annulée, false si introuvable / déjà traitée.
     */
    public boolean cancelGuestOrder(int orderId) throws SQLException {
        String lock = "SELECT status FROM orders WHERE id = ? FOR UPDATE";
        try (Connection conn = DBConnection.getConnection()) {
            conn.setAutoCommit(false);
            try {
                String status;
                try (PreparedStatement ps = conn.prepareStatement(lock)) {
                    ps.setInt(1, orderId);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next()) { conn.rollback(); return false; }
                        status = rs.getString("status");
                    }
                }
                if (!"EN_ATTENTE".equals(status)) { conn.rollback(); return false; }
                try (PreparedStatement ps = conn.prepareStatement(
                        "UPDATE products p JOIN order_items oi ON oi.product_id = p.id SET p.stock = p.stock + oi.qty WHERE oi.order_id = ?")) {
                    ps.setInt(1, orderId);
                    ps.executeUpdate();
                }
                try (PreparedStatement ps = conn.prepareStatement("UPDATE orders SET status = 'ANNULEE' WHERE id = ?")) {
                    ps.setInt(1, orderId);
                    ps.executeUpdate();
                }
                conn.commit();
                return true;
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            }
        }
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
        // Restauration du stock + suppressions DANS la même transaction
        // (aucune fenêtre d'incohérence en cas de crash).
        String sqlItems = "DELETE FROM order_items WHERE order_id = ?";
        String sqlOrder = "DELETE FROM orders WHERE id = ?";
        String sqlRestore = "UPDATE products SET stock = stock + ? WHERE id = ?";
        try (Connection conn = DBConnection.getConnection()) {
            conn.setAutoCommit(false);
            try {
                List<OrderItem> items;
                try (PreparedStatement psFind = conn.prepareStatement(
                        "SELECT product_id, qty FROM order_items WHERE order_id = ?")) {
                    psFind.setInt(1, id);
                    items = new ArrayList<>();
                    try (ResultSet rs = psFind.executeQuery()) {
                        while (rs.next()) {
                            OrderItem it = new OrderItem();
                            it.setProductId(rs.getInt("product_id"));
                            it.setQty(rs.getInt("qty"));
                            items.add(it);
                        }
                    }
                }
                if (items != null) {
                    try (PreparedStatement psRestore = conn.prepareStatement(sqlRestore)) {
                        for (OrderItem item : items) {
                            psRestore.setInt(1, item.getQty());
                            psRestore.setInt(2, item.getProductId());
                            psRestore.executeUpdate();
                        }
                    }
                }
                try (PreparedStatement psi = conn.prepareStatement(sqlItems)) {
                    psi.setInt(1, id);
                    psi.executeUpdate();
                }
                try (PreparedStatement pso = conn.prepareStatement(sqlOrder)) {
                    pso.setInt(1, id);
                    pso.executeUpdate();
                }
                conn.commit();
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            }
        }
    }

    /**
     * Recherche publique : numéro de commande UNIQUEMENT (Phase 7).
     * Ne jamais exposer nom/téléphone en recherche libre (données personnelles).
     * L'administration filtre côté dashboard sur la liste complète (authentifiée).
     */
    public List<Order> search(String query) throws SQLException {
        List<Order> list = new ArrayList<>();
        String sql = "SELECT * FROM orders WHERE order_number LIKE ? "
            + "ORDER BY created_at DESC";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            String q = "%" + query + "%";
            ps.setString(1, q);
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
        String sql = "SELECT o.*, py.status AS payment_status FROM orders o LEFT JOIN payments py ON py.order_id = o.id WHERE o.supplier_id = ? ORDER BY o.created_at DESC";
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
        try { o.setPaymentStatus(rs.getString("payment_status")); } catch (SQLException ignored) {}
        o.setTotal(rs.getInt("total"));
        o.setSupplierId(rs.getInt("supplier_id"));
        o.setStatus(rs.getString("status"));
        o.setCreatedAt(rs.getString("created_at"));
        return o;
    }
}
