package com.ecommerce.model;

import com.google.gson.JsonArray;
import com.google.gson.JsonParser;

import java.util.ArrayList;
import java.util.List;

public class Product {
    private int id;
    private String name;
    private String slug;
    private String reference;
    private String brand;
    private int categoryId;
    private String categoryName;
    private int price;
    private Integer oldPrice;
    private int discount;
    private Double rating;
    private Integer reviews;
    private String badge;
    private String description;
    private List<String> images;
    private List<String> sizes;
    private List<String> colors;
    private int stock;
    private int supplierId;
    private String status;
    private String supplierName;
    private String supplierWhatsapp;
    private String promoStart;
    private String promoEnd;

    public Product() {}

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getSlug() { return slug; }
    public void setSlug(String slug) { this.slug = slug; }

    public String getReference() { return reference; }
    public void setReference(String reference) { this.reference = reference; }

    public String getBrand() { return brand; }
    public void setBrand(String brand) { this.brand = brand; }

    public int getCategoryId() { return categoryId; }
    public void setCategoryId(int categoryId) { this.categoryId = categoryId; }

    public String getCategoryName() { return categoryName; }
    public void setCategoryName(String categoryName) { this.categoryName = categoryName; }

    public int getPrice() { return price; }
    public void setPrice(int price) { this.price = price; }

    public Integer getOldPrice() { return oldPrice; }
    public void setOldPrice(Integer oldPrice) { this.oldPrice = oldPrice; }

    public int getDiscount() { return discount; }
    public void setDiscount(int discount) { this.discount = discount; }

    public Double getRating() { return rating; }
    public void setRating(Double rating) { this.rating = rating; }

    public Integer getReviews() { return reviews; }
    public void setReviews(Integer reviews) { this.reviews = reviews; }

    public String getBadge() { return badge; }
    public void setBadge(String badge) { this.badge = badge; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public List<String> getImages() { return images; }
    public void setImages(List<String> images) { this.images = images; }

    public List<String> getSizes() { return sizes; }
    public void setSizes(List<String> sizes) { this.sizes = sizes; }

    public List<String> getColors() { return colors; }
    public void setColors(List<String> colors) { this.colors = colors; }

    public int getStock() { return stock; }
    public void setStock(int stock) { this.stock = stock; }

    public int getSupplierId() { return supplierId; }
    public void setSupplierId(int supplierId) { this.supplierId = supplierId; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getSupplierName() { return supplierName; }
    public void setSupplierName(String supplierName) { this.supplierName = supplierName; }

    public String getSupplierWhatsapp() { return supplierWhatsapp; }
    public void setSupplierWhatsapp(String supplierWhatsapp) { this.supplierWhatsapp = supplierWhatsapp; }

    public String getPromoStart() { return promoStart; }
    public void setPromoStart(String promoStart) { this.promoStart = promoStart; }

    public String getPromoEnd() { return promoEnd; }
    public void setPromoEnd(String promoEnd) { this.promoEnd = promoEnd; }

    public static List<String> parseJsonArray(String json) {
        if (json == null || json.isEmpty() || "null".equals(json)) return new ArrayList<>();
        try {
            JsonArray arr = JsonParser.parseString(json).getAsJsonArray();
            List<String> list = new ArrayList<>();
            for (var el : arr) list.add(el.getAsString());
            return list;
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }
}
