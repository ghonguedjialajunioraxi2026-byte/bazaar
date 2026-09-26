package com.ecommerce.model;

public class ShipmentEvent {
    private int id;
    private int shipmentId;
    private String status;
    private String label;
    private String detail;
    private String source;
    private String createdAt;

    public ShipmentEvent() {}

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }
    public int getShipmentId() { return shipmentId; }
    public void setShipmentId(int shipmentId) { this.shipmentId = shipmentId; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getLabel() { return label; }
    public void setLabel(String label) { this.label = label; }
    public String getDetail() { return detail; }
    public void setDetail(String detail) { this.detail = detail; }
    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }
    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }
}
