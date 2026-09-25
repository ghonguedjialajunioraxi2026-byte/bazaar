package com.ecommerce.model;

public class Offer {
    private int id;
    private int conversationId;
    private int productId;
    private int qty;
    private String size;
    private String color;
    private int catalogPrice;
    private int unitPrice;
    private String createdBy;
    private String status;
    private Integer parentOfferId;
    private String createdAt;
    // Enrichis pour l'affichage (non persistés)
    private String productName;
    private String productImage;

    public Offer() {}

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }
    public int getConversationId() { return conversationId; }
    public void setConversationId(int conversationId) { this.conversationId = conversationId; }
    public int getProductId() { return productId; }
    public void setProductId(int productId) { this.productId = productId; }
    public int getQty() { return qty; }
    public void setQty(int qty) { this.qty = qty; }
    public String getSize() { return size; }
    public void setSize(String size) { this.size = size; }
    public String getColor() { return color; }
    public void setColor(String color) { this.color = color; }
    public int getCatalogPrice() { return catalogPrice; }
    public void setCatalogPrice(int catalogPrice) { this.catalogPrice = catalogPrice; }
    public int getUnitPrice() { return unitPrice; }
    public void setUnitPrice(int unitPrice) { this.unitPrice = unitPrice; }
    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Integer getParentOfferId() { return parentOfferId; }
    public void setParentOfferId(Integer parentOfferId) { this.parentOfferId = parentOfferId; }
    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }
    public String getProductName() { return productName; }
    public void setProductName(String productName) { this.productName = productName; }
    public String getProductImage() { return productImage; }
    public void setProductImage(String productImage) { this.productImage = productImage; }
}
