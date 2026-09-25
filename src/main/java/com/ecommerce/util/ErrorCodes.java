package com.ecommerce.util;

/**
 * Codes d'erreur stables de l'API (Phase 2).
 * Règle : les réponses évoluent de façon ADDITIVE — ces codes s'ajoutent
 * aux champs existants, ils ne les remplacent jamais sans adapter le frontend.
 */
public final class ErrorCodes {

    private ErrorCodes() {}

    public static final String AUTH_REQUIRED   = "AUTH_REQUIRED";
    public static final String AUTH_INVALID    = "AUTH_INVALID";
    public static final String ACCESS_DENIED   = "ACCESS_DENIED";
    public static final String VALIDATION_ERROR = "VALIDATION_ERROR";
    public static final String RESOURCE_NOT_FOUND = "RESOURCE_NOT_FOUND";
    public static final String CONFLICT        = "CONFLICT";
    public static final String INVALID_REQUEST = "INVALID_REQUEST";
    public static final String DATABASE_ERROR  = "DATABASE_ERROR";
    public static final String INTERNAL_ERROR  = "INTERNAL_ERROR";
}
