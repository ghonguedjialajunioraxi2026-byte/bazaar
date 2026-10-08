package com.ecommerce.model;

/**
 * Relevé financier PawaPay (POST /v2/statements, async).
 * statementId généré PAR PawaPay (persisté APRÈS l'appel ACCEPTED).
 * Diagnostic/audit uniquement : AUCUNE écriture comptable n'en découle.
 */
public class PawaPayStatement {
    private int id;
    private String statementId;
    private String status;
    private String walletCountry;
    private String walletCurrency;
    private String walletProvider;
    private String startDate;
    private String endDate;
    private String callbackUrl;
    private String downloadUrl;
    private String downloadUrlExpiresAt;
    private String completedAt;
    private String failedAt;
    private String failureReason;
    private Integer requestedBy;
    private String createdAt;
    private String updatedAt;
    private String lastReconciledAt;

    public PawaPayStatement() {}

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public String getStatementId() { return statementId; }
    public void setStatementId(String statementId) { this.statementId = statementId; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getWalletCountry() { return walletCountry; }
    public void setWalletCountry(String walletCountry) { this.walletCountry = walletCountry; }

    public String getWalletCurrency() { return walletCurrency; }
    public void setWalletCurrency(String walletCurrency) { this.walletCurrency = walletCurrency; }

    public String getWalletProvider() { return walletProvider; }
    public void setWalletProvider(String walletProvider) { this.walletProvider = walletProvider; }

    public String getStartDate() { return startDate; }
    public void setStartDate(String startDate) { this.startDate = startDate; }

    public String getEndDate() { return endDate; }
    public void setEndDate(String endDate) { this.endDate = endDate; }

    public String getCallbackUrl() { return callbackUrl; }
    public void setCallbackUrl(String callbackUrl) { this.callbackUrl = callbackUrl; }

    public String getDownloadUrl() { return downloadUrl; }
    public void setDownloadUrl(String downloadUrl) { this.downloadUrl = downloadUrl; }

    public String getDownloadUrlExpiresAt() { return downloadUrlExpiresAt; }
    public void setDownloadUrlExpiresAt(String downloadUrlExpiresAt) {
        this.downloadUrlExpiresAt = downloadUrlExpiresAt;
    }

    public String getCompletedAt() { return completedAt; }
    public void setCompletedAt(String completedAt) { this.completedAt = completedAt; }

    public String getFailedAt() { return failedAt; }
    public void setFailedAt(String failedAt) { this.failedAt = failedAt; }

    public String getFailureReason() { return failureReason; }
    public void setFailureReason(String failureReason) { this.failureReason = failureReason; }

    public Integer getRequestedBy() { return requestedBy; }
    public void setRequestedBy(Integer requestedBy) { this.requestedBy = requestedBy; }

    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }

    public String getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(String updatedAt) { this.updatedAt = updatedAt; }

    public String getLastReconciledAt() { return lastReconciledAt; }
    public void setLastReconciledAt(String lastReconciledAt) { this.lastReconciledAt = lastReconciledAt; }
}
