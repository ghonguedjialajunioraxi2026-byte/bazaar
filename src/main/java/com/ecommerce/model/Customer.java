package com.ecommerce.model;

/** Compte client léger BAZAAR : nom + email (sans mot de passe). */
public class Customer {
    private int id;
    private String name;
    private String email;
    private String googleSub;
    /** Photo de profil (facultative, visuel réservé si absente). */
    private String avatar;
    private String createdAt;

    public Customer() {}

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getGoogleSub() { return googleSub; }
    public void setGoogleSub(String googleSub) { this.googleSub = googleSub; }

    public String getAvatar() { return avatar; }
    public void setAvatar(String avatar) { this.avatar = avatar; }

    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }
}
