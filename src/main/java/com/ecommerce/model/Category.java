package com.ecommerce.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Catégorie e-commerce hiérarchique (profondeur illimitée).
 * parentId = null -> catégorie principale (racine).
 * Les champs historiques (icon, active, count) sont conservés
 * pour compatibilité avec le frontend existant.
 */
public class Category {
    private int id;
    private String name;
    private String slug;
    private Integer parentId;
    private String description;
    private String icon;
    private String image;
    private int count;
    private boolean active = true;
    private int displayOrder;
    private String createdAt;
    private String updatedAt;

    /** Enfants directs (rempli par le service lors de la construction de l'arbre). Non persisté. */
    private List<Category> children = new ArrayList<>();
    /** Nombre de produits incluant les sous-catégories. Non persisté. */
    private int totalProductCount;

    public Category() {}

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getSlug() { return slug; }
    public void setSlug(String slug) { this.slug = slug; }

    public Integer getParentId() { return parentId; }
    public void setParentId(Integer parentId) { this.parentId = parentId; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getIcon() { return icon; }
    public void setIcon(String icon) { this.icon = icon; }

    public String getImage() { return image; }
    public void setImage(String image) { this.image = image; }

    public int getCount() { return count; }
    public void setCount(int count) { this.count = count; }

    /** Alias : nombre de produits directs (même valeur que count). */
    public int getProductCount() { return count; }
    public void setProductCount(int productCount) { this.count = productCount; }

    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }

    /** Alias de active (convention status). */
    public boolean isStatus() { return active; }
    public void setStatus(boolean status) { this.active = status; }

    public int getDisplayOrder() { return displayOrder; }
    public void setDisplayOrder(int displayOrder) { this.displayOrder = displayOrder; }

    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }

    public String getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(String updatedAt) { this.updatedAt = updatedAt; }

    public List<Category> getChildren() { return children; }
    public void setChildren(List<Category> children) {
        this.children = children == null ? new ArrayList<>() : children;
    }

    public int getTotalProductCount() { return totalProductCount; }
    public void setTotalProductCount(int totalProductCount) { this.totalProductCount = totalProductCount; }

    public boolean isRoot() { return parentId == null; }
}
