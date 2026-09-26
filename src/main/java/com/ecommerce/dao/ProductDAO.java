package com.ecommerce.dao;

import com.ecommerce.config.DBConnection;
import com.ecommerce.model.Product;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class ProductDAO {

    private Product mapRow(ResultSet rs) throws SQLException {
        Product p = new Product();
        p.setId(rs.getInt("id"));
        p.setName(rs.getString("name"));
        p.setSlug(rs.getString("slug"));
        p.setReference(rs.getString("reference"));
        p.setBrand(rs.getString("brand"));
        p.setCategoryId(rs.getInt("category_id"));
        p.setPrice(rs.getInt("price"));
        int oldPrice = rs.getInt("old_price");
        p.setOldPrice(rs.wasNull() ? null : oldPrice);
        p.setDiscount(rs.getInt("discount"));
        double rating = rs.getDouble("rating");
        p.setRating(rs.wasNull() ? 0.0 : rating);
        int reviews = rs.getInt("reviews");
        p.setReviews(rs.wasNull() ? 0 : reviews);
        p.setBadge(rs.getString("badge"));
        p.setDescription(rs.getString("description"));
        p.setImages(Product.parseJsonArray(rs.getString("images")));
        p.setSizes(Product.parseJsonArray(rs.getString("sizes")));
        p.setColors(Product.parseJsonArray(rs.getString("colors")));
        p.setStock(rs.getInt("stock"));
        int supId = rs.getInt("supplier_id");
        p.setSupplierId(rs.wasNull() ? 0 : supId);
        try { p.setStatus(rs.getString("status")); } catch (SQLException e) { p.setStatus(null); }
        try {
            p.setCategoryName(rs.getString("category_name"));
        } catch (SQLException e) {
            p.setCategoryName("");
        }
        try {
            p.setSupplierName(rs.getString("supplier_name"));
            p.setSupplierWhatsapp(rs.getString("supplier_phone"));
        } catch (SQLException e) {
            p.setSupplierName("");
            p.setSupplierWhatsapp("");
        }
        try {
            p.setPromoStart(rs.getString("promo_start"));
            p.setPromoEnd(rs.getString("promo_end"));
        } catch (SQLException e) {
            p.setPromoStart(null);
            p.setPromoEnd(null);
        }
        return p;
    }

    private String buildBaseQuery() {
        return "SELECT p.*, c.name AS category_name, s.name AS supplier_name, s.phone AS supplier_phone "
             + "FROM products p "
             + "LEFT JOIN categories c ON p.category_id = c.id "
             + "LEFT JOIN suppliers s ON p.supplier_id = s.id ";
    }

    private String buildBaseQueryPublic() {
        // Accueil : TOUS les produits, toutes catégories, sans filtre de statut.
        // (Le filtre status cassait l'accueil quand la colonne n'existe pas encore en base.)
        return "SELECT p.*, c.name AS category_name, s.name AS supplier_name, s.phone AS supplier_phone "
             + "FROM products p "
             + "LEFT JOIN categories c ON p.category_id = c.id "
             + "LEFT JOIN suppliers s ON p.supplier_id = s.id "
             + "WHERE 1=1 ";
    }

    public List<Product> findAll() throws SQLException {
        List<Product> list = new ArrayList<>();
        String sql = buildBaseQuery() + "ORDER BY p.id LIMIT 2000";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) list.add(mapRow(rs));
        }
        enrichWithImages(list);
        return list;
    }

    public List<Product> findAllPublic() throws SQLException {
        List<Product> list = new ArrayList<>();
        String sql = buildBaseQueryPublic() + "ORDER BY p.id LIMIT 2000";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) list.add(mapRow(rs));
        }
        enrichWithImages(list);
        return list;
    }

    public Product findById(int id) throws SQLException {
        String sql = buildBaseQuery() + "WHERE p.id = ?";
        Product p = null;
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) p = mapRow(rs);
            }
        }
        if (p != null) {
            List<Product> single = new ArrayList<>();
            single.add(p);
            enrichWithImages(single);
        }
        return p;
    }

    private void enrichWithImages(List<Product> products) throws SQLException {
        if (products.isEmpty()) return;
        // Placeholders liés (jamais de concaténation, même d'entiers).
        StringBuilder in = new StringBuilder();
        for (int i = 0; i < products.size(); i++) {
            if (i > 0) in.append(",");
            in.append("?");
        }
        String sql = "SELECT product_id, image_url, is_primary, sort_order FROM product_images WHERE product_id IN (" + in + ") ORDER BY is_primary DESC, sort_order ASC";
        java.util.Map<Integer, java.util.List<String>> imageMap = new java.util.LinkedHashMap<>();
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            for (int i = 0; i < products.size(); i++) ps.setInt(i + 1, products.get(i).getId());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    int pid = rs.getInt("product_id");
                    String url = rs.getString("image_url");
                    if (url != null && !url.isEmpty()) {
                        imageMap.computeIfAbsent(pid, k -> new ArrayList<>()).add(url);
                    }
                }
            }
        }
        for (Product p : products) {
            java.util.List<String> extraImages = imageMap.get(p.getId());
            if (extraImages != null && !extraImages.isEmpty()) {
                java.util.List<String> merged = new ArrayList<>(extraImages);
                for (String img : p.getImages()) {
                    if (!merged.contains(img)) merged.add(img);
                }
                p.setImages(merged);
            }
        }
    }

    public List<Product> findByCategory(String category) throws SQLException {
        List<Product> list = new ArrayList<>();
        String sql = buildBaseQueryPublic() + "AND c.name = ? ORDER BY p.id";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, category);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) list.add(mapRow(rs));
            }
        }
        enrichWithImages(list);
        return list;
    }

    /**
     * Produits publics d'une liste de catégories (une catégorie + ses sous-catégories).
     * Placeholders générés dynamiquement — toujours via PreparedStatement.
     */
    public List<Product> findPublicByCategoryIds(List<Integer> categoryIds) throws SQLException {
        List<Product> list = new ArrayList<>();
        if (categoryIds == null || categoryIds.isEmpty()) return list;
        StringBuilder in = new StringBuilder();
        for (int i = 0; i < categoryIds.size(); i++) {
            if (i > 0) in.append(",");
            in.append("?");
        }
        String sql = buildBaseQueryPublic() + "AND p.category_id IN (" + in + ") ORDER BY p.id";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            for (int i = 0; i < categoryIds.size(); i++) {
                ps.setInt(i + 1, categoryIds.get(i));
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) list.add(mapRow(rs));
            }
        }
        enrichWithImages(list);
        return list;
    }

    public List<Product> findPopular(int limit) throws SQLException {
        List<Product> list = new ArrayList<>();
        String sql = buildBaseQueryPublic() + "ORDER BY p.reviews DESC LIMIT ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) list.add(mapRow(rs));
            }
        }
        enrichWithImages(list);
        return list;
    }

    public List<Product> findNew(int limit) throws SQLException {
        List<Product> list = new ArrayList<>();
        String sql = buildBaseQueryPublic() + "AND p.badge = 'Nouveau' ORDER BY p.created_at DESC LIMIT ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) list.add(mapRow(rs));
            }
        }
        enrichWithImages(list);
        return list;
    }

    public List<Product> findPromo(int limit) throws SQLException {
        List<Product> list = new ArrayList<>();
        String sql = buildBaseQueryPublic() + "AND p.discount >= 15 ORDER BY p.discount DESC LIMIT ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) list.add(mapRow(rs));
            }
        }
        enrichWithImages(list);
        return list;
    }

    public List<Product> findTopRated(int limit) throws SQLException {
        List<Product> list = new ArrayList<>();
        String sql = buildBaseQueryPublic() + "ORDER BY p.rating DESC, p.reviews DESC LIMIT ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) list.add(mapRow(rs));
            }
        }
        enrichWithImages(list);
        return list;
    }

    public List<Product> findRecommended(int limit) throws SQLException {
        List<Product> list = new ArrayList<>();
        String sql = buildBaseQueryPublic() + "ORDER BY RAND() LIMIT ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) list.add(mapRow(rs));
            }
        }
        enrichWithImages(list);
        return list;
    }

    public List<Product> search(String query) throws SQLException {
        List<Product> list = new ArrayList<>();
        String sql = buildBaseQueryPublic()
            + "AND (p.name LIKE ? OR p.brand LIKE ? OR p.description LIKE ? OR c.name LIKE ? OR p.reference LIKE ? OR p.slug LIKE ?) "
            + "ORDER BY p.reviews DESC";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            String q = "%" + query + "%";
            ps.setString(1, q);
            ps.setString(2, q);
            ps.setString(3, q);
            ps.setString(4, q);
            ps.setString(5, q);
            ps.setString(6, q);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) list.add(mapRow(rs));
            }
        }
        enrichWithImages(list);
        return list;
    }

    public List<Product> filter(String category, String brand, String badge,
                                 Integer minPrice, Integer maxPrice,
                                 Boolean inStock, String sort,
                                 int page, int limit) throws SQLException {
        StringBuilder where = new StringBuilder(" AND 1=1 ");
        List<Object> params = new ArrayList<>();

        if (category != null && !category.isEmpty()) {
            where.append(" AND c.name = ? ");
            params.add(category);
        }
        if (brand != null && !brand.isEmpty()) {
            where.append(" AND p.brand = ? ");
            params.add(brand);
        }
        if (badge != null && !badge.isEmpty()) {
            where.append(" AND p.badge = ? ");
            params.add(badge);
        }
        if (minPrice != null) {
            where.append(" AND p.price >= ? ");
            params.add(minPrice);
        }
        if (maxPrice != null) {
            where.append(" AND p.price <= ? ");
            params.add(maxPrice);
        }
        if (inStock != null && inStock) {
            where.append(" AND p.stock > 0 ");
        }

        String orderBy;
        if ("price-asc".equals(sort)) orderBy = " ORDER BY p.price ASC ";
        else if ("price-desc".equals(sort)) orderBy = " ORDER BY p.price DESC ";
        else if ("newest".equals(sort)) orderBy = " ORDER BY p.badge = 'Nouveau' DESC, p.created_at DESC ";
        else if ("best-rated".equals(sort)) orderBy = " ORDER BY p.rating DESC, p.reviews DESC ";
        else if ("best-sales".equals(sort)) orderBy = " ORDER BY p.reviews DESC ";
        else orderBy = " ORDER BY p.reviews DESC ";

        int offset = (page - 1) * limit;
        String sql = buildBaseQueryPublic() + where + orderBy + "LIMIT ? OFFSET ?";

        List<Product> list = new ArrayList<>();
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            int idx = 1;
            for (Object param : params) {
                if (param instanceof String) ps.setString(idx++, (String) param);
                else if (param instanceof Integer) ps.setInt(idx++, (Integer) param);
            }
            ps.setInt(idx++, limit);
            ps.setInt(idx, offset);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) list.add(mapRow(rs));
            }
        }
        enrichWithImages(list);
        return list;
    }

    public int countFiltered(String category, String brand, String badge,
                              Integer minPrice, Integer maxPrice,
                              Boolean inStock) throws SQLException {
        // Compteur aligné sur l'accueil : tous les produits (aucun filtre statut/catégorie imposé).
        StringBuilder where = new StringBuilder(" WHERE 1=1 ");
        List<Object> params = new ArrayList<>();

        if (category != null && !category.isEmpty()) {
            where.append(" AND c.name = ? ");
            params.add(category);
        }
        if (brand != null && !brand.isEmpty()) {
            where.append(" AND p.brand = ? ");
            params.add(brand);
        }
        if (badge != null && !badge.isEmpty()) {
            where.append(" AND p.badge = ? ");
            params.add(badge);
        }
        if (minPrice != null) {
            where.append(" AND p.price >= ? ");
            params.add(minPrice);
        }
        if (maxPrice != null) {
            where.append(" AND p.price <= ? ");
            params.add(maxPrice);
        }
        if (inStock != null && inStock) {
            where.append(" AND p.stock > 0 ");
        }

        String sql = "SELECT COUNT(*) FROM products p LEFT JOIN categories c ON p.category_id = c.id " + where;
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            int idx = 1;
            for (Object param : params) {
                if (param instanceof String) ps.setString(idx++, (String) param);
                else if (param instanceof Integer) ps.setInt(idx++, (Integer) param);
            }
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt(1);
            }
        }
        return 0;
    }

    public void updateStock(int productId, int newStock) throws SQLException {
        String sql = "UPDATE products SET stock = ? WHERE id = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, Math.max(0, newStock));
            ps.setInt(2, productId);
            ps.executeUpdate();
        }
    }

    public boolean decrementStock(int productId, int qty) throws SQLException {
        String sql = "UPDATE products SET stock = stock - ? WHERE id = ? AND stock >= ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, qty);
            ps.setInt(2, productId);
            ps.setInt(3, qty);
            return ps.executeUpdate() > 0;
        }
    }

    public List<Product> findLowStock(int threshold) throws SQLException {
        List<Product> list = new ArrayList<>();
        String sql = buildBaseQuery() + "WHERE p.stock <= ? AND p.stock > 0 ORDER BY p.stock ASC";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, threshold);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) list.add(mapRow(rs));
            }
        }
        return list;
    }

    public List<Product> findOutOfStock() throws SQLException {
        List<Product> list = new ArrayList<>();
        String sql = buildBaseQuery() + "WHERE p.stock = 0 ORDER BY p.name";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) list.add(mapRow(rs));
        }
        return list;
    }

    public int countAll() throws SQLException {
        String sql = "SELECT COUNT(*) FROM products";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            if (rs.next()) return rs.getInt(1);
        }
        return 0;
    }

    public int create(Product p) throws SQLException {
        String slug = toSlug(p.getName());
        String sql = "INSERT INTO products (name, slug, reference, brand, category_id, price, old_price, discount, badge, description, images, sizes, colors, stock, supplier_id, promo_start, promo_end) "
                   + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, p.getName());
            ps.setString(2, slug);
            ps.setString(3, p.getReference());
            ps.setString(4, p.getBrand());
            ps.setInt(5, p.getCategoryId());
            ps.setInt(6, p.getPrice());
            if (p.getOldPrice() != null) ps.setInt(7, p.getOldPrice()); else ps.setNull(7, Types.INTEGER);
            ps.setInt(8, p.getDiscount());
            ps.setString(9, p.getBadge());
            ps.setString(10, p.getDescription());
            ps.setString(11, toJsonArray(p.getImages()));
            ps.setString(12, toJsonArray(p.getSizes()));
            ps.setString(13, toJsonArray(p.getColors()));
            ps.setInt(14, p.getStock());
            if (p.getSupplierId() > 0) ps.setInt(15, p.getSupplierId()); else ps.setNull(15, Types.INTEGER);
            if (p.getPromoStart() != null) ps.setString(16, p.getPromoStart()); else ps.setNull(16, Types.DATE);
            if (p.getPromoEnd() != null) ps.setString(17, p.getPromoEnd()); else ps.setNull(17, Types.DATE);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) return keys.getInt(1);
            }
        }
        return -1;
    }

    public void update(Product p) throws SQLException {
        String sql = "UPDATE products SET name=?, slug=?, reference=?, brand=?, category_id=?, price=?, old_price=?, discount=?, badge=?, description=?, images=?, sizes=?, colors=?, stock=?, supplier_id=?, promo_start=?, promo_end=? WHERE id=?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, p.getName());
            ps.setString(2, toSlug(p.getName()));
            ps.setString(3, p.getReference());
            ps.setString(4, p.getBrand());
            ps.setInt(5, p.getCategoryId());
            ps.setInt(6, p.getPrice());
            if (p.getOldPrice() != null) ps.setInt(7, p.getOldPrice()); else ps.setNull(7, Types.INTEGER);
            ps.setInt(8, p.getDiscount());
            ps.setString(9, p.getBadge());
            ps.setString(10, p.getDescription());
            ps.setString(11, toJsonArray(p.getImages()));
            ps.setString(12, toJsonArray(p.getSizes()));
            ps.setString(13, toJsonArray(p.getColors()));
            ps.setInt(14, p.getStock());
            if (p.getSupplierId() > 0) ps.setInt(15, p.getSupplierId()); else ps.setNull(15, Types.INTEGER);
            if (p.getPromoStart() != null) ps.setString(16, p.getPromoStart()); else ps.setNull(16, Types.DATE);
            if (p.getPromoEnd() != null) ps.setString(17, p.getPromoEnd()); else ps.setNull(17, Types.DATE);
            ps.setInt(18, p.getId());
            ps.executeUpdate();
        }
    }

    public void delete(int id) throws SQLException {
        try (Connection conn = DBConnection.getConnection()) {
            // Vérifier si le produit existe
            try (PreparedStatement check = conn.prepareStatement("SELECT id FROM products WHERE id = ?")) {
                check.setInt(1, id);
                ResultSet rs = check.executeQuery();
                if (!rs.next()) {
                    throw new SQLException("Produit introuvable (id=" + id + ")");
                }
            }

            // Vérifier si le produit a des commandes liées
            try (PreparedStatement orderCheck = conn.prepareStatement(
                    "SELECT COUNT(*) FROM order_items WHERE product_id = ?")) {
                orderCheck.setInt(1, id);
                ResultSet rs = orderCheck.executeQuery();
                if (rs.next() && rs.getInt(1) > 0) {
                    throw new SQLException("Ce produit ne peut pas être supprimé car il est lié à des commandes existantes.");
                }
            }

            // Supprimer les images associées (sécurité même si CASCADE existe)
            try (PreparedStatement delImages = conn.prepareStatement("DELETE FROM product_images WHERE product_id = ?")) {
                delImages.setInt(1, id);
                delImages.executeUpdate();
            }

            // Supprimer le produit
            try (PreparedStatement del = conn.prepareStatement("DELETE FROM products WHERE id = ?")) {
                del.setInt(1, id);
                del.executeUpdate();
            }
        }
    }

    public boolean isOwner(int productId, int supplierId) throws SQLException {
        String sql = "SELECT id FROM products WHERE id = ? AND supplier_id = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, productId);
            ps.setInt(2, supplierId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    public List<Product> findBySupplier(int supplierId) throws SQLException {
        List<Product> list = new ArrayList<>();
        String sql = buildBaseQuery() + "WHERE p.supplier_id = ? ORDER BY p.id DESC";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, supplierId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) list.add(mapRow(rs));
            }
        }
        enrichWithImages(list);
        return list;
    }

    public int createSupplierProduct(Product p) throws SQLException {
        String slug = toSlug(p.getName());
        String sql = "INSERT INTO products (name, slug, reference, brand, category_id, price, old_price, discount, badge, description, images, sizes, colors, stock, supplier_id, status, promo_start, promo_end) "
                   + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'ACTIVE', ?, ?)";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, p.getName());
            ps.setString(2, slug);
            ps.setString(3, p.getReference());
            ps.setString(4, p.getBrand());
            ps.setInt(5, p.getCategoryId());
            ps.setInt(6, p.getPrice());
            if (p.getOldPrice() != null) ps.setInt(7, p.getOldPrice()); else ps.setNull(7, Types.INTEGER);
            ps.setInt(8, p.getDiscount());
            ps.setString(9, p.getBadge());
            ps.setString(10, p.getDescription());
            ps.setString(11, toJsonArray(p.getImages()));
            ps.setString(12, toJsonArray(p.getSizes()));
            ps.setString(13, toJsonArray(p.getColors()));
            ps.setInt(14, p.getStock());
            ps.setInt(15, p.getSupplierId());
            if (p.getPromoStart() != null) ps.setString(16, p.getPromoStart()); else ps.setNull(16, Types.DATE);
            if (p.getPromoEnd() != null) ps.setString(17, p.getPromoEnd()); else ps.setNull(17, Types.DATE);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) return keys.getInt(1);
            }
        }
        return -1;
    }

    // Mise à jour par le fournisseur : ne modifie JAMAIS supplier_id ni status
    public void updateSupplierProduct(Product p) throws SQLException {
        String sql = "UPDATE products SET name=?, slug=?, reference=?, brand=?, category_id=?, price=?, old_price=?, discount=?, badge=?, description=?, images=?, sizes=?, colors=?, stock=?, promo_start=?, promo_end=? WHERE id=? AND supplier_id=?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, p.getName());
            ps.setString(2, toSlug(p.getName()));
            ps.setString(3, p.getReference());
            ps.setString(4, p.getBrand());
            ps.setInt(5, p.getCategoryId());
            ps.setInt(6, p.getPrice());
            if (p.getOldPrice() != null) ps.setInt(7, p.getOldPrice()); else ps.setNull(7, Types.INTEGER);
            ps.setInt(8, p.getDiscount());
            ps.setString(9, p.getBadge());
            ps.setString(10, p.getDescription());
            ps.setString(11, toJsonArray(p.getImages()));
            ps.setString(12, toJsonArray(p.getSizes()));
            ps.setString(13, toJsonArray(p.getColors()));
            ps.setInt(14, p.getStock());
            if (p.getPromoStart() != null) ps.setString(15, p.getPromoStart()); else ps.setNull(15, Types.DATE);
            if (p.getPromoEnd() != null) ps.setString(16, p.getPromoEnd()); else ps.setNull(16, Types.DATE);
            ps.setInt(17, p.getId());
            ps.setInt(18, p.getSupplierId());
            ps.executeUpdate();
        }
    }

    // Modifier le statut (par le fournisseur ou l'admin)
    public void updateSupplierProductStatus(int id, String status) throws SQLException {
        String sql = "UPDATE products SET status = ? WHERE id = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, status);
            ps.setInt(2, id);
            ps.executeUpdate();
        }
    }

    public String getStatus(int id) throws SQLException {
        String sql = "SELECT status FROM products WHERE id = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getString("status");
            }
        }
        return null;
    }

    public void updateImageJson(int id, java.util.List<String> images) throws SQLException {
        String sql = "UPDATE products SET images = ? WHERE id = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, toJsonArray(images));
            ps.setInt(2, id);
            ps.executeUpdate();
        }
    }

    private String toSlug(String name) {
        if (name == null) return "";
        String slug = name.toLowerCase()
            .replaceAll("[àáâãäå]", "a").replaceAll("[èéêë]", "e")
            .replaceAll("[ìíîï]", "i").replaceAll("[òóôõö]", "o")
            .replaceAll("[ùúûü]", "u").replaceAll("[ýÿ]", "y")
            .replaceAll("[ç]", "c").replaceAll("[ñ]", "n")
            .replaceAll("[^a-z0-9\\s-]", "").replaceAll("[\\s-]+", "-")
            .replaceAll("^-|-$", "");
        return slug;
    }

    private String toJsonArray(java.util.List<String> list) {
        if (list == null || list.isEmpty()) return "[]";
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < list.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append("\"").append(list.get(i).replace("\\", "\\\\").replace("\"", "\\\"")).append("\"");
        }
        sb.append("]");
        return sb.toString();
    }
}
