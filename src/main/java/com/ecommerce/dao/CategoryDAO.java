package com.ecommerce.dao;

import com.ecommerce.config.DBConnection;
import com.ecommerce.model.Category;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Accès données des catégories hiérarchiques.
 * Exclusivement PreparedStatement. Ressources fermées via try-with-resources.
 */
public class CategoryDAO {

    private static final String ORDER = " ORDER BY c.display_order ASC, c.name ASC";

    private Category mapRow(ResultSet rs) throws SQLException {
        Category c = new Category();
        c.setId(rs.getInt("id"));
        c.setName(rs.getString("name"));
        c.setSlug(rs.getString("slug"));
        int parentId = rs.getInt("parent_id");
        c.setParentId(rs.wasNull() ? null : parentId);
        try { c.setDescription(rs.getString("description")); } catch (SQLException ignored) {}
        try { c.setIcon(rs.getString("icon")); } catch (SQLException ignored) {}
        try { c.setImage(rs.getString("image")); } catch (SQLException ignored) {}
        try { c.setActive(rs.getBoolean("is_active")); } catch (SQLException ignored) { c.setActive(true); }
        try { c.setDisplayOrder(rs.getInt("display_order")); } catch (SQLException ignored) {}
        try { c.setCreatedAt(rs.getString("created_at")); } catch (SQLException ignored) {}
        try { c.setUpdatedAt(rs.getString("updated_at")); } catch (SQLException ignored) {}
        try { c.setCount(rs.getInt("product_count")); } catch (SQLException ignored) {}
        return c;
    }

    // ---------------- CREATE ----------------

    public int create(Category cat) throws SQLException {
        String sql = "INSERT INTO categories (name, slug, parent_id, description, icon, image, is_active, display_order) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, cat.getName());
            ps.setString(2, cat.getSlug());
            if (cat.getParentId() == null) ps.setNull(3, Types.INTEGER);
            else ps.setInt(3, cat.getParentId());
            ps.setString(4, cat.getDescription());
            ps.setString(5, cat.getIcon());
            ps.setString(6, cat.getImage());
            ps.setBoolean(7, cat.isActive());
            ps.setInt(8, cat.getDisplayOrder());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) return keys.getInt(1);
            }
            return 0;
        }
    }

    // ---------------- READ ----------------

    public Category findById(int id) throws SQLException {
        String sql = "SELECT c.*, 0 AS product_count FROM categories c WHERE c.id = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return mapRow(rs);
            }
        }
        return null;
    }

    public Category findBySlug(String slug) throws SQLException {
        String sql = "SELECT c.*, 0 AS product_count FROM categories c WHERE c.slug = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, slug);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return mapRow(rs);
            }
        }
        return null;
    }

    public List<Category> findAll() throws SQLException {
        List<Category> list = new ArrayList<>();
        String sql = "SELECT c.*, 0 AS product_count FROM categories c" + ORDER;
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) list.add(mapRow(rs));
        }
        return list;
    }

    public List<Category> findAllWithCount() throws SQLException {
        List<Category> list = new ArrayList<>();
        String sql = "SELECT c.*, COUNT(p.id) AS product_count "
                + "FROM categories c "
                + "LEFT JOIN products p ON c.id = p.category_id "
                + "GROUP BY c.id" + ORDER.replace("c.", "c.");
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) list.add(mapRow(rs));
        }
        return list;
    }

    /** Racines uniquement (parent_id IS NULL). */
    public List<Category> findRootCategories() throws SQLException {
        return findChildren(null);
    }

    /** Enfants directs d'un parent (null = racines). */
    public List<Category> findChildren(Integer parentId) throws SQLException {
        List<Category> list = new ArrayList<>();
        String sql = parentId == null
                ? "SELECT c.*, 0 AS product_count FROM categories c WHERE c.parent_id IS NULL" + ORDER
                : "SELECT c.*, 0 AS product_count FROM categories c WHERE c.parent_id = ?" + ORDER;
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            if (parentId != null) ps.setInt(1, parentId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) list.add(mapRow(rs));
            }
        }
        return list;
    }

    /** Catégories actives (menu public), ordonnées. */
    public List<Category> findActive() throws SQLException {
        List<Category> list = new ArrayList<>();
        String sql = "SELECT c.*, 0 AS product_count FROM categories c WHERE c.is_active = 1" + ORDER;
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) list.add(mapRow(rs));
        }
        return list;
    }

    public List<Category> findActiveRoots() throws SQLException {
        List<Category> list = new ArrayList<>();
        String sql = "SELECT c.*, 0 AS product_count FROM categories c "
                + "WHERE c.is_active = 1 AND c.parent_id IS NULL" + ORDER;
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) list.add(mapRow(rs));
        }
        return list;
    }

    // ---------------- SEARCH / FILTER / PAGINATION ----------------

    /**
     * Recherche + filtrage + pagination.
     * @param query texte libre (nom, slug, description) ou null
     * @param parentId filtre parent (null = sans filtre ; utiliser -1 pour racines uniquement)
     * @param active filtre statut (null = tous)
     */
    public List<Category> search(String query, Integer parentId, Boolean active, int limit, int offset) throws SQLException {
        List<Category> list = new ArrayList<>();
        StringBuilder sql = new StringBuilder(
                "SELECT c.*, COUNT(p.id) AS product_count FROM categories c "
                + "LEFT JOIN products p ON c.id = p.category_id WHERE 1=1");
        List<Object> params = new ArrayList<>();
        if (query != null && !query.trim().isEmpty()) {
            sql.append(" AND (c.name LIKE ? OR c.slug LIKE ? OR c.description LIKE ?)");
            String like = "%" + query.trim() + "%";
            params.add(like); params.add(like); params.add(like);
        }
        if (parentId != null) {
            if (parentId == -1) sql.append(" AND c.parent_id IS NULL");
            else { sql.append(" AND c.parent_id = ?"); params.add(parentId); }
        }
        if (active != null) {
            sql.append(" AND c.is_active = ?");
            params.add(active ? 1 : 0);
        }
        sql.append(" GROUP BY c.id").append(ORDER).append(" LIMIT ? OFFSET ?");
        params.add(limit); params.add(offset);
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql.toString())) {
            bindParams(ps, params);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) list.add(mapRow(rs));
            }
        }
        return list;
    }

    public int countSearch(String query, Integer parentId, Boolean active) throws SQLException {
        StringBuilder sql = new StringBuilder("SELECT COUNT(*) FROM categories c WHERE 1=1");
        List<Object> params = new ArrayList<>();
        if (query != null && !query.trim().isEmpty()) {
            sql.append(" AND (c.name LIKE ? OR c.slug LIKE ? OR c.description LIKE ?)");
            String like = "%" + query.trim() + "%";
            params.add(like); params.add(like); params.add(like);
        }
        if (parentId != null) {
            if (parentId == -1) sql.append(" AND c.parent_id IS NULL");
            else { sql.append(" AND c.parent_id = ?"); params.add(parentId); }
        }
        if (active != null) {
            sql.append(" AND c.is_active = ?");
            params.add(active ? 1 : 0);
        }
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql.toString())) {
            bindParams(ps, params);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt(1);
            }
        }
        return 0;
    }

    private void bindParams(PreparedStatement ps, List<Object> params) throws SQLException {
        for (int i = 0; i < params.size(); i++) {
            Object p = params.get(i);
            if (p instanceof Integer) ps.setInt(i + 1, (Integer) p);
            else if (p instanceof Boolean) ps.setBoolean(i + 1, (Boolean) p);
            else ps.setString(i + 1, String.valueOf(p));
        }
    }

    // ---------------- UPDATE ----------------

    public void update(Category cat) throws SQLException {
        String sql = "UPDATE categories SET name=?, slug=?, parent_id=?, description=?, icon=?, image=?, is_active=?, display_order=? WHERE id=?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, cat.getName());
            ps.setString(2, cat.getSlug());
            if (cat.getParentId() == null) ps.setNull(3, Types.INTEGER);
            else ps.setInt(3, cat.getParentId());
            ps.setString(4, cat.getDescription());
            ps.setString(5, cat.getIcon());
            ps.setString(6, cat.getImage());
            ps.setBoolean(7, cat.isActive());
            ps.setInt(8, cat.getDisplayOrder());
            ps.setInt(9, cat.getId());
            ps.executeUpdate();
        }
    }

    public void setActive(int id, boolean active) throws SQLException {
        String sql = "UPDATE categories SET is_active = ? WHERE id = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setBoolean(1, active);
            ps.setInt(2, id);
            ps.executeUpdate();
        }
    }

    public void toggleActive(int id) throws SQLException {
        String sql = "UPDATE categories SET is_active = NOT is_active WHERE id = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, id);
            ps.executeUpdate();
        }
    }

    public void updateDisplayOrder(int id, int order) throws SQLException {
        String sql = "UPDATE categories SET display_order = ? WHERE id = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, order);
            ps.setInt(2, id);
            ps.executeUpdate();
        }
    }

    // ---------------- DELETE ----------------

    public void delete(int id) throws SQLException {
        String sql = "DELETE FROM categories WHERE id = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, id);
            ps.executeUpdate();
        }
    }

    // ---------------- CHECKS / COUNTS ----------------

    public boolean existsBySlug(String slug, Integer excludeId) throws SQLException {
        String sql = excludeId == null
                ? "SELECT COUNT(*) FROM categories WHERE slug = ?"
                : "SELECT COUNT(*) FROM categories WHERE slug = ? AND id <> ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, slug);
            if (excludeId != null) ps.setInt(2, excludeId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt(1) > 0;
            }
        }
        return false;
    }

    /** Nombre de produits DIRECTEMENT rattachés à la catégorie. */
    public int countProductsByCategory(int categoryId) throws SQLException {
        String sql = "SELECT COUNT(*) FROM products WHERE category_id = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, categoryId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt(1);
            }
        }
        return 0;
    }

    /** Nombre d'enfants directs. */
    public int countChildren(int categoryId) throws SQLException {
        String sql = "SELECT COUNT(*) FROM categories WHERE parent_id = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, categoryId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt(1);
            }
        }
        return 0;
    }
}
