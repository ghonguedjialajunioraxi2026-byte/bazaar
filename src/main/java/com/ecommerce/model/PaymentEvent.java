package com.ecommerce.model;

/** Événement PawaPay reçu (callback), clé unique = idempotence webhook. */
public class PaymentEvent {
    private int id;
    private int paymentId;
    private String eventKey;
    private String payload;
    private String receivedAt;

    public PaymentEvent() {}

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public int getPaymentId() { return paymentId; }
    public void setPaymentId(int paymentId) { this.paymentId = paymentId; }

    public String getEventKey() { return eventKey; }
    public void setEventKey(String eventKey) { this.eventKey = eventKey; }

    public String getPayload() { return payload; }
    public void setPayload(String payload) { this.payload = payload; }

    public String getReceivedAt() { return receivedAt; }
    public void setReceivedAt(String receivedAt) { this.receivedAt = receivedAt; }
}
