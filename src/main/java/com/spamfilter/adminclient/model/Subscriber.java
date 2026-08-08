package com.spamfilter.adminclient.model;

import java.time.Instant;

/**
 * A row from Neon's subscribers table. This app reads it broadly and writes
 * only to the `status` column (suspend/activate/block a subscriber).
 */
public class Subscriber {

    private final String id;
    private final String msisdn;
    private final String imsi;
    private final String displayName;
    private final String status;
    private final String createdAt;

    public Subscriber(String id, String msisdn, String imsi, String displayName, String status, Instant createdAt) {
        this.id = id;
        this.msisdn = msisdn;
        this.imsi = imsi;
        this.displayName = displayName;
        this.status = status;
        this.createdAt = createdAt.toString();
    }

    public String getId() {
        return id;
    }

    public String getMsisdn() {
        return msisdn;
    }

    public String getImsi() {
        return imsi;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getStatus() {
        return status;
    }

    public String getCreatedAt() {
        return createdAt;
    }
}
