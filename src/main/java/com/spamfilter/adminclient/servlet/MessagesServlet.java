package com.spamfilter.adminclient.servlet;

import com.spamfilter.adminclient.auth.AdminSessionUtil;
import com.spamfilter.adminclient.db.MessageRepository;
import com.spamfilter.adminclient.model.MessageRow;
import com.spamfilter.adminclient.model.Page;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * GET /messages (HTML page) and GET /api/messages (JSON) - every message
 * across every subscriber, filterable by verdict/status/number. Read-only:
 * this app never writes to the messages table.
 */
public class MessagesServlet extends HttpServlet {

    private static final String API_PATH = "/api/messages";

    private final MessageRepository messageRepository;

    public MessagesServlet(MessageRepository messageRepository) {
        this.messageRepository = messageRepository;
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String label = req.getParameter("label");
        String status = req.getParameter("status");
        String query = req.getParameter("query");
        int page = parseInt(req.getParameter("page"), 1);
        int pageSize = Math.min(100, parseInt(req.getParameter("pageSize"), 25));

        if (API_PATH.equals(req.getServletPath())) {
            try {
                Page<MessageRow> result = messageRepository.search(label, status, query, page, pageSize);
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
            Page<MessageRow> result = messageRepository.search(label, status, query, page, pageSize);
            body = render(result, label, status, query);
        } catch (IllegalStateException e) {
            body = "<div class=\"card\"><div class=\"banner error\">Could not load messages: "
                    + WebPage.escape(e.getMessage()) + "</div></div>";
        }

        resp.setContentType("text/html; charset=UTF-8");
        try (PrintWriter out = resp.getWriter()) {
            out.write(WebPage.shell("Messages", "Every classified SMS across every subscriber.",
                    "/messages", AdminSessionUtil.currentAdminEmail(req), body));
        }
    }

    private String render(Page<MessageRow> result, String label, String status, String query) {
        String rows = result.getItems().isEmpty()
                ? "<tr><td colspan=\"6\"><div class=\"empty-state\"><div class=\"glyph\">&#9711;</div>No messages match these filters.</div></td></tr>"
                : result.getItems().stream().map(this::row).collect(Collectors.joining());

        return """
                <div class="card">
                  <div class="filter-bar">
                    <div class="field">
                      <label for="msgQuery">Source or destination</label>
                      <input id="msgQuery" placeholder="e.g. 2000" value="%s">
                    </div>
                    <div class="field">
                      <label for="msgLabel">Verdict</label>
                      <select id="msgLabel">
                        <option value="all">All</option>
                        <option value="spam"%s>Spam</option>
                        <option value="ham"%s>Ham</option>
                      </select>
                    </div>
                    <div class="field">
                      <label for="msgStatus">Status</label>
                      <select id="msgStatus">
                        <option value="all">All</option>
                        <option value="DELIVERED"%s>Delivered</option>
                        <option value="BLOCKED"%s>Blocked</option>
                      </select>
                    </div>
                    <button type="button" id="msgApply">Apply</button>
                  </div>

                  <div class="table-wrap" id="msgTableWrap">
                    <table>
                      <thead><tr><th>Source</th><th>Destination</th><th>Verdict</th><th>Status</th><th>Received</th><th></th></tr></thead>
                      <tbody id="msgTableBody">%s</tbody>
                    </table>
                  </div>
                  <div class="pagination">
                    <span id="msgPaginationInfo">Page %d of %d &middot; %d total</span>
                    <button type="button" class="btn-ghost btn-sm" id="msgPrevBtn">&larr; Prev</button>
                    <button type="button" class="btn-ghost btn-sm" id="msgNextBtn">Next &rarr;</button>
                  </div>
                </div>

                <script>
                  (function() {
                    const els = {
                      query: document.getElementById('msgQuery'), label: document.getElementById('msgLabel'),
                      status: document.getElementById('msgStatus'), wrap: document.getElementById('msgTableWrap'),
                      body: document.getElementById('msgTableBody'), info: document.getElementById('msgPaginationInfo'),
                      prev: document.getElementById('msgPrevBtn'), next: document.getElementById('msgNextBtn'),
                      apply: document.getElementById('msgApply'),
                    };
                    const apiPath = "%s";
                    const state = { page: 1, pageSize: 25 };
                    let totalPages = 1;

                    function rowHtml(m) {
                      const badge = m.classificationLabel === 'spam'
                        ? '<span class="badge badge-critical"><i class="dot"></i>Spam ' + m.classificationScore.toFixed(2) + '</span>'
                        : '<span class="badge badge-good"><i class="dot"></i>Ham ' + m.classificationScore.toFixed(2) + '</span>';
                      const statusBadge = m.status === 'BLOCKED'
                        ? '<span class="badge badge-critical"><i class="dot"></i>Blocked</span>'
                        : '<span class="badge badge-good"><i class="dot"></i>Delivered</span>';
                      const blacklistAction = (m.blacklisted || m.isBlacklisted)
                        ? ''
                        : '<button type="button" class="btn-ghost btn-sm" data-list="/api/blocklist" data-msisdn="' + escapeHtml(m.source) + '" data-name="Blacklist">Blacklist</button>';
                      return `<tr>
                        <td class="mono">${escapeHtml(m.source)}</td>
                        <td class="mono">${escapeHtml(m.destination)}</td>
                        <td>${badge}</td>
                        <td>${statusBadge}</td>
                        <td class="muted">${formatDateTime(m.receivedAt)}</td>
                        <td class="row-actions">
                          <button type="button" class="btn-ghost btn-sm" data-view-message="${escapeHtml(m.id || '')}" data-message="${escapeHtml(m.smsBody || '')}">View message</button>
                          ${blacklistAction}
                        </td>
                      </tr>`;
                    }

                    async function load() {
                      els.wrap.classList.add('loading');
                      try {
                        const params = new URLSearchParams({
                          label: els.label.value, status: els.status.value, query: els.query.value,
                          page: state.page, pageSize: state.pageSize
                        });
                        const data = await api(apiPath + '?' + params.toString());
                        totalPages = Math.max(1, Math.ceil(data.totalItems / data.pageSize));
                        els.body.innerHTML = data.items.length === 0
                          ? '<tr><td colspan="6"><div class="empty-state"><div class="glyph">&#9711;</div>No messages match these filters.</div></td></tr>'
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
                    els.body.addEventListener('click', (e) => {
                      const viewBtn = e.target.closest('[data-view-message]');
                      if (viewBtn) {
                        showTextModal('Message content', viewBtn.dataset.message || 'No message content available.');
                        return;
                      }

                      const btn = e.target.closest('[data-list]');
                      if (!btn) return;
                      quickAddToList(btn.dataset.list, btn.dataset.msisdn, btn.dataset.name);
                    });
                    load();
                  })();
                </script>
                """.formatted(
                WebPage.escape(query == null ? "" : query),
                "spam".equals(label) ? " selected" : "", "ham".equals(label) ? " selected" : "",
                "DELIVERED".equals(status) ? " selected" : "", "BLOCKED".equals(status) ? " selected" : "",
                rows, result.getPage(), result.getTotalPages(), result.getTotalItems(), API_PATH);
    }

    private String row(MessageRow m) {
        String blacklistAction = m.isBlacklisted()
                ? ""
                : "<button type=\"button\" class=\"btn-ghost btn-sm\" data-list=\"/api/blocklist\" data-msisdn=\"%s\" data-name=\"Blacklist\">Blacklist</button>".formatted(WebPage.escape(m.getSource()));

        return """
                <tr>
                  <td class="mono">%s</td>
                  <td class="mono">%s</td>
                  <td>%s</td>
                  <td>%s</td>
                  <td class="muted">%s</td>
                  <td class="row-actions">
                    <button type="button" class="btn-ghost btn-sm" data-view-message="%s" data-message="%s">View message</button>
                    %s
                  </td>
                </tr>
                """.formatted(WebPage.escape(m.getSource()), WebPage.escape(m.getDestination()),
                verdictBadge(m), WebPage.messageStatusBadge(m.getStatus()), WebPage.formatDateTime(m.getReceivedAt()),
                WebPage.escape(m.getId()), WebPage.escape(m.getSmsBody() == null ? "" : m.getSmsBody()),
                blacklistAction);
    }

    private String verdictBadge(MessageRow m) {
        String scoreText = String.format(java.util.Locale.ROOT, "%.2f", m.getClassificationScore());
        boolean spam = "spam".equalsIgnoreCase(m.getClassificationLabel());
        return WebPage.badge((spam ? "Spam " : "Ham ") + scoreText, spam ? "critical" : "good");
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
