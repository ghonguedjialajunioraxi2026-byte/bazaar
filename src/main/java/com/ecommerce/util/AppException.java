package com.ecommerce.util;

/**
 * Exception métier portant un code d'erreur stable + un statut HTTP.
 * Permet aux servlets de répondre proprement sans exposer de détails internes.
 */
public class AppException extends Exception {

    private final String code;
    private final int httpStatus;

    public AppException(String code, int httpStatus, String userMessage) {
        super(userMessage);
        this.code = code;
        this.httpStatus = httpStatus;
    }

    public AppException(String code, int httpStatus, String userMessage, Throwable cause) {
        super(userMessage, cause);
        this.code = code;
        this.httpStatus = httpStatus;
    }

    public String getCode() {
        return code;
    }

    public int getHttpStatus() {
        return httpStatus;
    }
}
