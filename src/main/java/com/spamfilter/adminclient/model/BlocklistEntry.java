package com.spamfilter.adminclient.model;

import java.time.Instant;

/** A row from the shared blocklist table - numbers blocked outright. */
public class BlocklistEntry {

    private final long id;
    private final String msisdn;
    private final String reason;
    private final String createdAt;
    private final String expiresAt;

    public BlocklistEntry(long id, String msisdn, String reason, Instant createdAt, Instant expiresAt) {
        this.id = id;
        this.msisdn = msisdn;
        this.reason = reason;
        this.createdAt = createdAt.toString();
        this.expiresAt = expiresAt == null ? null : expiresAt.toString();
    }

    public long getId() {
        return id;
    }

    public String getMsisdn() {
        return msisdn;
    }

    public String getReason() {
        return reason;
    }

    public String getCreatedAt() {
        return createdAt;
    }

    public String getExpiresAt() {
        return expiresAt;
    }
}
