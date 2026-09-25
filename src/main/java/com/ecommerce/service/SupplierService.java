package com.ecommerce.service;

import com.ecommerce.config.DBConnection;
import com.ecommerce.dao.*;
import com.ecommerce.model.*;

import java.sql.*;
import java.util.*;

public class SupplierService {
    private final SupplierDAO supplierDAO = new SupplierDAO();
    private final ProductDAO productDAO = new ProductDAO();
    private final ProductImageDAO productImageDAO = new ProductImageDAO();
    private final OrderDAO orderDAO = new OrderDAO();
    private final ConversationDAO conversationDAO = new ConversationDAO();
    private final SupplierPaymentDAO supplierPaymentDAO = new SupplierPaymentDAO();
    private final NotificationDAO notificationDAO = new NotificationDAO();
    private final SupplierSessionDAO sessionDAO = new SupplierSessionDAO();

    public List<Supplier> getAll() throws SQLException { return supplierDAO.findAll(); }
    public List<Supplier> getActive() throws SQLException { return supplierDAO.findActive(); }
    public Supplier getById(int id) throws SQLException { return supplierDAO.findById(id); }
    public int createSupplier(Supplier s) throws SQLException { return supplierDAO.create(s); }

    public Supplier register(Supplier s, String password) throws SQLException {
        if (s.getEmail() == null || s.getEmail().trim().isEmpty()) {
            throw new SQLException("L'email est obligatoire");
        }
        if (supplierDAO.findByEmail(s.getEmail().trim()) != null) {
            throw new SQLException("Un compte existe déjà avec cet email");
        }
        s.setEmail(s.getEmail().trim());
        s.setPasswordHash(com.ecommerce.util.PasswordUtil.hash(password));
        s.setActive(true);
        if (s.getCommissionRate() == 0) s.setCommissionRate(10.0);
        int id = supplierDAO.create(s);
        if (id <= 0) throw new SQLException("Impossible de créer le compte");
        return supplierDAO.findById(id);
    }
    public void updateSupplier(Supplier s) throws SQLException { supplierDAO.update(s); }
    public void deleteSupplier(int id) throws SQLException { supplierDAO.delete(id); }
    public void updateStatus(int id, boolean active) throws SQLException { supplierDAO.updateStatus(id, active); }

    // --------------------------- PROFIL ---------------------------
    public Supplier getProfile(int id) throws SQLException { return supplierDAO.findById(id); }

    public void updateProfile(int id, Supplier data) throws SQLException, com.ecommerce.util.AppException {
        if (data.getName() == null || data.getName().trim().isEmpty()) {
            throw new com.ecommerce.util.AppException(com.ecommerce.util.ErrorCodes.VALIDATION_ERROR, 400, "Nom de boutique obligatoire");
        }
        com.ecommerce.util.RequestValidator.maxLen(data.getName().trim(), 120, "Nom de boutique");
        if (data.getEmail() != null && !data.getEmail().trim().isEmpty()) {
            com.ecommerce.util.RequestValidator.email(data.getEmail().trim(), "Email");
        }
        if (data.getPhone() != null && !data.getPhone().trim().isEmpty()) {
            com.ecommerce.util.RequestValidator.phone(data.getPhone().trim(), "Téléphone");
        }
        com.ecommerce.util.RequestValidator.maxLen(data.getCity(), 60, "Ville");
        com.ecommerce.util.RequestValidator.maxLen(data.getAddress(), 255, "Adresse");
        com.ecommerce.util.RequestValidator.maxLen(data.getDescription(), 2000, "Description");
        com.ecommerce.util.RequestValidator.maxLen(data.getContactName(), 120, "Contact");
        data.setId(id);
        supplierDAO.updateProfile(id, data);
    }

    public void updateLogo(int id, String logo) throws SQLException {
        supplierDAO.updateLogo(id, logo);
    }

    public boolean changePassword(int id, String current, String newPassword) throws SQLException, com.ecommerce.util.AppException {
        if (newPassword == null || newPassword.length() < 6) {
            throw new com.ecommerce.util.AppException(com.ecommerce.util.ErrorCodes.VALIDATION_ERROR, 400, "Mot de passe trop court (min 6)");
        }
        Supplier s = supplierDAO.findById(id);
        if (s == null || s.getPasswordHash() == null) return false;
        if (!com.ecommerce.util.PasswordUtil.verify(current, s.getPasswordHash())) return false;
        supplierDAO.updatePassword(id, com.ecommerce.util.PasswordUtil.hash(newPassword));
        return true;
    }

    public void invalidateSupplierSessions(int id) throws SQLException {
        sessionDAO.invalidateAllForSupplier(id);
    }

    // --------------------------- PRODUITS ---------------------------
    public List<Product> getProductsBySupplier(int supplierId) throws SQLException {
        return productDAO.findBySupplier(supplierId);
    }

    public Product getProductForSupplier(int supplierId, int productId) throws SQLException {
        if (!productDAO.isOwner(productId, supplierId)) return null;
        return productDAO.findById(productId);
    }

    public int createProductForSupplier(int supplierId, Product p) throws SQLException, com.ecommerce.util.AppException {
        if (p.getName() == null || p.getName().trim().isEmpty()) {
            throw new com.ecommerce.util.AppException(com.ecommerce.util.ErrorCodes.VALIDATION_ERROR, 400, "Nom du produit obligatoire");
        }
        if (p.getPrice() <= 0) {
            throw new com.ecommerce.util.AppException(com.ecommerce.util.ErrorCodes.VALIDATION_ERROR, 400, "Prix invalide");
        }
        if (p.getStock() < 0) {
            throw new com.ecommerce.util.AppException(com.ecommerce.util.ErrorCodes.VALIDATION_ERROR, 400, "Stock invalide");
        }
        if (new com.ecommerce.dao.CategoryDAO().findById(p.getCategoryId()) == null) {
            throw new com.ecommerce.util.AppException(com.ecommerce.util.ErrorCodes.VALIDATION_ERROR, 400, "Catégorie introuvable");
        }
        p.setSupplierId(supplierId);
        int id = productDAO.createSupplierProduct(p);
        if (id > 0) {
            List<String> images = p.getImages() == null ? new ArrayList<>() : p.getImages();
            productImageDAO.syncImages(id, images);
            productDAO.updateImageJson(id, images);
            notifyAdminNewProduct(id, p.getName(), supplierName(supplierId));
        }
        return id;
    }

    public boolean updateProductForSupplier(int supplierId, int productId, Product p) throws SQLException {
        if (!productDAO.isOwner(productId, supplierId)) return false;
        p.setId(productId);
        p.setSupplierId(supplierId);
        productDAO.updateSupplierProduct(p);
        List<String> images = p.getImages() == null ? new ArrayList<>() : p.getImages();
        productImageDAO.syncImages(productId, images);
        productDAO.updateImageJson(productId, images);
        // Un produit refusé modifié doit repasser en validation
        if ("REJECTED".equals(productDAO.getStatus(productId))) {
            productDAO.updateSupplierProductStatus(productId, "PENDING");
            notifyAdminNewProduct(productId, p.getName(), supplierName(supplierId));
        }
        return true;
    }

    public boolean setProductStock(int supplierId, int productId, int stock) throws SQLException {
        if (!productDAO.isOwner(productId, supplierId)) return false;
        productDAO.updateStock(productId, stock);
        return true;
    }

    public boolean setProductActive(int supplierId, int productId, boolean active) throws SQLException {
        if (!productDAO.isOwner(productId, supplierId)) return false;
        String status = productDAO.getStatus(productId);
        if (active) {
            // Un produit ne peut être visible sur la boutique qu'après validation admin (APPROVED)
            if (!"APPROVED".equals(status)) return false;
            productDAO.updateSupplierProductStatus(productId, "ACTIVE");
        } else {
            productDAO.updateSupplierProductStatus(productId, "INACTIVE");
        }
        return true;
    }

    public boolean deleteProductForSupplier(int supplierId, int productId) throws SQLException {
        if (!productDAO.isOwner(productId, supplierId)) return false;
        // Déplacement en statut refusé plutôt que suppression hard si lié à commandes
        try {
            productDAO.delete(productId);
        } catch (SQLException e) {
            // lié à des commandes : on désactive à la place
            productDAO.updateSupplierProductStatus(productId, "INACTIVE");
        }
        return true;
    }

    private String supplierName(int id) {
        try {
            Supplier s = supplierDAO.findById(id);
            return s == null ? "Fournisseur #" + id : s.getName();
        } catch (Exception e) {
            return "Fournisseur #" + id;
        }
    }

    private void notifyAdminNewProduct(int productId, String productName, String supplierName) {
        try {
            Notification n = new Notification();
            n.setTargetRole("SUPER_ADMIN");
            n.setTitle("Nouveau produit en ligne");
            n.setMessage("Le fournisseur '" + supplierName + "' a ajouté le produit '" + productName + "' (disponible sur la boutique).");
            n.setType("SUPPLIER");
            n.setLink("/products/" + productId);
            notificationDAO.create(n);
        } catch (Exception ignored) {}
    }

    // --------------------------- COMMANDES ---------------------------
    public List<Order> getOrdersBySupplier(int supplierId) throws SQLException {
        return orderDAO.findBySupplier(supplierId);
    }

    public Order getOrderForSupplier(int supplierId, int orderId) throws SQLException {
        Order o = orderDAO.findById(orderId);
        if (o == null) return null;
        if (o.getSupplierId() != supplierId) return null;
        return o;
    }

    public boolean updateOrderStatusForSupplier(int supplierId, int orderId, String status) throws SQLException, com.ecommerce.util.AppException {
        if (!com.ecommerce.util.OrderStatus.isKnown(status)) {
            throw new com.ecommerce.util.AppException(com.ecommerce.util.ErrorCodes.INVALID_ORDER_STATUS, 400, "Statut inconnu");
        }
        Order o = orderDAO.findById(orderId);
        if (o == null || o.getSupplierId() != supplierId) return false;
        if (!com.ecommerce.util.OrderStatus.canTransition(o.getStatus(), status)) {
            throw new com.ecommerce.util.AppException(com.ecommerce.util.ErrorCodes.INVALID_ORDER_STATUS, 400,
                "Transition refusée : " + o.getStatus() + " → " + status);
        }
        orderDAO.updateStatus(orderId, status);
        return true;
    }

    // --------------------------- DASHBOARD ---------------------------
    public Map<String, Object> getDashboard(int supplierId) throws SQLException {
        List<Product> products = productDAO.findBySupplier(supplierId);
        Map<String, Object> data = new LinkedHashMap<>();

        int totalProducts = products.size();
        int active = 0, pending = 0;
        for (Product p : products) {
            String st = p.getStatus() == null ? "ACTIVE" : p.getStatus();
            if ("ACTIVE".equals(st) || "APPROVED".equals(st)) active++;
            else if ("PENDING".equals(st)) pending++;
        }

        List<Order> orders = orderDAO.findBySupplier(supplierId);
        int totalOrders = orders.size();
        int pendingOrders = 0, delivered = 0, cancelled = 0;
        for (Order o : orders) {
            String st = o.getStatus() == null ? "EN_ATTENTE" : o.getStatus();
            switch (st) {
                case "EN_ATTENTE": pendingOrders++; break;
                case "LIVREE": delivered++; break;
                case "ANNULEE": cancelled++; break;
                default: break;
            }
        }
        // Revenus comptabilisés (ledger) — taux figé par commande, pas taux courant.
        long[] settled = new com.ecommerce.dao.MonetizationDAO().supplierTotals(supplierId);
        int revenueGross = (int) settled[0];
        int revenueNet = (int) settled[2];

        Supplier s = supplierDAO.findById(supplierId);
        int unread = conversationDAO.unreadCountForSupplier(supplierId);

        data.put("totalProducts", totalProducts);
        data.put("activeProducts", active);
        data.put("pendingProducts", pending);
        data.put("totalOrders", totalOrders);
        data.put("pendingOrders", pendingOrders);
        data.put("deliveredOrders", delivered);
        data.put("cancelledOrders", cancelled);
        data.put("revenueGross", revenueGross);
        data.put("revenueNet", revenueNet);
        data.put("balance", s == null ? 0 : s.getBalance());
        data.put("pendingBalance", s == null ? 0 : s.getPendingBalance());
        data.put("unreadMessages", unread);
        return data;
    }

    // --------------------------- REVENUS (ledger Phase 10) ---------------------------
    public Map<String, Object> getRevenue(int supplierId) throws SQLException {
        double commissionRate = 10.0;
        Supplier s = supplierDAO.findById(supplierId);
        if (s != null) commissionRate = s.getCommissionRate();

        // Source comptable : ledger EARNED − REVERSED (taux figé par commande).
        com.ecommerce.dao.MonetizationDAO mdao = new com.ecommerce.dao.MonetizationDAO();
        long[] totals = mdao.supplierTotals(supplierId);
        Map<String, long[]> periods = mdao.supplierPeriods(supplierId);
        long[] today = periods.getOrDefault("today", new long[3]);
        long[] week = periods.getOrDefault("week7", new long[3]);
        long[] month = periods.getOrDefault("month", new long[3]);
        long[] year = periods.getOrDefault("year", new long[3]);

        List<Order> orders = orderDAO.findBySupplier(supplierId);
        int cancelledAmount = 0;
        for (Order o : orders) {
            if ("ANNULEE".equals(o.getStatus())) cancelledAmount += o.getTotal();
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("grossToday", today[0]);
        data.put("grossWeek", week[0]);
        data.put("grossMonth", month[0]);
        data.put("grossYear", year[0]);
        data.put("grossTotal", totals[0]);
        data.put("commissionRate", commissionRate);
        data.put("commissionTotal", totals[1]);
        data.put("otherFees", 0);
        data.put("netTotal", totals[2]);
        data.put("netToday", today[2]);
        data.put("netMonth", month[2]);
        data.put("cancelledAmount", cancelledAmount);
        data.put("settledOrders", totals[3]);
        return data;
    }

    private java.time.LocalDate parseDate(String s) {
        if (s == null || s.isEmpty()) return null;
        try { return java.time.LocalDateTime.parse(s.replace(' ', 'T').substring(0, 19)).toLocalDate(); }
        catch (Exception e) { try { return java.time.LocalDate.parse(s.substring(0, 10)); } catch (Exception e2) { return null; } }
    }

    // --------------------------- STATISTIQUES ---------------------------
    public Map<String, Object> getStatistics(int supplierId) throws SQLException {
        double commissionRate = 10.0;
        try { commissionRate = supplierDAO.findById(supplierId).getCommissionRate(); } catch (Exception ignored) {}

        List<Order> orders = orderDAO.findBySupplier(supplierId);
        List<Product> products = productDAO.findBySupplier(supplierId);

        // ventes des 7 derniers jours + mensuel
        Map<java.time.LocalDate, Integer> daily = new TreeMap<>();
        Map<String, Integer> monthly = new TreeMap<>();
        int validCount = 0, cancelledCount = 0;
        int totalValid = 0;

        java.time.LocalDate now = java.time.LocalDate.now();
        for (int i = 6; i >= 0; i--) daily.put(now.minusDays(i), 0);

        for (Order o : orders) {
            String st = o.getStatus();
            if (st == null) continue;
            if ("ANNULEE".equals(st)) { cancelledCount++; continue; }
            if (!"LIVREE".equals(st)) continue;
            validCount++;
            totalValid += o.getTotal();
            java.time.LocalDate d = parseDate(o.getCreatedAt());
            if (d != null) {
                if (!d.isBefore(now.minusDays(6)) && !d.isAfter(now)) daily.put(d, daily.getOrDefault(d, 0) + o.getTotal());
                String ym = d.getYear() + "-" + String.format("%02d", d.getMonthValue());
                monthly.put(ym, monthly.getOrDefault(ym, 0) + o.getTotal());
            }
        }

        // produits les plus/moins vendus (via order_items + products du fournisseur)
        Map<Integer, Integer> soldQty = new LinkedHashMap<>();
        Map<Integer, Integer> soldRevenue = new LinkedHashMap<>();
        for (Product p : products) { soldQty.put(p.getId(), 0); soldRevenue.put(p.getId(), 0); }

        String sql = "SELECT oi.product_id, SUM(oi.qty) qty, SUM(oi.qty * oi.unit_price) rev "
                + "FROM order_items oi JOIN orders o ON o.id = oi.order_id "
                + "WHERE o.supplier_id = ? AND o.status = 'LIVREE' "
                + "GROUP BY oi.product_id";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, supplierId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    int pid = rs.getInt("product_id");
                    soldQty.put(pid, rs.getInt("qty"));
                    soldRevenue.put(pid, rs.getInt("rev"));
                }
            }
        }

        List<Map<String, Object>> topProducts = new ArrayList<>();
        List<Map<String, Object>> lowProducts = new ArrayList<>();
        for (Product p : products) {
            int qty = soldQty.getOrDefault(p.getId(), 0);
            if (qty > 0) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("id", p.getId()); m.put("name", p.getName()); m.put("qty", qty);
                m.put("revenue", soldRevenue.getOrDefault(p.getId(), 0));
                topProducts.add(m);
            }
            Map<String, Object> lm = new LinkedHashMap<>();
            lm.put("id", p.getId()); lm.put("name", p.getName()); lm.put("qty", qty);
            lowProducts.add(lm);
        }
        topProducts.sort((a, b) -> ((Integer) b.get("qty")).compareTo((Integer) a.get("qty")));
        lowProducts.sort((a, b) -> ((Integer) a.get("qty")).compareTo((Integer) b.get("qty")));
        if (topProducts.size() > 5) topProducts = topProducts.subList(0, 5);
        if (lowProducts.size() > 5) lowProducts = lowProducts.subList(0, 5);

        List<Integer> dailyValues = new ArrayList<>(daily.values());
        List<String> dailyLabels = new ArrayList<>();
        for (java.time.LocalDate d : daily.keySet()) dailyLabels.add(d.toString());

        double avgBasket = validCount == 0 ? 0 : (double) totalValid / validCount;
        double cancellationRate = (validCount + cancelledCount) == 0 ? 0
                : (double) cancelledCount / (validCount + cancelledCount) * 100;

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("dailyLabels", dailyLabels);
        data.put("dailyValues", dailyValues);
        data.put("monthly", monthly);
        data.put("totalOrders", validCount);
        data.put("cancelledOrders", cancelledCount);
        data.put("cancellationRate", Math.round(cancellationRate * 100.0) / 100.0);
        data.put("averageBasket", Math.round(avgBasket));
        data.put("commissionRate", commissionRate);
        data.put("topProducts", topProducts);
        data.put("lowProducts", lowProducts);
        return data;
    }

    // --------------------------- PAIEMENTS ---------------------------
    public Map<String, Object> getPayments(int supplierId) throws SQLException {
        Supplier s = supplierDAO.findById(supplierId);
        List<SupplierPayment> history = supplierPaymentDAO.findBySupplier(supplierId);
        int requested = supplierPaymentDAO.sumRequested(supplierId);

        int available = 0, pending = 0, paid = 0;
        for (SupplierPayment sp : history) {
            switch (sp.getStatus()) {
                case "PAID": paid += sp.getAmount(); break;
                case "PENDING": case "PROCESSING": pending += sp.getAmount(); break;
                default: break;
            }
        }
        int balance = s == null ? 0 : s.getBalance();
        int availableNow = Math.max(0, balance - requested);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("balance", balance);
        data.put("available", availableNow);
        data.put("pending", pending);
        data.put("paid", paid);
        data.put("history", history);
        return data;
    }

    public boolean requestPayment(int supplierId, int amount, String method) throws SQLException {
        Supplier s = supplierDAO.findById(supplierId);
        if (s == null || amount <= 0) return false;
        int requested = supplierPaymentDAO.sumRequested(supplierId);
        int available = Math.max(0, s.getBalance() - requested);
        if (amount > available) return false;
        SupplierPayment sp = new SupplierPayment();
        sp.setSupplierId(supplierId);
        sp.setReference("RV-" + System.currentTimeMillis());
        sp.setAmount(amount);
        sp.setMethod(method == null ? "MOBILE_MONEY" : method);
        sp.setStatus("PENDING");
        try { sp.setNotes("Demande n°" + UUID.randomUUID().toString().substring(0,8)); } catch (Exception e) { sp.setNotes("Demande"); }
        supplierPaymentDAO.create(sp);
        return true;
    }

    // --------------------------- CONVERSATIONS ---------------------------
    public List<Conversation> getConversations(int supplierId) throws SQLException {
        return conversationDAO.findBySupplier(supplierId);
    }

    public Conversation getConversationForSupplier(int supplierId, int convId) throws SQLException {
        Conversation c = conversationDAO.findByIdScoped(supplierId, convId);
        return c;
    }

    public List<SupplierMessage> getMessagesForSupplier(int supplierId, int convId) throws SQLException {
        Conversation c = conversationDAO.findByIdScoped(supplierId, convId);
        if (c == null) return null;
        return conversationDAO.findMessages(convId);
    }

    public boolean sendSupplierMessage(int supplierId, int convId, String body) throws SQLException {
        Conversation c = conversationDAO.findByIdScoped(supplierId, convId);
        if (c == null) return false;
        if (body == null || body.trim().isEmpty()) return false;
        conversationDAO.addMessage(convId, "SUPPLIER", body.trim());
        conversationDAO.updateActivity(convId);
        return true;
    }

    public boolean markConversationRead(int supplierId, int convId) throws SQLException {
        Conversation c = conversationDAO.findByIdScoped(supplierId, convId);
        if (c == null) return false;
        conversationDAO.markConversationRead(supplierId, convId);
        return true;
    }

    public int unreadConversations(int supplierId) throws SQLException {
        return conversationDAO.unreadCountForSupplier(supplierId);
    }

    // --------------------------- NOTIFICATIONS ---------------------------
    public List<Notification> getNotifications(int supplierId) throws SQLException {
        return notificationDAO.findForSupplier(supplierId);
    }

    public int getUnreadNotifications(int supplierId) throws SQLException {
        return notificationDAO.countUnreadForSupplier(supplierId);
    }

    public void markNotificationRead(int supplierId, int id) throws SQLException {
        notificationDAO.markReadForSupplier(supplierId, id);
    }

    public void markAllNotificationsRead(int supplierId) throws SQLException {
        notificationDAO.markAllReadForSupplier(supplierId);
    }

    public void notifySupplier(int supplierId, String title, String message, String type) throws SQLException {
        notificationDAO.createForSupplier(supplierId, title, message, type);
    }
}
