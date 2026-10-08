package com.ecommerce.model;

public class Payment {
    private int id;
    private int orderId;
    private String method;
    private int amount;
    private String currency;
    private String transactionRef;
    private String idempotenceKey;
    private String provider;
    /** Référence interne MBOA (MBOA-ORD-YYYYMMDD-XXXXXXXX), UNIQUE. */
    private String internalReference;
    /** depositId PawaPay (UUID v4) — référence de réconciliation. */
    private String providerTransactionId;
    /** Référence commande côté PawaPay (clientReferenceId = order_number). */
    private String clientReferenceId;
    /** Provider PawaPay (MTN_MOMO_CMR / ORANGE_CMR). */
    private String operator;
    /** MSISDN international (237XXXXXXXXX), jamais loggé en clair. */
    private String phoneNumber;
    private String failureReason;
    private Integer verifiedBy;
    private String status;
    private String paidAt;
    private String updatedAt;
    private String createdAt;

    public Payment() {}

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public int getOrderId() { return orderId; }
    public void setOrderId(int orderId) { this.orderId = orderId; }

    public String getMethod() { return method; }
    public void setMethod(String method) { this.method = method; }

    public int getAmount() { return amount; }
    public void setAmount(int amount) { this.amount = amount; }

    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }

    public String getTransactionRef() { return transactionRef; }
    public void setTransactionRef(String transactionRef) { this.transactionRef = transactionRef; }

    public String getIdempotenceKey() { return idempotenceKey; }
    public void setIdempotenceKey(String idempotenceKey) { this.idempotenceKey = idempotenceKey; }

    public String getProvider() { return provider; }
    public void setProvider(String provider) { this.provider = provider; }

    public String getInternalReference() { return internalReference; }
    public void setInternalReference(String internalReference) { this.internalReference = internalReference; }

    public String getProviderTransactionId() { return providerTransactionId; }
    public void setProviderTransactionId(String providerTransactionId) { this.providerTransactionId = providerTransactionId; }

    public String getClientReferenceId() { return clientReferenceId; }
    public void setClientReferenceId(String clientReferenceId) { this.clientReferenceId = clientReferenceId; }

    public String getOperator() { return operator; }
    public void setOperator(String operator) { this.operator = operator; }

    public String getPhoneNumber() { return phoneNumber; }
    public void setPhoneNumber(String phoneNumber) { this.phoneNumber = phoneNumber; }

    public String getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(String updatedAt) { this.updatedAt = updatedAt; }

    public String getFailureReason() { return failureReason; }
    public void setFailureReason(String failureReason) { this.failureReason = failureReason; }

    public Integer getVerifiedBy() { return verifiedBy; }
    public void setVerifiedBy(Integer verifiedBy) { this.verifiedBy = verifiedBy; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getPaidAt() { return paidAt; }
    public void setPaidAt(String paidAt) { this.paidAt = paidAt; }

    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }
}
