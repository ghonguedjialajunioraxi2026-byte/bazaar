package com.ecommerce.service;

import com.ecommerce.dao.CategoryDAO;
import com.ecommerce.model.Category;
import com.ecommerce.util.SlugUtil;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Logique métier des catégories : validation, slugs, hiérarchie, arbre.
 * Toute la logique métier vit ici — jamais dans les Servlets ni le frontend.
 */
public class CategoryService {
    private final CategoryDAO categoryDAO = new CategoryDAO();

    // ---------------- READ ----------------

    public List<Category> getAllWithCount() throws SQLException {
        List<Category> list = categoryDAO.findAllWithCount();
        aggregateSubtreeCounts(list);
        return list;
    }

    public List<Category> getAll() throws SQLException {
        return categoryDAO.findAll();
    }

    public Category getCategoryById(int id) throws SQLException {
        return categoryDAO.findById(id);
    }

    public Category getBySlug(String slug) throws SQLException {
        if (slug == null) return null;
        return categoryDAO.findBySlug(slug.trim());
    }

    public List<Category> getRoots() throws SQLException {
        return categoryDAO.findRootCategories();
    }

    public List<Category> getChildren(int parentId) throws SQLException {
        return categoryDAO.findChildren(parentId);
    }

    public List<Category> getActive() throws SQLException {
        return categoryDAO.findActive();
    }

    /**
     * Arborescence complète (profondeur illimitée).
     * @param activeOnly si true, seules les catégories actives (+ enfants actifs)
     */
    public List<Category> getTree(boolean activeOnly) throws SQLException {
        List<Category> flat = activeOnly ? categoryDAO.findActive() : categoryDAO.findAll();
        // Les compteurs directs sont déjà à 0 ici ; on les charge puis on agrège
        Map<Integer, Integer> directCounts = directCountMap();
        for (Category c : flat) {
            c.setCount(directCounts.getOrDefault(c.getId(), 0));
        }
        List<Category> tree = buildTree(flat);
        aggregateTreeCounts(tree);
        return tree;
    }

    public List<Category> search(String query, Integer parentId, Boolean active, int page, int limit) throws SQLException {
        int safePage = Math.max(1, page);
        int safeLimit = Math.min(100, Math.max(1, limit));
        List<Category> list = categoryDAO.search(query, parentId, active, safeLimit, (safePage - 1) * safeLimit);
        aggregateSubtreeCounts(list);
        return list;
    }

    public int countSearch(String query, Integer parentId, Boolean active) throws SQLException {
        return categoryDAO.countSearch(query, parentId, active);
    }

    // ---------------- CREATE ----------------

    public int createCategory(Category cat) throws SQLException {
        validateForCreate(cat);
        cat.setSlug(resolveSlug(cat.getSlug(), cat.getName(), null));
        if (cat.getDisplayOrder() < 0) cat.setDisplayOrder(0);
        return categoryDAO.create(cat);
    }

    // ---------------- UPDATE ----------------

    public void updateCategory(Category cat) throws SQLException {
        if (cat == null || cat.getId() <= 0) {
            throw new SQLException("Catégorie invalide");
        }
        Category existing = categoryDAO.findById(cat.getId());
        if (existing == null) throw new SQLException("Catégorie introuvable");
        validateForUpdate(cat);
        cat.setSlug(resolveSlug(cat.getSlug(), cat.getName(), cat.getId()));
        if (cat.getDisplayOrder() < 0) cat.setDisplayOrder(0);
        categoryDAO.update(cat);
    }

    public void setActive(int id, boolean active) throws SQLException {
        Category c = categoryDAO.findById(id);
        if (c == null) throw new SQLException("Catégorie introuvable");
        categoryDAO.setActive(id, active);
    }

    public void toggleActive(int id) throws SQLException {
        Category c = categoryDAO.findById(id);
        if (c == null) throw new SQLException("Catégorie introuvable");
        categoryDAO.toggleActive(id);
    }

    public void updateDisplayOrder(int id, int order) throws SQLException {
        if (categoryDAO.findById(id) == null) throw new SQLException("Catégorie introuvable");
        categoryDAO.updateDisplayOrder(id, Math.max(0, order));
    }

    // ---------------- DELETE ----------------

    /**
     * Suppression sécurisée : refuse si la catégorie a des enfants
     * ou des produits rattachés (message explicite).
     * Grâce au FK ON DELETE SET NULL, aucun risque de suppression en cascade.
     */
    public void deleteCategory(int id) throws SQLException {
        Category c = categoryDAO.findById(id);
        if (c == null) throw new SQLException("Catégorie introuvable");
        int children = categoryDAO.countChildren(id);
        if (children > 0) {
            throw new SQLException("Suppression impossible : cette catégorie contient "
                    + children + " sous-catégorie(s). Supprimez ou déplacez-les d'abord.");
        }
        int products = categoryDAO.countProductsByCategory(id);
        if (products > 0) {
            throw new SQLException("Suppression impossible : " + products
                    + " produit(s) sont rattachés à cette catégorie. Réassignez-les d'abord.");
        }
        categoryDAO.delete(id);
    }

    // ---------------- VALIDATION ----------------

    private void validateForCreate(Category cat) throws SQLException {
        if (cat == null) throw new SQLException("Catégorie invalide");
        String name = cat.getName() == null ? "" : cat.getName().trim();
        if (name.length() < 2) throw new SQLException("Le nom de la catégorie doit contenir au moins 2 caractères");
        if (name.length() > 150) throw new SQLException("Le nom de la catégorie est trop long (150 max)");
        cat.setName(name);
        validateParent(null, cat.getParentId());
    }

    private void validateForUpdate(Category cat) throws SQLException {
        String name = cat.getName() == null ? "" : cat.getName().trim();
        if (name.length() < 2) throw new SQLException("Le nom de la catégorie doit contenir au moins 2 caractères");
        if (name.length() > 150) throw new SQLException("Le nom de la catégorie est trop long (150 max)");
        cat.setName(name);
        validateParent(cat.getId(), cat.getParentId());
    }

    /**
     * Valide le parent : existe, pas soi-même, pas un de ses descendants (anti-cycle).
     */
    private void validateParent(Integer selfId, Integer parentId) throws SQLException {
        if (parentId == null) return;
        if (selfId != null && parentId.equals(selfId)) {
            throw new SQLException("Une catégorie ne peut pas être son propre parent");
        }
        Category parent = categoryDAO.findById(parentId);
        if (parent == null) throw new SQLException("Catégorie parent introuvable");
        if (selfId != null && isDescendant(selfId, parentId)) {
            throw new SQLException("Hiérarchie invalide : le parent choisi est un descendant de cette catégorie (cycle interdit)");
        }
    }

    /** true si candidateId est un descendant (direct ou indirect) de ancestorId. */
    private boolean isDescendant(int ancestorId, int candidateId) throws SQLException {
        Integer current = candidateId;
        int guard = 0;
        while (current != null && guard++ < 1000) {
            if (current == ancestorId) return true;
            Category c = categoryDAO.findById(current);
            if (c == null) return false;
            current = c.getParentId();
        }
        return false;
    }

    /** Génère un slug unique (suffixe -2, -3... en cas de doublon). */
    private String resolveSlug(String slug, String name, Integer excludeId) throws SQLException {
        String base = (slug == null || slug.trim().isEmpty()) ? SlugUtil.toSlug(name) : SlugUtil.toSlug(slug);
        String candidate = base;
        int i = 2;
        while (categoryDAO.existsBySlug(candidate, excludeId)) {
            candidate = base + "-" + (i++);
        }
        return candidate;
    }

    // ---------------- ARBRE & COMPTEURS ----------------

    /** Construit l'arbre à partir d'une liste plate (profondeur illimitée). */
    public List<Category> buildTree(List<Category> flat) {
        Map<Integer, Category> byId = new LinkedHashMap<>();
        for (Category c : flat) {
            c.setChildren(new ArrayList<>());
            byId.put(c.getId(), c);
        }
        List<Category> roots = new ArrayList<>();
        for (Category c : flat) {
            if (c.getParentId() == null || !byId.containsKey(c.getParentId())) {
                roots.add(c);
            } else {
                byId.get(c.getParentId()).getChildren().add(c);
            }
        }
        return roots;
    }

    /** totalProductCount = produits directs + tous les descendants (récursif). */
    private int aggregateTreeCounts(List<Category> nodes) {
        int sum = 0;
        for (Category n : nodes) {
            int total = n.getCount() + aggregateTreeCounts(n.getChildren());
            n.setTotalProductCount(total);
            sum += total;
        }
        return sum;
    }

    private Map<Integer, Integer> directCountMap() throws SQLException {
        Map<Integer, Integer> map = new LinkedHashMap<>();
        for (Category c : categoryDAO.findAllWithCount()) {
            map.put(c.getId(), c.getCount());
        }
        return map;
    }

    /** Pour une liste plate : totalProductCount inclut les descendants. */
    private void aggregateSubtreeCounts(List<Category> list) throws SQLException {
        if (list.isEmpty()) return;
        List<Category> all = categoryDAO.findAll();
        Map<Integer, List<Integer>> childrenIds = new LinkedHashMap<>();
        for (Category c : all) {
            if (c.getParentId() != null) {
                childrenIds.computeIfAbsent(c.getParentId(), k -> new ArrayList<>()).add(c.getId());
            }
        }
        Map<Integer, Integer> direct = directCountMap();
        for (Category c : list) {
            c.setTotalProductCount(sumSubtree(c.getId(), direct, childrenIds));
        }
    }

    private int sumSubtree(int id, Map<Integer, Integer> direct, Map<Integer, List<Integer>> childrenIds) {
        int sum = direct.getOrDefault(id, 0);
        List<Integer> kids = childrenIds.get(id);
        if (kids != null) {
            for (int kid : kids) sum += sumSubtree(kid, direct, childrenIds);
        }
        return sum;
    }

    /** Tous les ids du sous-arbre (id inclus) — utile pour filtrer les produits. */
    public List<Integer> getSubtreeIds(int rootId) throws SQLException {
        List<Category> all = categoryDAO.findAll();
        Map<Integer, List<Integer>> childrenIds = new LinkedHashMap<>();
        for (Category c : all) {
            if (c.getParentId() != null) {
                childrenIds.computeIfAbsent(c.getParentId(), k -> new ArrayList<>()).add(c.getId());
            }
        }
        List<Integer> ids = new ArrayList<>();
        collectIds(rootId, childrenIds, ids);
        return ids;
    }

    private void collectIds(int id, Map<Integer, List<Integer>> childrenIds, List<Integer> out) {
        out.add(id);
        List<Integer> kids = childrenIds.get(id);
        if (kids != null) {
            for (int kid : kids) collectIds(kid, childrenIds, out);
        }
    }

    /** Fil d'Ariane : de la racine jusqu'à la catégorie (incluse). */
    public List<Category> getBreadcrumb(int id) throws SQLException {
        List<Category> path = new ArrayList<>();
        Category current = categoryDAO.findById(id);
        int guard = 0;
        while (current != null && guard++ < 1000) {
            path.add(0, current);
            current = current.getParentId() == null ? null : categoryDAO.findById(current.getParentId());
        }
        return path;
    }
}
