package com.spamfilter.adminclient.model;

/** A row from the shared users table with an admin-tier role. */
public class Admin {

    private final String id;
    private final String email;
    private final String displayName;
    private final String role;

    public Admin(String id, String email, String displayName, String role) {
        this.id = id;
        this.email = email;
        this.displayName = displayName;
        this.role = role;
    }

    public String getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getRole() {
        return role;
    }
}
