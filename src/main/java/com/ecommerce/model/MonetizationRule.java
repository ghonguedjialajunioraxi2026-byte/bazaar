package com.ecommerce.model;

public class MonetizationRule {
    private int id;
    private String name;
    private int commissionBps;
    private int fixedFee;
    private boolean active;

    public MonetizationRule() {}

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public int getCommissionBps() { return commissionBps; }
    public void setCommissionBps(int commissionBps) { this.commissionBps = commissionBps; }
    public int getFixedFee() { return fixedFee; }
    public void setFixedFee(int fixedFee) { this.fixedFee = fixedFee; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
}
