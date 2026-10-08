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
    public static final String INVALID_ORDER_STATUS = "INVALID_ORDER_STATUS";
    public static final String ORDER_NOT_FOUND = "ORDER_NOT_FOUND";
    public static final String ORDER_ACCESS_DENIED = "ORDER_ACCESS_DENIED";
    public static final String CONVERSATION_NOT_FOUND = "CONVERSATION_NOT_FOUND";
    public static final String CONVERSATION_ACCESS_DENIED = "CONVERSATION_ACCESS_DENIED";
    public static final String MESSAGE_EMPTY = "MESSAGE_EMPTY";
    public static final String MESSAGE_TOO_LONG = "MESSAGE_TOO_LONG";
    public static final String RATE_LIMITED = "RATE_LIMITED";
    public static final String OFFER_INVALID = "OFFER_INVALID";
    public static final String OFFER_ACCESS_DENIED = "OFFER_ACCESS_DENIED";
    public static final String OFFER_NOT_AVAILABLE = "OFFER_NOT_AVAILABLE";
    public static final String PAYMENT_REQUIRED = "PAYMENT_REQUIRED";
    public static final String PAYMENT_NOT_FOUND = "PAYMENT_NOT_FOUND";
    public static final String PAYMENT_INVALID = "PAYMENT_INVALID";
    public static final String PROVIDER_UNAVAILABLE = "PROVIDER_UNAVAILABLE";
    public static final String ORDER_ALREADY_PAID = "ORDER_ALREADY_PAID";
    public static final String DUPLICATE_DEPOSIT = "DUPLICATE_DEPOSIT";
    public static final String PAYMENT_ALREADY_PROCESSING = "PAYMENT_ALREADY_PROCESSING";
    public static final String PAWAPAY_TIMEOUT = "PAWAPAY_TIMEOUT";
    public static final String PAWAPAY_REJECTED = "PAWAPAY_REJECTED";
    public static final String PAYOUT_BLOCKED = "PAYOUT_BLOCKED";
    public static final String PAYOUT_CANCEL_REFUSED = "PAYOUT_CANCEL_REFUSED";
    public static final String REFUND_CANCEL_REFUSED = "REFUND_CANCEL_REFUSED";
    public static final String PAYOUT_DETAILS_MISSING = "PAYOUT_DETAILS_MISSING";
    public static final String REFUND_NOT_ALLOWED = "REFUND_NOT_ALLOWED";
    public static final String ALREADY_REFUNDED = "ALREADY_REFUNDED";
    public static final String DISPUTE_OPEN = "DISPUTE_OPEN";
    public static final String NOTIFICATION_NOT_FOUND = "NOTIFICATION_NOT_FOUND";
    public static final String NOTIFICATION_ACCESS_DENIED = "NOTIFICATION_ACCESS_DENIED";
}
