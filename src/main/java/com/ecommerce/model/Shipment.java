package com.ecommerce.model;

public class Shipment {
    private int id;
    private int orderId;
    private String trackingNumber;
    private String carrier;
    private String status;
    private String failureReason;
    private String shippedAt;
    private String deliveredAt;
    private String createdAt;

    public Shipment() {}

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }
    public int getOrderId() { return orderId; }
    public void setOrderId(int orderId) { this.orderId = orderId; }
    public String getTrackingNumber() { return trackingNumber; }
    public void setTrackingNumber(String trackingNumber) { this.trackingNumber = trackingNumber; }
    public String getCarrier() { return carrier; }
    public void setCarrier(String carrier) { this.carrier = carrier; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getFailureReason() { return failureReason; }
    public void setFailureReason(String failureReason) { this.failureReason = failureReason; }
    public String getShippedAt() { return shippedAt; }
    public void setShippedAt(String shippedAt) { this.shippedAt = shippedAt; }
    public String getDeliveredAt() { return deliveredAt; }
    public void setDeliveredAt(String deliveredAt) { this.deliveredAt = deliveredAt; }
    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }
}
