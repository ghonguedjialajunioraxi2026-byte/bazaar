package com.ecommerce.model;

public class Promotion {
    private int id;
    private int productId;
    private int promoPrice;
    private int promoPercent;
    private String startDate;
    private String endDate;
    private boolean active;
    private String createdAt;

    public Promotion() {}

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public int getProductId() { return productId; }
    public void setProductId(int productId) { this.productId = productId; }

    public int getPromoPrice() { return promoPrice; }
    public void setPromoPrice(int promoPrice) { this.promoPrice = promoPrice; }

    public int getPromoPercent() { return promoPercent; }
    public void setPromoPercent(int promoPercent) { this.promoPercent = promoPercent; }

    public String getStartDate() { return startDate; }
    public void setStartDate(String startDate) { this.startDate = startDate; }

    public String getEndDate() { return endDate; }
    public void setEndDate(String endDate) { this.endDate = endDate; }

    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }

    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }
}
