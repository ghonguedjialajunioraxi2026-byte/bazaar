package com.ecommerce.model;

public class DeliveryZone {
    private int id;
    private String city;
    private String zone;
    private int fee;
    private boolean active;

    public DeliveryZone() {}

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public String getCity() { return city; }
    public void setCity(String city) { this.city = city; }

    public String getZone() { return zone; }
    public void setZone(String zone) { this.zone = zone; }

    public int getFee() { return fee; }
    public void setFee(int fee) { this.fee = fee; }

    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
}
