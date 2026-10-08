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
        // Photo facultative : sans logo, la boutique reçoit le visuel réservé.
        if (s.getLogo() == null || s.getLogo().trim().isEmpty()) {
            s.setLogo(com.ecommerce.util.AvatarDefaults.DEFAULT_SUPPLIER_LOGO);
        }
        if (s.getCommissionRate() == 0) s.setCommissionRate(1.0);
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
            // Email déjà utilisé par UNE AUTRE boutique : message clair (pas d'erreur 500 générique).
            Supplier other = supplierDAO.findByEmail(data.getEmail().trim().toLowerCase());
            if (other != null && other.getId() != id) {
                throw new com.ecommerce.util.AppException(com.ecommerce.util.ErrorCodes.CONFLICT, 409,
                    "Cet email est déjà utilisé par une autre boutique");
            }
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
        // Révocation : les sessions volées ne survivent pas au changement.
        try {
            sessionDAO.invalidateAllForSupplier(id);
        } catch (Exception ignored) {}
        // Alerte sécurité réelle (chaque changement notifié : pas de déduplication).
        try {
            new NotificationService().notifySupplier(id, "PASSWORD_CHANGED",
                "Mot de passe modifié",
                "Le mot de passe de votre compte fournisseur a été modifié. "
                    + "Si ce n'était pas vous, contactez le support.",
                "SUPPLIER", id, null, null);
        } catch (Exception ignored) {}
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
        if (p.getDeliveryPrice() < 0) {
            throw new com.ecommerce.util.AppException(com.ecommerce.util.ErrorCodes.VALIDATION_ERROR, 400, "Frais de livraison invalides");
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

    public boolean setProductStock(int supplierId, int productId, int stock) throws SQLException {        if (!productDAO.isOwner(productId, supplierId)) return false;
        productDAO.updateStock(productId, stock);
        // Seuils réels (LOW_STOCK_THRESHOLD centralisé) : notification fournisseur + admin.
        try {
            Product p = productDAO.findById(productId);
            String pname = p != null && p.getName() != null ? p.getName() : ("Produit #" + productId);
            NotificationService notifs = new NotificationService();
            if (stock <= 0) {
                notifs.notifySupplier(supplierId, "PRODUCT_OUT_OF_STOCK",
                    "Rupture de stock : " + pname,
                    "« " + pname + " » est épuisé.", "PRODUCT", productId, null,
                    "PRODUCT:" + productId + ":OUT:SUP" + supplierId);
                notifs.notifyAdmins("PRODUCT_OUT_OF_STOCK",
                    "Rupture de stock : " + pname,
                    "« " + pname + " » (fournisseur #" + supplierId + ") est épuisé.",
                    "PRODUCT", productId, null,
                    "PRODUCT:" + productId + ":OUT");
            } else if (stock <= NotificationType.LOW_STOCK_THRESHOLD) {
                notifs.notifySupplier(supplierId, "PRODUCT_LOW_STOCK",
                    "Stock faible : " + pname,
                    "Il ne reste que " + stock + " unité(s) en stock pour « " + pname + " ».",
                    "PRODUCT", productId, null,
                    "PRODUCT:" + productId + ":LOW:SUP" + supplierId);
            }
        } catch (Exception ignored) {}
        return true;
    }

    /**
     * Demande de catégorie (validation admin requise, jamais directe).
     * Vérifie : nom non vide, parent existant si précisé, pas de doublon
     * (catégorie existante au même niveau, ni demande PENDING identique).
     */
    public int requestCategory(int supplierId, String name, Integer parentId)
            throws SQLException, com.ecommerce.util.AppException {
        String clean = name == null ? "" : name.trim();
        if (clean.isEmpty() || clean.length() > 120) {
            throw new com.ecommerce.util.AppException(
                com.ecommerce.util.ErrorCodes.VALIDATION_ERROR, 400, "Nom de catégorie invalide");
        }
        com.ecommerce.dao.CategoryDAO categoryDAO = new com.ecommerce.dao.CategoryDAO();
        if (parentId != null && parentId > 0) {
            if (categoryDAO.findById(parentId) == null) {
                throw new com.ecommerce.util.AppException(
                    com.ecommerce.util.ErrorCodes.VALIDATION_ERROR, 400, "Catégorie parente introuvable");
            }
        } else {
            parentId = null;
        }
        for (com.ecommerce.model.Category c : categoryDAO.search(clean, null, null, 50, 0)) {
            Integer cp = c.getParentId();
            boolean sameLevel = (parentId == null && cp == null)
                || (parentId != null && parentId.equals(cp));
            if (sameLevel && clean.equalsIgnoreCase(c.getName().trim())) {
                throw new com.ecommerce.util.AppException(
                    com.ecommerce.util.ErrorCodes.CONFLICT, 409, "Cette catégorie existe déjà");
            }
        }
        com.ecommerce.dao.CategoryRequestDAO reqDAO = new com.ecommerce.dao.CategoryRequestDAO();
        if (reqDAO.existsPending(supplierId, clean, parentId)) {
            throw new com.ecommerce.util.AppException(
                com.ecommerce.util.ErrorCodes.CONFLICT, 409, "Demande déjà en attente de validation");
        }
        int id = reqDAO.create(supplierId, clean, parentId);
        try {
            new NotificationService().notifyAdmins("CATEGORY_REQUESTED",
                "Demande de catégorie : " + clean,
                "Fournisseur #" + supplierId + " propose « " + clean + " »"
                    + (parentId != null ? " (sous-catégorie)" : " (catégorie)"),
                "CATEGORY_REQUEST", id, null,
                "CATREQ:" + id + ":PENDING");
        } catch (Exception ignored) {}
        return id;
    }

    public java.util.List<com.ecommerce.model.CategoryRequest> getCategoryRequests(int supplierId)
            throws SQLException {
        return new com.ecommerce.dao.CategoryRequestDAO().findBySupplier(supplierId);
    }

    public boolean setProductActive(int supplierId, int productId, boolean active) throws SQLException {        if (!productDAO.isOwner(productId, supplierId)) return false;
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
            new NotificationService().notifyAdmins("PRODUCT_SUBMITTED",
                "Nouveau produit en ligne",
                "Le fournisseur '" + supplierName + "' a ajouté le produit '" + productName + "' (disponible sur la boutique).",
                "PRODUCT", productId, "/products/" + productId,
                "PRODUCT:" + productId + ":SUBMITTED");
        } catch (Exception ignored) {}
    }

    // --------------------------- COMMANDES ---------------------------
    public List<Order> getOrdersBySupplier(int supplierId) throws SQLException {
        return orderDAO.findBySupplier(supplierId);
    }

    /**
     * Liste enrichie (1 requête) : commande + statut payout + finance
     * (commission figée, net). Lecture seule, même périmètre fournisseur.
     */
    public List<Map<String, Object>> getOrdersEnriched(int supplierId) throws SQLException {
        return orderDAO.findBySupplierEnriched(supplierId);
    }

    /**
     * Finance d'une commande pour SON fournisseur : payout (ligne auto) +
     * écritures ledger (brut/commission/frais/net figés). 403 hors périmètre
     * via null (le servlet répond 404). Montants 100 % serveur.
     */
    public Map<String, Object> getOrderFinance(int supplierId, int orderId) throws SQLException {
        Order o = orderDAO.findById(orderId);
        if (o == null || o.getSupplierId() != supplierId) return null;
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("orderId", o.getId());
        out.put("orderNumber", o.getOrderNumber());
        out.put("orderStatus", o.getStatus());
        out.put("orderTotal", o.getTotal());
        SupplierPayment sp = new PayoutDAO().findByOrderId(orderId);
        if (sp != null) {
            Map<String, Object> p = new LinkedHashMap<>();
            p.put("id", sp.getId());
            p.put("reference", sp.getReference());
            p.put("payoutId", sp.getPayoutId());
            p.put("provider", sp.getProvider());
            p.put("amount", sp.getAmount());
            p.put("grossAmount", sp.getGrossAmount());
            p.put("commission", sp.getCommission());
            p.put("pawapayFee", sp.getPawapayFee());
            p.put("status", sp.getStatus());
            p.put("failureReason", sp.getFailureReason());
            p.put("createdAt", sp.getCreatedAt());
            out.put("payout", p);
        } else {
            out.put("payout", null);
        }
        List<Map<String, Object>> ledger = new ArrayList<>();
        for (com.ecommerce.model.LedgerEntry e : new MonetizationDAO().findByOrder(orderId)) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("reference", e.getReference());
            m.put("kind", e.getKind());
            m.put("gross", e.getGross());
            m.put("commission", e.getCommission());
            m.put("commissionBps", e.getCommissionBps());
            m.put("fixedFee", e.getFixedFee());
            m.put("supplierEarnings", e.getSupplierEarnings());
            m.put("status", e.getStatus());
            ledger.add(m);
        }
        out.put("ledger", ledger);
        return out;
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
        // Workflow v2 : l'interface n'avance plus les statuts un par un.
        // EN_LIVRAISON = via [EN COURS DE LIVRAISON] (start-delivery),
        // LIVREE = via [VALIDER LA LIVRAISON] (validate-delivery).
        if ("EN_LIVRAISON".equals(status) || "LIVREE".equals(status)) {
            throw new com.ecommerce.util.AppException(com.ecommerce.util.ErrorCodes.VALIDATION_ERROR, 400,
                "Utilisez le workflow de livraison (EN COURS DE LIVRAISON / VALIDER LA LIVRAISON)");
        }
        Order o = orderDAO.findById(orderId);
        if (o == null || o.getSupplierId() != supplierId) return false;
        if (!com.ecommerce.util.OrderStatus.canTransition(o.getStatus(), status)) {
            throw new com.ecommerce.util.AppException(com.ecommerce.util.ErrorCodes.INVALID_ORDER_STATUS, 400,
                "Transition refusée : " + o.getStatus() + " → " + status);
        }
        boolean toShipping = "EN_LIVRAISON".equals(status) && !"EN_LIVRAISON".equals(o.getStatus());
        if (toShipping) {
            // Le bouton « Commande reçue » côté client exige un n° de suivi :
            // l'expédition est créée ici (avant le changement de statut, car
            // createShipment n'accepte que CONFIRMEE/EN_PREPARATION).
            // Sans elle, le bouton ne pourrait pas fonctionner.
            try {
                new com.ecommerce.service.ShipmentService()
                    .createShipment(orderId, supplierId, "SUPPLIER");
            } catch (Exception ignored) {}
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
        double commissionRate = 1.0;
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
        double commissionRate = 1.0;
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
                case "PAID": case "COMPLETED": paid += sp.getAmount(); break;
                case "PENDING": case "PROCESSING": case "ACCEPTED": case "ENQUEUED":
                    pending += sp.getAmount(); break;
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

    public boolean sendSupplierMessage(int supplierId, int convId, String body)
            throws SQLException, com.ecommerce.util.AppException {
        Conversation c = conversationDAO.findByIdScoped(supplierId, convId);
        if (c == null) return false;
        if (body == null || body.trim().isEmpty()) return false;
        if (body.trim().length() > ConversationService.MESSAGE_MAX) return false;
        // Même garde anti-spam que côté client (10/min/conversation).
        if (conversationDAO.countRecentFrom(convId, "SUPPLIER", 1)
                >= ConversationService.MESSAGE_PER_MINUTE) {
            throw new com.ecommerce.util.AppException(
                com.ecommerce.util.ErrorCodes.RATE_LIMITED, 429, "Ralentissez : trop de messages");
        }
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

    public List<Notification> getNotificationsPaged(int supplierId, String category, Boolean unreadOnly, int limit, int offset) throws SQLException {
        return notificationDAO.findForSupplierPaged(supplierId, category, unreadOnly, limit, offset);
    }

    public List<Notification> getNotificationsPaged(int supplierId, String category, Boolean unreadOnly, Boolean archivedOnly, int limit, int offset) throws SQLException {
        return notificationDAO.findForSupplierPaged(supplierId, category, unreadOnly, archivedOnly, limit, offset);
    }

    public int countNotifications(int supplierId, String category, Boolean unreadOnly) throws SQLException {
        return notificationDAO.countForSupplierPaged(supplierId, category, unreadOnly);
    }

    public int countNotifications(int supplierId, String category, Boolean unreadOnly, Boolean archivedOnly) throws SQLException {
        return notificationDAO.countForSupplierPaged(supplierId, category, unreadOnly, archivedOnly);
    }

    public boolean deleteNotification(int supplierId, int id) throws SQLException {
        return notificationDAO.deleteForSupplier(supplierId, id);
    }

    public boolean archiveNotification(int supplierId, int id, boolean archived) throws SQLException {
        return notificationDAO.archiveForSupplier(supplierId, id, archived);
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
