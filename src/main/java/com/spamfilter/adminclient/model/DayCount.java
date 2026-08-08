package com.spamfilter.adminclient.model;

/** One point in the dashboard's messages-per-day chart. */
public class DayCount {

    private final String date;
    private final long spam;
    private final long ham;

    public DayCount(String date, long spam, long ham) {
        this.date = date;
        this.spam = spam;
        this.ham = ham;
    }

    public String getDate() {
        return date;
    }

    public long getSpam() {
        return spam;
    }

    public long getHam() {
        return ham;
    }
}
