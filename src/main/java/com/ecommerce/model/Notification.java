package com.ecommerce.model;

public class Notification {
    private int id;
    private String targetRole;
    private Integer targetUserId;
    private String title;
    private String message;
    private String type;
    private String priority = "NORMAL";
    private boolean read;
    private String link;
    private String createdAt;

    public Notification() {}

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public String getTargetRole() { return targetRole; }
    public void setTargetRole(String targetRole) { this.targetRole = targetRole; }

    public Integer getTargetUserId() { return targetUserId; }
    public void setTargetUserId(Integer targetUserId) { this.targetUserId = targetUserId; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public String getPriority() { return priority; }
    public void setPriority(String priority) {
        this.priority = (priority == null || priority.isEmpty()) ? "NORMAL" : priority.toUpperCase();
    }

    public boolean isRead() { return read; }
    public void setRead(boolean read) { this.read = read; }

    public String getLink() { return link; }
    public void setLink(String link) { this.link = link; }

    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }
}
