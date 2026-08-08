package com.spamfilter.adminclient.servlet;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.spamfilter.adminclient.auth.AdminSessionUtil;
import com.spamfilter.adminclient.db.DashboardRepository;
import com.spamfilter.adminclient.model.DashboardStats;
import com.spamfilter.adminclient.model.LogEntry;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.stream.Collectors;

/**
 * GET / - the landing page: KPI tiles, a 14-day spam/ham chart, a spam-rate
 * ring, and a feed of the most recent system log entries.
 */
public class DashboardServlet extends HttpServlet {

    private final DashboardRepository dashboardRepository;
    private final ObjectMapper mapper = new ObjectMapper();

    public DashboardServlet(DashboardRepository dashboardRepository) {
        this.dashboardRepository = dashboardRepository;
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        if (WebPage.requireAdminLogin(req, resp) == null) {
            return;
        }

        String body;
        try {
            DashboardStats stats = dashboardRepository.load();
            body = render(stats);
        } catch (IllegalStateException e) {
            body = "<div class=\"card\"><div class=\"banner error\">Could not load dashboard data: "
                    + WebPage.escape(e.getMessage()) + "</div></div>";
        }

        resp.setContentType("text/html; charset=UTF-8");
        try (PrintWriter out = resp.getWriter()) {
            out.write(WebPage.shell("Dashboard", "Live overview of traffic, subscribers and moderation lists.",
                    "/", AdminSessionUtil.currentAdminEmail(req), body));
        }
    }

    private String render(DashboardStats stats) throws IOException {
        String chartJson = mapper.writeValueAsString(stats.getTimeseries());

        String recentLogs = stats.getRecentLogs().isEmpty()
                ? emptyState("No log entries yet.")
                : "<ul class=\"log-feed\">" + stats.getRecentLogs().stream().map(this::logRow)
                        .collect(Collectors.joining()) + "</ul>";

        return """
                <div class="kpi-grid">
                  %s
                </div>

                <div class="grid-2">
                  <div class="card">
                    <div class="card-head">
                      <h2>Messages, last 14 days</h2>
                      <div class="legend">
                        <span class="legend-item"><span class="legend-swatch" style="background:var(--good)"></span>Ham</span>
                        <span class="legend-item"><span class="legend-swatch" style="background:var(--critical)"></span>Spam</span>
                      </div>
                    </div>
                    <div id="messagesChart"></div>
                  </div>

                  <div>
                    <div class="card">
                      <div class="card-head"><h2>Spam rate today</h2></div>
                      <div class="ring-wrap">
                        <div id="spamRing" style="position:relative;"></div>
                        <div class="ring-caption">%d spam &middot; %d ham of %d messages today</div>
                      </div>
                    </div>
                    <div class="card">
                      <div class="card-head"><h2>Recent activity</h2><a class="link" href="/logs">View all &rarr;</a></div>
                      %s
                    </div>
                  </div>
                </div>

                <style>
                  .log-feed { list-style: none; margin: 0; padding: 0; display: flex; flex-direction: column; gap: 0.7rem; max-height: 340px; overflow-y: auto; }
                  .log-feed li { display: flex; gap: 0.6rem; align-items: flex-start; font-size: 0.85rem; }
                  .log-feed .log-msg { color: var(--text-secondary); }
                  .log-feed .log-time { display: block; color: var(--muted); font-size: 0.72rem; margin-top: 0.15rem; }
                </style>

                <script id="chartData" type="application/json">%s</script>
                <script>
                  function renderStackedBars(container, data) {
                    const width = 760, height = 220, padTop = 14, padBottom = 26, padLeft = 32, padRight = 8;
                    const plotW = width - padLeft - padRight, plotH = height - padTop - padBottom;
                    const n = Math.max(1, data.length);
                    const colW = plotW / n;
                    const barW = Math.min(26, colW * 0.55);
                    const max = Math.max(1, ...data.map(d => d.spam + d.ham));
                    const scaleY = v => (v / max) * plotH;
                    const baseline = padTop + plotH;

                    let grid = '';
                    const tickCount = Math.max(1, Math.min(4, max));
                    for (let t = 0; t <= tickCount; t++) {
                      const y = padTop + plotH - (t / tickCount) * plotH;
                      grid += `<line x1="${padLeft}" y1="${y}" x2="${width - padRight}" y2="${y}" stroke="var(--gridline)" stroke-width="1"/>`;
                      grid += `<text x="${padLeft - 6}" y="${y + 3}" text-anchor="end" font-size="9" fill="var(--muted)">${Math.round((t / tickCount) * max)}</text>`;
                    }

                    let bars = '', labels = '';
                    data.forEach((d, i) => {
                      const x = padLeft + i * colW + (colW - barW) / 2;
                      const hamH = scaleY(d.ham), spamH = scaleY(d.spam);
                      const gap = (d.ham > 0 && d.spam > 0) ? 2 : 0;
                      const hamY = baseline - hamH;
                      const spamY = hamY - gap - spamH;
                      bars += `<g class="bar-group" data-date="${d.date}" data-spam="${d.spam}" data-ham="${d.ham}">`;
                      if (hamH > 0) bars += `<rect x="${x}" y="${hamY}" width="${barW}" height="${Math.max(hamH,1)}" rx="4" fill="var(--good)"/>`;
                      if (spamH > 0) bars += `<rect x="${x}" y="${spamY}" width="${barW}" height="${Math.max(spamH,1)}" rx="4" fill="var(--critical)"/>`;
                      bars += `<rect x="${x - 4}" y="${padTop}" width="${barW + 8}" height="${plotH}" fill="transparent" class="hit"/>`;
                      bars += `</g>`;
                      const label = d.date.slice(5);
                      const everyNth = n > 10 ? (i %% 2 === 0) : true;
                      if (everyNth) labels += `<text x="${x + barW / 2}" y="${height - 8}" text-anchor="middle" font-size="9" fill="var(--muted)">${label}</text>`;
                    });

                    container.innerHTML = `<svg viewBox="0 0 ${width} ${height}" style="width:100%%;height:auto;display:block;overflow:visible;">
                      ${grid}
                      <line x1="${padLeft}" y1="${baseline}" x2="${width - padRight}" y2="${baseline}" stroke="var(--baseline)" stroke-width="1"/>
                      ${bars}${labels}
                    </svg>`;

                    let tooltip = document.getElementById('chartTooltip');
                    if (!tooltip) {
                      tooltip = document.createElement('div');
                      tooltip.id = 'chartTooltip';
                      tooltip.className = 'chart-tooltip';
                      document.body.appendChild(tooltip);
                    }
                    container.querySelectorAll('.bar-group').forEach(g => {
                      g.addEventListener('mousemove', (e) => {
                        const ds = g.dataset;
                        tooltip.innerHTML = `<strong>${ds.date}</strong>Spam: ${ds.spam} &middot; Ham: ${ds.ham}`;
                        tooltip.style.left = (e.clientX + 14) + 'px';
                        tooltip.style.top = (e.clientY - 12) + 'px';
                        tooltip.classList.add('show');
                      });
                      g.addEventListener('mouseleave', () => tooltip.classList.remove('show'));
                    });
                  }

                  function renderRing(container, percent) {
                    const size = 150, stroke = 14, r = (size - stroke) / 2, c = 2 * Math.PI * r;
                    const clamped = Math.max(0, Math.min(100, percent));
                    const offset = c * (1 - clamped / 100);
                    container.style.width = size + 'px';
                    container.style.height = size + 'px';
                    container.innerHTML = `
                      <svg width="${size}" height="${size}" viewBox="0 0 ${size} ${size}">
                        <circle cx="${size/2}" cy="${size/2}" r="${r}" fill="none" stroke="var(--gridline)" stroke-width="${stroke}"/>
                        <circle cx="${size/2}" cy="${size/2}" r="${r}" fill="none" stroke="var(--critical)" stroke-width="${stroke}"
                          stroke-linecap="round" stroke-dasharray="${c}" stroke-dashoffset="${offset}"
                          transform="rotate(-90 ${size/2} ${size/2})" style="transition: stroke-dashoffset 0.6s ease-out;"/>
                      </svg>
                      <div class="ring-value" style="position:absolute; inset:0; display:flex; align-items:center; justify-content:center;">${percent.toFixed(1)}%%</div>`;
                  }

                  function animateCount(el) {
                    const target = Number(el.dataset.value || 0);
                    const duration = 600;
                    const start = performance.now();
                    function frame(now) {
                      const t = Math.min(1, (now - start) / duration);
                      el.textContent = Math.round(target * (1 - Math.pow(1 - t, 3))).toLocaleString();
                      if (t < 1) requestAnimationFrame(frame);
                    }
                    requestAnimationFrame(frame);
                  }

                  document.querySelectorAll('.kpi-value[data-value]').forEach(animateCount);
                  renderStackedBars(document.getElementById('messagesChart'), JSON.parse(document.getElementById('chartData').textContent));
                  renderRing(document.getElementById('spamRing'), %s);
                </script>
                """.formatted(
                kpiTiles(stats),
                stats.getSpamToday(), stats.getHamToday(), stats.getMessagesToday(),
                recentLogs,
                chartJson,
                stats.getSpamRatePercent());
    }

    private String kpiTiles(DashboardStats s) {
        return "" +
                kpiTile("Subscribers", s.getSubscribersTotal(), "default",
                        s.getSubscribersActive() + " active &middot; " + s.getSubscribersSuspended()
                                + " suspended &middot; " + s.getSubscribersBlocked() + " blocked") +
                kpiTile("Messages today", s.getMessagesToday(), "default",
                        s.getSpamToday() + " spam &middot; " + s.getHamToday() + " ham") +
                kpiTile("Blocked today", s.getBlockedMessagesToday(), "critical",
                        s.getMessagesTotal() + " messages all time") +
                kpiTile("Calls today", s.getCallsToday(), "default",
                        s.getBlockedCallsToday() + " blocked &middot; " + s.getCallsTotal() + " all time") +
                kpiTile("Blocklist / Whitelist", s.getBlocklistSize() + s.getWhitelistSize(), "default",
                        s.getBlocklistSize() + " blocked numbers &middot; " + s.getWhitelistSize() + " trusted numbers") +
                kpiTile("Alerts, 24h", s.getErrorsLast24h() + s.getWarningsLast24h(),
                        s.getErrorsLast24h() > 0 ? "critical" : (s.getWarningsLast24h() > 0 ? "warning" : "good"),
                        s.getErrorsLast24h() + " errors &middot; " + s.getWarningsLast24h() + " warnings");
    }

    private String kpiTile(String label, long value, String tone, String foot) {
        return """
                <div class="kpi-tile tone-%s">
                  <div class="kpi-label">%s</div>
                  <div class="kpi-value" data-value="%d">0</div>
                  <div class="kpi-foot">%s</div>
                </div>
                """.formatted(tone, WebPage.escape(label), value, foot);
    }

    private String logRow(LogEntry log) {
        return """
                <li>
                  %s
                  <div>
                    <span class="log-msg">%s</span>
                    <span class="log-time">%s &middot; %s</span>
                  </div>
                </li>
                """.formatted(WebPage.severityBadge(log.getSeverity()), WebPage.escape(log.getMessage()),
                WebPage.escape(log.getEventType()), WebPage.escape(log.getCreatedAt()));
    }

    private String emptyState(String message) {
        return "<div class=\"empty-state\"><div class=\"glyph\">&#9679;</div>" + WebPage.escape(message) + "</div>";
    }
}
