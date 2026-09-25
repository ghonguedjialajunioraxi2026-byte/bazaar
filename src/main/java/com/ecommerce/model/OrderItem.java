package com.ecommerce.model;

public class OrderItem {
    private int id;
    private int orderId;
    private int productId;
    /** Snapshot du nom au moment de l'achat (l'historique ne suit pas les renommages). */
    private String productName;
    private String size;
    private String color;
    private int qty;
    private int unitPrice;
    /** Offre négociée consommée (nullable, traçabilité Phase 8). */
    private Integer offerId;

    public OrderItem() {}

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public int getOrderId() { return orderId; }
    public void setOrderId(int orderId) { this.orderId = orderId; }

    public int getProductId() { return productId; }
    public void setProductId(int productId) { this.productId = productId; }

    public String getProductName() { return productName; }
    public void setProductName(String productName) { this.productName = productName; }

    public String getSize() { return size; }
    public void setSize(String size) { this.size = size; }

    public String getColor() { return color; }
    public void setColor(String color) { this.color = color; }

    public int getQty() { return qty; }
    public void setQty(int qty) { this.qty = qty; }

    public int getUnitPrice() { return unitPrice; }
    public void setUnitPrice(int unitPrice) { this.unitPrice = unitPrice; }

    public Integer getOfferId() { return offerId; }
    public void setOfferId(Integer offerId) { this.offerId = offerId; }
}
