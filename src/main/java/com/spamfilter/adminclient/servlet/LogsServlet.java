package com.spamfilter.adminclient.servlet;

import com.spamfilter.adminclient.auth.AdminSessionUtil;
import com.spamfilter.adminclient.db.LogRepository;
import com.spamfilter.adminclient.model.LogEntry;
import com.spamfilter.adminclient.model.Page;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * GET /logs (HTML page) and GET /api/logs (JSON) - the system/event log
 * every other component writes to. Read-only: this app never writes here.
 */
public class LogsServlet extends HttpServlet {

    private static final String API_PATH = "/api/logs";

    private final LogRepository logRepository;

    public LogsServlet(LogRepository logRepository) {
        this.logRepository = logRepository;
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String severity = req.getParameter("severity");
        String query = req.getParameter("query");
        int page = parseInt(req.getParameter("page"), 1);
        int pageSize = Math.min(100, parseInt(req.getParameter("pageSize"), 25));

        if (API_PATH.equals(req.getServletPath())) {
            try {
                Page<LogEntry> result = logRepository.search(severity, query, page, pageSize);
                JsonSupport.writeJson(resp, HttpServletResponse.SC_OK, Map.of(
                        "items", result.getItems(), "page", result.getPage(),
                        "pageSize", result.getPageSize(), "totalItems", result.getTotalItems()));
            } catch (IllegalStateException e) {
                JsonSupport.writeError(resp, HttpServletResponse.SC_SERVICE_UNAVAILABLE, e.getMessage());
            }
            return;
        }

        if (WebPage.requireAdminLogin(req, resp) == null) {
            return;
        }

        String body;
        try {
            Page<LogEntry> result = logRepository.search(severity, query, page, pageSize);
            body = render(result, severity, query);
        } catch (IllegalStateException e) {
            body = "<div class=\"card\"><div class=\"banner error\">Could not load logs: "
                    + WebPage.escape(e.getMessage()) + "</div></div>";
        }

        resp.setContentType("text/html; charset=UTF-8");
        try (PrintWriter out = resp.getWriter()) {
            out.write(WebPage.shell("Logs", "System and event log written by every component.",
                    "/logs", AdminSessionUtil.currentAdminEmail(req), body));
        }
    }

    private String render(Page<LogEntry> result, String severity, String query) {
        String rows = result.getItems().isEmpty()
                ? "<tr><td colspan=\"4\"><div class=\"empty-state\"><div class=\"glyph\">&#9711;</div>No log entries match these filters.</div></td></tr>"
                : result.getItems().stream().map(this::row).collect(Collectors.joining());

        return """
                <div class="card">
                  <div class="filter-bar">
                    <div class="field">
                      <label for="logQuery">Event type or message</label>
                      <input id="logQuery" placeholder="e.g. smpp.bind" value="%s">
                    </div>
                    <div class="field">
                      <label for="logSeverity">Severity</label>
                      <select id="logSeverity">
                        <option value="all">All</option>
                        <option value="INFO"%s>Info</option>
                        <option value="WARN"%s>Warn</option>
                        <option value="ERROR"%s>Error</option>
                      </select>
                    </div>
                    <button type="button" id="logApply">Apply</button>
                  </div>

                  <div class="table-wrap" id="logTableWrap">
                    <table>
                      <thead><tr><th>Severity</th><th>Event</th><th>Message</th><th>When</th></tr></thead>
                      <tbody id="logTableBody">%s</tbody>
                    </table>
                  </div>
                  <div class="pagination">
                    <span id="logPaginationInfo">Page %d of %d &middot; %d total</span>
                    <button type="button" class="btn-ghost btn-sm" id="logPrevBtn">&larr; Prev</button>
                    <button type="button" class="btn-ghost btn-sm" id="logNextBtn">Next &rarr;</button>
                  </div>
                </div>

                <script>
                  (function() {
                    const els = {
                      query: document.getElementById('logQuery'), severity: document.getElementById('logSeverity'),
                      wrap: document.getElementById('logTableWrap'), body: document.getElementById('logTableBody'),
                      info: document.getElementById('logPaginationInfo'), prev: document.getElementById('logPrevBtn'),
                      next: document.getElementById('logNextBtn'), apply: document.getElementById('logApply'),
                    };
                    const apiPath = "%s";
                    const state = { page: 1, pageSize: 25 };
                    let totalPages = 1;

                    function severityBadge(s) {
                      if (s === 'ERROR') return '<span class="badge badge-critical"><i class="dot"></i>Error</span>';
                      if (s === 'WARN') return '<span class="badge badge-warning"><i class="dot"></i>Warn</span>';
                      return '<span class="badge badge-info"><i class="dot"></i>Info</span>';
                    }

                    function rowHtml(l) {
                      return `<tr>
                        <td>${severityBadge(l.severity)}</td>
                        <td class="mono">${escapeHtml(l.eventType)}</td>
                        <td>${escapeHtml(l.message)}</td>
                        <td class="muted">${new Date(l.createdAt).toLocaleString()}</td>
                      </tr>`;
                    }

                    async function load() {
                      els.wrap.classList.add('loading');
                      try {
                        const params = new URLSearchParams({ severity: els.severity.value, query: els.query.value, page: state.page, pageSize: state.pageSize });
                        const data = await api(apiPath + '?' + params.toString());
                        totalPages = Math.max(1, Math.ceil(data.totalItems / data.pageSize));
                        els.body.innerHTML = data.items.length === 0
                          ? '<tr><td colspan="4"><div class="empty-state"><div class="glyph">&#9711;</div>No log entries match these filters.</div></td></tr>'
                          : data.items.map(rowHtml).join('');
                        els.info.textContent = 'Page ' + data.page + ' of ' + totalPages + ' \\u00b7 ' + data.totalItems + ' total';
                        els.prev.disabled = data.page <= 1;
                        els.next.disabled = data.page >= totalPages;
                      } catch (err) {
                        showToast(err.message, 'error');
                      } finally {
                        els.wrap.classList.remove('loading');
                      }
                    }

                    els.apply.addEventListener('click', () => { state.page = 1; load(); });
                    els.query.addEventListener('keydown', (e) => { if (e.key === 'Enter') { state.page = 1; load(); } });
                    els.prev.addEventListener('click', () => { if (state.page > 1) { state.page--; load(); } });
                    els.next.addEventListener('click', () => { if (state.page < totalPages) { state.page++; load(); } });
                    load();
                  })();
                </script>
                """.formatted(
                WebPage.escape(query == null ? "" : query),
                "INFO".equals(severity) ? " selected" : "", "WARN".equals(severity) ? " selected" : "",
                "ERROR".equals(severity) ? " selected" : "",
                rows, result.getPage(), result.getTotalPages(), result.getTotalItems(), API_PATH);
    }

    private String row(LogEntry l) {
        return """
                <tr>
                  <td>%s</td>
                  <td class="mono">%s</td>
                  <td>%s</td>
                  <td class="muted">%s</td>
                </tr>
                """.formatted(WebPage.severityBadge(l.getSeverity()), WebPage.escape(l.getEventType()),
                WebPage.escape(l.getMessage()), WebPage.escape(l.getCreatedAt()));
    }

    private static int parseInt(String value, int fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            return Math.max(1, Integer.parseInt(value));
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}
