package com.spamfilter.adminclient.servlet;

import com.spamfilter.adminclient.auth.AdminSessionUtil;
import com.spamfilter.adminclient.db.CallRepository;
import com.spamfilter.adminclient.model.CallRow;
import com.spamfilter.adminclient.model.Page;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * GET /calls (HTML page) and GET /api/calls (JSON) - every voice call across
 * every subscriber, filterable by status/number. Read-only: owned by the
 * SIP client, this app never writes to the calls table.
 */
public class CallsServlet extends HttpServlet {

    private static final String API_PATH = "/api/calls";

    private final CallRepository callRepository;

    public CallsServlet(CallRepository callRepository) {
        this.callRepository = callRepository;
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String status = req.getParameter("status");
        String query = req.getParameter("query");
        int page = parseInt(req.getParameter("page"), 1);
        int pageSize = Math.min(100, parseInt(req.getParameter("pageSize"), 25));

        if (API_PATH.equals(req.getServletPath())) {
            try {
                Page<CallRow> result = callRepository.search(status, query, page, pageSize);
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
            Page<CallRow> result = callRepository.search(status, query, page, pageSize);
            body = render(result, status, query);
        } catch (IllegalStateException e) {
            body = "<div class=\"card\"><div class=\"banner error\">Could not load calls: "
                    + WebPage.escape(e.getMessage()) + "</div></div>";
        }

        resp.setContentType("text/html; charset=UTF-8");
        try (PrintWriter out = resp.getWriter()) {
            out.write(WebPage.shell("Calls", "Every classified voice call across every subscriber.",
                    "/calls", AdminSessionUtil.currentAdminEmail(req), body));
        }
    }

    private String render(Page<CallRow> result, String status, String query) {
        String rows = result.getItems().isEmpty()
                ? "<tr><td colspan=\"5\"><div class=\"empty-state\"><div class=\"glyph\">&#9711;</div>No calls match these filters.</div></td></tr>"
                : result.getItems().stream().map(this::row).collect(Collectors.joining());

        return """
                <div class="card">
                  <div class="filter-bar">
                    <div class="field">
                      <label for="callQuery">Source or destination</label>
                      <input id="callQuery" placeholder="e.g. 2000" value="%s">
                    </div>
                    <div class="field">
                      <label for="callStatus">Status</label>
                      <select id="callStatus">
                        <option value="all">All</option>
                        <option value="COMPLETED"%s>Completed</option>
                        <option value="BLOCKED"%s>Blocked</option>
                        <option value="MISSED"%s>Missed</option>
                        <option value="FAILED"%s>Failed</option>
                      </select>
                    </div>
                    <button type="button" id="callApply">Apply</button>
                  </div>

                  <div class="table-wrap" id="callTableWrap">
                    <table>
                      <thead><tr><th>Source</th><th>Destination</th><th>Status</th><th>Started</th><th>Duration</th></tr></thead>
                      <tbody id="callTableBody">%s</tbody>
                    </table>
                  </div>
                  <div class="pagination">
                    <span id="callPaginationInfo">Page %d of %d &middot; %d total</span>
                    <button type="button" class="btn-ghost btn-sm" id="callPrevBtn">&larr; Prev</button>
                    <button type="button" class="btn-ghost btn-sm" id="callNextBtn">Next &rarr;</button>
                  </div>
                </div>

                <script>
                  (function() {
                    const els = {
                      query: document.getElementById('callQuery'), status: document.getElementById('callStatus'),
                      wrap: document.getElementById('callTableWrap'), body: document.getElementById('callTableBody'),
                      info: document.getElementById('callPaginationInfo'), prev: document.getElementById('callPrevBtn'),
                      next: document.getElementById('callNextBtn'), apply: document.getElementById('callApply'),
                    };
                    const apiPath = "%s";
                    const state = { page: 1, pageSize: 25 };
                    let totalPages = 1;

                    function statusBadge(s) {
                      if (s === 'BLOCKED') return '<span class="badge badge-critical"><i class="dot"></i>Blocked</span>';
                      if (s === 'MISSED') return '<span class="badge badge-warning"><i class="dot"></i>Missed</span>';
                      if (s === 'FAILED') return '<span class="badge badge-serious"><i class="dot"></i>Failed</span>';
                      return '<span class="badge badge-good"><i class="dot"></i>Completed</span>';
                    }

                    function duration(m) {
                      if (!m.endedAt) return '&mdash;';
                      const seconds = Math.max(0, Math.round((new Date(m.endedAt) - new Date(m.startedAt)) / 1000));
                      const mins = Math.floor(seconds / 60);
                      const secs = seconds %% 60;
                      return mins + 'm ' + secs + 's';
                    }

                    function rowHtml(m) {
                      return `<tr>
                        <td class="mono">${escapeHtml(m.source)}</td>
                        <td class="mono">${escapeHtml(m.destination)}</td>
                        <td>${statusBadge(m.status)}</td>
                        <td class="muted">${new Date(m.startedAt).toLocaleString()}</td>
                        <td class="muted">${duration(m)}</td>
                      </tr>`;
                    }

                    async function load() {
                      els.wrap.classList.add('loading');
                      try {
                        const params = new URLSearchParams({ status: els.status.value, query: els.query.value, page: state.page, pageSize: state.pageSize });
                        const data = await api(apiPath + '?' + params.toString());
                        totalPages = Math.max(1, Math.ceil(data.totalItems / data.pageSize));
                        els.body.innerHTML = data.items.length === 0
                          ? '<tr><td colspan="5"><div class="empty-state"><div class="glyph">&#9711;</div>No calls match these filters.</div></td></tr>'
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
                "COMPLETED".equals(status) ? " selected" : "", "BLOCKED".equals(status) ? " selected" : "",
                "MISSED".equals(status) ? " selected" : "", "FAILED".equals(status) ? " selected" : "",
                rows, result.getPage(), result.getTotalPages(), result.getTotalItems(), API_PATH);
    }

    private String row(CallRow c) {
        String duration = "&mdash;";
        if (c.getEndedAt() != null && c.getStartedAt() != null) {
            long seconds = Math.max(0, java.time.Duration.between(
                    java.time.Instant.parse(c.getStartedAt()), java.time.Instant.parse(c.getEndedAt())).getSeconds());
            duration = (seconds / 60) + "m " + (seconds % 60) + "s";
        }
        return """
                <tr>
                  <td class="mono">%s</td>
                  <td class="mono">%s</td>
                  <td>%s</td>
                  <td class="muted">%s</td>
                  <td class="muted">%s</td>
                </tr>
                """.formatted(WebPage.escape(c.getSource()), WebPage.escape(c.getDestination()),
                WebPage.callStatusBadge(c.getStatus()), WebPage.escape(c.getStartedAt()), duration);
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
