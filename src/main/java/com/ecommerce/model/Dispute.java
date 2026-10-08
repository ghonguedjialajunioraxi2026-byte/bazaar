package com.ecommerce.model;

/**
 * Litige "commande non reçue" (protection acheteur).
 * Tant qu'un litige est OPEN : PAYOUT = BLOCKED.
 * Une simple déclaration ne rembourse jamais automatiquement :
 * la résolution (REFUND ou REJECT) est un acte admin tracé.
 */
public class Dispute {
    public static final String REASON_NOT_RECEIVED = "NOT_RECEIVED";
    public static final String STATUS_OPEN = "OPEN";
    public static final String STATUS_REFUNDED = "RESOLVED_REFUNDED";
    public static final String STATUS_REJECTED = "RESOLVED_REJECTED";

    private int id;
    private int orderId;
    private String phone;
    private String reason;
    private String status;
    private String resolutionNote;
    private String resolvedAt;
    private String createdAt;
    private String updatedAt;

    public Dispute() {}

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public int getOrderId() { return orderId; }
    public void setOrderId(int orderId) { this.orderId = orderId; }

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getResolutionNote() { return resolutionNote; }
    public void setResolutionNote(String resolutionNote) { this.resolutionNote = resolutionNote; }

    public String getResolvedAt() { return resolvedAt; }
    public void setResolvedAt(String resolvedAt) { this.resolvedAt = resolvedAt; }

    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }

    public String getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(String updatedAt) { this.updatedAt = updatedAt; }
}
