package com.spamfilter.adminclient.model;

import java.time.Instant;

/**
 * A row from Neon's calls table, as written by the SIP client after
 * classification. Read-only - this app never writes to this table.
 */
public class CallRow {

    private final String id;
    private final String source;
    private final String destination;
    private final String startedAt;
    private final String endedAt;
    private final String classificationLabel;
    private final Double classificationScore;
    private final String status;

    public CallRow(String id, String source, String destination, Instant startedAt, Instant endedAt,
                    String classificationLabel, Double classificationScore, String status) {
        this.id = id;
        this.source = source;
        this.destination = destination;
        this.startedAt = startedAt == null ? null : startedAt.toString();
        this.endedAt = endedAt == null ? null : endedAt.toString();
        this.classificationLabel = classificationLabel;
        this.classificationScore = classificationScore;
        this.status = status;
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

    public String getStartedAt() {
        return startedAt;
    }

    public String getEndedAt() {
        return endedAt;
    }

    public String getClassificationLabel() {
        return classificationLabel;
    }

    public Double getClassificationScore() {
        return classificationScore;
    }

    public String getStatus() {
        return status;
    }
}
