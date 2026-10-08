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
    /** 1 = réception confirmée par le client (ou l'admin) — condition du Payout. */
    private boolean receiptConfirmed;
    private String receiptConfirmedAt;
    private String receiptConfirmedBy;
    /** Workflow v2 : le fournisseur est en route (EN COURS DE LIVRAISON). */
    private String deliveryStartedAt;
    private String deliveryStartedBy;
    /** Workflow v2 : le fournisseur a validé après confirmation client. */
    private String deliveryValidatedAt;
    private String deliveryValidatedBy;
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

    public boolean isReceiptConfirmed() { return receiptConfirmed; }
    public void setReceiptConfirmed(boolean receiptConfirmed) { this.receiptConfirmed = receiptConfirmed; }

    public String getReceiptConfirmedAt() { return receiptConfirmedAt; }
    public void setReceiptConfirmedAt(String receiptConfirmedAt) { this.receiptConfirmedAt = receiptConfirmedAt; }

    public String getReceiptConfirmedBy() { return receiptConfirmedBy; }
    public void setReceiptConfirmedBy(String receiptConfirmedBy) { this.receiptConfirmedBy = receiptConfirmedBy; }
    public String getDeliveryStartedAt() { return deliveryStartedAt; }
    public void setDeliveryStartedAt(String deliveryStartedAt) { this.deliveryStartedAt = deliveryStartedAt; }
    public boolean isDeliveryStarted() { return deliveryStartedAt != null && !deliveryStartedAt.isEmpty(); }
    public String getDeliveryStartedBy() { return deliveryStartedBy; }
    public void setDeliveryStartedBy(String deliveryStartedBy) { this.deliveryStartedBy = deliveryStartedBy; }
    public String getDeliveryValidatedAt() { return deliveryValidatedAt; }
    public void setDeliveryValidatedAt(String deliveryValidatedAt) { this.deliveryValidatedAt = deliveryValidatedAt; }
    public boolean isDeliveryValidated() { return deliveryValidatedAt != null && !deliveryValidatedAt.isEmpty(); }
    public String getDeliveryValidatedBy() { return deliveryValidatedBy; }
    public void setDeliveryValidatedBy(String deliveryValidatedBy) { this.deliveryValidatedBy = deliveryValidatedBy; }
    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }
}
