package com.spamfilter.adminclient.model;

import java.time.Instant;

/**
 * A row from Neon's messages table, as written by the SMPP server after
 * classification. Read-only - this app never writes to this table.
 */
public class MessageRow {

    private final String id;
    private final String source;
    private final String destination;
    private final String classificationLabel;
    private final double classificationScore;
    private final String status;
    private final String smppMessageId;
    private final String smsBody;
    private final boolean blacklisted;
    private final String receivedAt;

    public MessageRow(String id, String source, String destination, String classificationLabel,
                       double classificationScore, String status, String smppMessageId, String smsBody,
                       boolean blacklisted, Instant receivedAt) {
        this.id = id;
        this.source = source;
        this.destination = destination;
        this.classificationLabel = classificationLabel;
        this.classificationScore = classificationScore;
        this.status = status;
        this.smppMessageId = smppMessageId;
        this.smsBody = smsBody;
        this.blacklisted = blacklisted;
        this.receivedAt = receivedAt.toString();
    }

    public String getId() {
        return id;
    }

    public String getSource() {
        return source;
    }

    public String getDestination() {
        return destination;
    }

    public String getClassificationLabel() {
        return classificationLabel;
    }

    public double getClassificationScore() {
        return classificationScore;
    }

    public String getStatus() {
        return status;
    }

    public String getSmppMessageId() {
        return smppMessageId;
    }

    public String getSmsBody() {
        return smsBody;
    }

    public boolean isBlacklisted() {
        return blacklisted;
    }

    public boolean getBlacklisted() {
        return blacklisted;
    }

    public String getReceivedAt() {
        return receivedAt;
    }
}
