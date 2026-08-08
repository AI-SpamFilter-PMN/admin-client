package com.spamfilter.adminclient.model;

import java.time.Instant;

/**
 * A row from Neon's logs table. Read-only - this app never writes to this
 * table, it's the system/event log every other component writes to.
 */
public class LogEntry {

    private final long id;
    private final String eventType;
    private final String severity;
    private final String message;
    private final String relatedMessageId;
    private final String relatedCallId;
    private final String createdAt;

    public LogEntry(long id, String eventType, String severity, String message,
                     String relatedMessageId, String relatedCallId, Instant createdAt) {
        this.id = id;
        this.eventType = eventType;
        this.severity = severity;
        this.message = message;
        this.relatedMessageId = relatedMessageId;
        this.relatedCallId = relatedCallId;
        this.createdAt = createdAt.toString();
    }

    public long getId() {
        return id;
    }

    public String getEventType() {
        return eventType;
    }

    public String getSeverity() {
        return severity;
    }

    public String getMessage() {
        return message;
    }

    public String getRelatedMessageId() {
        return relatedMessageId;
    }

    public String getRelatedCallId() {
        return relatedCallId;
    }

    public String getCreatedAt() {
        return createdAt;
    }
}
