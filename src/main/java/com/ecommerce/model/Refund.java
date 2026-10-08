package com.ecommerce.model;

/**
 * Remboursement PawaPay (POST /v2/refunds {refundId, depositId}).
 * Un refundId (UUIDv4) unique, persisté AVANT l'appel réseau —
 * même discipline que le Deposit (réconciliation garantie).
 */
public class Refund {
    private int id;
    private int orderId;
    private int paymentId;
    private String refundId;
    private String depositId;
    /**
     * Référence assignée PAR le provider (reçu destinataire, callback).
     * Distincte du refundId (choisi par BAZAAR).
     */
    private String providerTransactionId;
    private int amount;
    private String currency;
    private String status;
    private String failureReason;
    /** Horodatage du dernier polling/callback (supervision admin). */
    private String lastReconciledAt;
    private String createdAt;
    private String updatedAt;

    public Refund() {}

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public int getOrderId() { return orderId; }
    public void setOrderId(int orderId) { this.orderId = orderId; }

    public int getPaymentId() { return paymentId; }
    public void setPaymentId(int paymentId) { this.paymentId = paymentId; }

    public String getRefundId() { return refundId; }
    public void setRefundId(String refundId) { this.refundId = refundId; }

    public String getDepositId() { return depositId; }
    public void setDepositId(String depositId) { this.depositId = depositId; }

    public String getProviderTransactionId() { return providerTransactionId; }
    public void setProviderTransactionId(String providerTransactionId) {
        this.providerTransactionId = providerTransactionId;
    }

    public int getAmount() { return amount; }
    public void setAmount(int amount) { this.amount = amount; }

    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getFailureReason() { return failureReason; }
    public void setFailureReason(String failureReason) { this.failureReason = failureReason; }

    public String getLastReconciledAt() { return lastReconciledAt; }
    public void setLastReconciledAt(String lastReconciledAt) { this.lastReconciledAt = lastReconciledAt; }

    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }

    public String getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(String updatedAt) { this.updatedAt = updatedAt; }
}
