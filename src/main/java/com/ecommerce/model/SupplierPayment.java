package com.ecommerce.model;

public class SupplierPayment {
    private int id;
    private int supplierId;
    /** Commande versée (lignes "auto" PawaPay) ; NULL pour les retraits manuels historiques. */
    private Integer orderId;
    /** payoutId PawaPay (UUIDv4), UNIQUE — idempotence du versement. */
    private String payoutId;
    /**
     * Référence assignée PAR le provider (reçu SMS du destinataire,
     * transmise en callback). Distincte du payoutId (choisi par BAZAAR).
     */
    private String providerTransactionId;
    /** Provider PawaPay destinataire (MTN_MOMO_CMR / ORANGE_CMR). */
    private String provider;
    /** MSISDN destinataire (jamais loggé en clair). */
    private String phoneNumber;
    /** Décomposition : brut − commission − frais = net versé (amount). */
    private Integer grossAmount;
    private Integer commission;
    private Integer pawapayFee;
    private String failureReason;
    /** Horodatage du dernier polling/callback (supervision admin). */
    private String lastReconciledAt;
    private String reference;
    private int amount;
    private String method;
    private String status;
    private String notes;
    private String createdAt;
    private String updatedAt;

    public SupplierPayment() {}

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public int getSupplierId() { return supplierId; }
    public void setSupplierId(int supplierId) { this.supplierId = supplierId; }

    public Integer getOrderId() { return orderId; }
    public void setOrderId(Integer orderId) { this.orderId = orderId; }

    public String getPayoutId() { return payoutId; }
    public void setPayoutId(String payoutId) { this.payoutId = payoutId; }

    public String getProviderTransactionId() { return providerTransactionId; }
    public void setProviderTransactionId(String providerTransactionId) {
        this.providerTransactionId = providerTransactionId;
    }

    public String getProvider() { return provider; }
    public void setProvider(String provider) { this.provider = provider; }

    public String getPhoneNumber() { return phoneNumber; }
    public void setPhoneNumber(String phoneNumber) { this.phoneNumber = phoneNumber; }

    public Integer getGrossAmount() { return grossAmount; }
    public void setGrossAmount(Integer grossAmount) { this.grossAmount = grossAmount; }

    public Integer getCommission() { return commission; }
    public void setCommission(Integer commission) { this.commission = commission; }

    public Integer getPawapayFee() { return pawapayFee; }
    public void setPawapayFee(Integer pawapayFee) { this.pawapayFee = pawapayFee; }

    public String getFailureReason() { return failureReason; }
    public void setFailureReason(String failureReason) { this.failureReason = failureReason; }

    public String getLastReconciledAt() { return lastReconciledAt; }
    public void setLastReconciledAt(String lastReconciledAt) { this.lastReconciledAt = lastReconciledAt; }

    public String getReference() { return reference; }
    public void setReference(String reference) { this.reference = reference; }

    public int getAmount() { return amount; }
    public void setAmount(int amount) { this.amount = amount; }

    public String getMethod() { return method; }
    public void setMethod(String method) { this.method = method; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }

    public String getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(String updatedAt) { this.updatedAt = updatedAt; }
}
