package com.spamfilter.adminclient.model;

import java.util.List;

/** Everything the dashboard page needs, gathered by DashboardRepository. */
public class DashboardStats {

    private final long subscribersActive;
    private final long subscribersSuspended;
    private final long subscribersBlocked;

    private final long messagesTotal;
    private final long messagesToday;
    private final long spamToday;
    private final long hamToday;
    private final long blockedMessagesToday;

    private final long callsTotal;
    private final long callsToday;
    private final long blockedCallsToday;

    private final long blocklistSize;
    private final long whitelistSize;

    private final long errorsLast24h;
    private final long warningsLast24h;

    private final List<DayCount> timeseries;
    private final List<LogEntry> recentLogs;

    public DashboardStats(long subscribersActive, long subscribersSuspended, long subscribersBlocked,
                           long messagesTotal, long messagesToday, long spamToday, long hamToday,
                           long blockedMessagesToday, long callsTotal, long callsToday, long blockedCallsToday,
                           long blocklistSize, long whitelistSize, long errorsLast24h, long warningsLast24h,
                           List<DayCount> timeseries, List<LogEntry> recentLogs) {
        this.subscribersActive = subscribersActive;
        this.subscribersSuspended = subscribersSuspended;
        this.subscribersBlocked = subscribersBlocked;
        this.messagesTotal = messagesTotal;
        this.messagesToday = messagesToday;
        this.spamToday = spamToday;
        this.hamToday = hamToday;
        this.blockedMessagesToday = blockedMessagesToday;
        this.callsTotal = callsTotal;
        this.callsToday = callsToday;
        this.blockedCallsToday = blockedCallsToday;
        this.blocklistSize = blocklistSize;
        this.whitelistSize = whitelistSize;
        this.errorsLast24h = errorsLast24h;
        this.warningsLast24h = warningsLast24h;
        this.timeseries = timeseries;
        this.recentLogs = recentLogs;
    }

    public long getSubscribersActive() {
        return subscribersActive;
    }

    public long getSubscribersSuspended() {
        return subscribersSuspended;
    }

    public long getSubscribersBlocked() {
        return subscribersBlocked;
    }

    public long getSubscribersTotal() {
        return subscribersActive + subscribersSuspended + subscribersBlocked;
    }

    public long getMessagesTotal() {
        return messagesTotal;
    }

    public long getMessagesToday() {
        return messagesToday;
    }

    public long getSpamToday() {
        return spamToday;
    }

    public long getHamToday() {
        return hamToday;
    }

    public long getBlockedMessagesToday() {
        return blockedMessagesToday;
    }

    public double getSpamRatePercent() {
        return messagesToday == 0 ? 0.0 : (spamToday * 100.0) / messagesToday;
    }

    public long getCallsTotal() {
        return callsTotal;
    }

    public long getCallsToday() {
        return callsToday;
    }

    public long getBlockedCallsToday() {
        return blockedCallsToday;
    }

    public long getBlocklistSize() {
        return blocklistSize;
    }

    public long getWhitelistSize() {
        return whitelistSize;
    }

    public long getErrorsLast24h() {
        return errorsLast24h;
    }

    public long getWarningsLast24h() {
        return warningsLast24h;
    }

    public List<DayCount> getTimeseries() {
        return timeseries;
    }

    public List<LogEntry> getRecentLogs() {
        return recentLogs;
    }
}
