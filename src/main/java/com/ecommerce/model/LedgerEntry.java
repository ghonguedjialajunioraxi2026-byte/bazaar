package com.ecommerce.model;

public class LedgerEntry {
    private int id;
    private String reference;
    private int orderId;
    private Integer supplierId;
    private String kind;
    private int gross;
    private int discount;
    private int net;
    private int commissionBps;
    private int commission;
    private int fixedFee;
    private int supplierEarnings;
    private String status;
    private String createdAt;

    public LedgerEntry() {}

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }
    public String getReference() { return reference; }
    public void setReference(String reference) { this.reference = reference; }
    public int getOrderId() { return orderId; }
    public void setOrderId(int orderId) { this.orderId = orderId; }
    public Integer getSupplierId() { return supplierId; }
    public void setSupplierId(Integer supplierId) { this.supplierId = supplierId; }
    public String getKind() { return kind; }
    public void setKind(String kind) { this.kind = kind; }
    public int getGross() { return gross; }
    public void setGross(int gross) { this.gross = gross; }
    public int getDiscount() { return discount; }
    public void setDiscount(int discount) { this.discount = discount; }
    public int getNet() { return net; }
    public void setNet(int net) { this.net = net; }
    public int getCommissionBps() { return commissionBps; }
    public void setCommissionBps(int commissionBps) { this.commissionBps = commissionBps; }
    public int getCommission() { return commission; }
    public void setCommission(int commission) { this.commission = commission; }
    public int getFixedFee() { return fixedFee; }
    public void setFixedFee(int fixedFee) { this.fixedFee = fixedFee; }
    public int getSupplierEarnings() { return supplierEarnings; }
    public void setSupplierEarnings(int supplierEarnings) { this.supplierEarnings = supplierEarnings; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }
}
