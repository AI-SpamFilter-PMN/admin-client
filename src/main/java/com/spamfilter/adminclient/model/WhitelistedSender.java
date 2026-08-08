package com.spamfilter.adminclient.model;

import java.time.Instant;

/** A row from the shared whitelisted_senders table - trusted senders, added by an admin or the system. */
public class WhitelistedSender {

    private final long id;
    private final String senderId;
    private final String aliasName;
    private final String description;
    private final String addedBy;
    private final String createdAt;

    public WhitelistedSender(long id, String senderId, String aliasName, String description, String addedBy,
                              Instant createdAt) {
        this.id = id;
        this.senderId = senderId;
        this.aliasName = aliasName;
        this.description = description;
        this.addedBy = addedBy;
        this.createdAt = createdAt.toString();
    }

    public long getId() {
        return id;
    }

    public String getSenderId() {
        return senderId;
    }

    public String getAliasName() {
        return aliasName;
    }

    public String getDescription() {
        return description;
    }

    public String getAddedBy() {
        return addedBy;
    }

    public String getCreatedAt() {
        return createdAt;
    }
}
