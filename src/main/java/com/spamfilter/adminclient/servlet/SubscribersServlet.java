package com.spamfilter.adminclient.servlet;

import com.fasterxml.jackson.databind.JsonNode;
import com.spamfilter.adminclient.auth.AdminSessionUtil;
import com.spamfilter.adminclient.db.SubscriberRepository;
import com.spamfilter.adminclient.model.Page;
import com.spamfilter.adminclient.model.Subscriber;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * GET /subscribers (HTML page), GET /api/subscribers (JSON) and POST
 * /api/subscribers (status change) - the users/numbers on the private
 * network. Reads broadly, writes only the `status` column.
 */
public class SubscribersServlet extends HttpServlet {

    private static final String API_PATH = "/api/subscribers";

    private final SubscriberRepository subscriberRepository;

    public SubscribersServlet(SubscriberRepository subscriberRepository) {
        this.subscriberRepository = subscriberRepository;
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String status = req.getParameter("status");
        String query = req.getParameter("query");
        int page = parseInt(req.getParameter("page"), 1);
        int pageSize = Math.min(100, parseInt(req.getParameter("pageSize"), 25));

        if (API_PATH.equals(req.getServletPath())) {
            try {
                Page<Subscriber> result = subscriberRepository.search(status, query, page, pageSize);
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
            Page<Subscriber> result = subscriberRepository.search(status, query, page, pageSize);
            body = render(result, status, query);
        } catch (IllegalStateException e) {
            body = "<div class=\"card\"><div class=\"banner error\">Could not load subscribers: "
                    + WebPage.escape(e.getMessage()) + "</div></div>";
        }

        resp.setContentType("text/html; charset=UTF-8");
        try (PrintWriter out = resp.getWriter()) {
            out.write(WebPage.shell("Subscribers", "The numbers on the private network, and their standing.",
                    "/subscribers", AdminSessionUtil.currentAdminEmail(req), body));
        }
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        JsonNode json = JsonSupport.readJson(req);
        if (json == null) {
            JsonSupport.writeError(resp, HttpServletResponse.SC_BAD_REQUEST, "Malformed request body");
            return;
        }

        String msisdn = json.path("msisdn").asText(null);
        String imsi = json.path("imsi").asText(null);
        String displayName = json.path("displayName").asText(null);
        String status = json.path("status").asText(null);

        if (msisdn != null && !msisdn.isBlank()) {
            try {
                subscriberRepository.add(msisdn, imsi, displayName, status == null || status.isBlank() ? "ACTIVE" : status);
                JsonSupport.writeJson(resp, HttpServletResponse.SC_CREATED, Map.of("ok", true));
                return;
            } catch (IllegalArgumentException e) {
                JsonSupport.writeError(resp, HttpServletResponse.SC_BAD_REQUEST, e.getMessage());
                return;
            } catch (IllegalStateException e) {
                JsonSupport.writeError(resp, HttpServletResponse.SC_SERVICE_UNAVAILABLE, e.getMessage());
                return;
            }
        }

        String id = json.path("id").asText(null);
        if (id == null || status == null) {
            JsonSupport.writeError(resp, HttpServletResponse.SC_BAD_REQUEST, "id and status are required");
            return;
        }
        try {
            subscriberRepository.updateStatus(id, status);
            JsonSupport.writeJson(resp, HttpServletResponse.SC_OK, Map.of("ok", true));
        } catch (IllegalArgumentException e) {
            JsonSupport.writeError(resp, HttpServletResponse.SC_BAD_REQUEST, e.getMessage());
        } catch (IllegalStateException e) {
            JsonSupport.writeError(resp, HttpServletResponse.SC_SERVICE_UNAVAILABLE, e.getMessage());
        }
    }

    @Override
    protected void doDelete(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String id = req.getParameter("id");
        String msisdn = req.getParameter("msisdn");

        if ((id == null || id.isBlank()) && (msisdn == null || msisdn.isBlank())) {
            JsonSupport.writeError(resp, HttpServletResponse.SC_BAD_REQUEST, "id or msisdn is required");
            return;
        }

        try {
            if (msisdn != null && !msisdn.isBlank()) {
                subscriberRepository.deleteByMsisdn(msisdn);
            } else {
                subscriberRepository.deleteById(id);
            }
            JsonSupport.writeJson(resp, HttpServletResponse.SC_OK, Map.of("ok", true));
        } catch (IllegalArgumentException e) {
            JsonSupport.writeError(resp, HttpServletResponse.SC_BAD_REQUEST, e.getMessage());
        } catch (IllegalStateException e) {
            JsonSupport.writeError(resp, HttpServletResponse.SC_SERVICE_UNAVAILABLE, e.getMessage());
        }
    }

    private String render(Page<Subscriber> result, String status, String query) {
        String rows = result.getItems().isEmpty()
                ? "<tr><td colspan=\"6\"><div class=\"empty-state\"><div class=\"glyph\">&#9711;</div>No subscribers match these filters.</div></td></tr>"
                : result.getItems().stream().map(this::row).collect(Collectors.joining());

        return """
                <div class="card">
                  <div class="card-head"><h2>Add a subscriber</h2></div>
                  <form id="subForm">
                    <div class="filter-bar" style="align-items:flex-start;">
                      <div class="field">
                        <label for="subMsisdn">MSISDN</label>
                        <input id="subMsisdn" name="msisdn" required placeholder="e.g. +1234567890">
                      </div>
                      <div class="field">
                        <label for="subImsi">IMSI</label>
                        <input id="subImsi" name="imsi" placeholder="Optional">
                      </div>
                      <div class="field">
                        <label for="subDisplayName">Display name</label>
                        <input id="subDisplayName" name="displayName" placeholder="Optional">
                      </div>
                      <div class="field">
                        <label for="subStatusCreate">Status</label>
                        <select id="subStatusCreate" name="status">
                          <option value="ACTIVE">Active</option>
                          <option value="SUSPENDED">Suspended</option>
                          <option value="BLOCKED">Blocked</option>
                        </select>
                      </div>
                      <button type="submit">Add subscriber</button>
                    </div>
                  </form>
                </div>

                <div class="card">
                  <div class="filter-bar">
                    <div class="field">
                      <label for="subQuery">MSISDN, name or IMSI</label>
                      <input id="subQuery" placeholder="e.g. 2000" value="%s">
                    </div>
                    <div class="field">
                      <label for="subStatus">Status</label>
                      <select id="subStatus">
                        <option value="all">All</option>
                        <option value="ACTIVE"%s>Active</option>
                        <option value="SUSPENDED"%s>Suspended</option>
                        <option value="BLOCKED"%s>Blocked</option>
                      </select>
                    </div>
                    <button type="button" id="subApply">Apply</button>
                  </div>

                  <div class="table-wrap" id="subTableWrap">
                    <table>
                      <thead><tr><th>MSISDN</th><th>Name</th><th>IMSI</th><th>Status</th><th>Joined</th><th></th></tr></thead>
                      <tbody id="subTableBody">%s</tbody>
                    </table>
                  </div>
                  <div class="pagination">
                    <span id="subPaginationInfo">Page %d of %d &middot; %d total</span>
                    <button type="button" class="btn-ghost btn-sm" id="subPrevBtn">&larr; Prev</button>
                    <button type="button" class="btn-ghost btn-sm" id="subNextBtn">Next &rarr;</button>
                  </div>
                </div>

                <script>
                  (function() {
                    const els = {
                      query: document.getElementById('subQuery'), status: document.getElementById('subStatus'),
                      wrap: document.getElementById('subTableWrap'), body: document.getElementById('subTableBody'),
                      info: document.getElementById('subPaginationInfo'), prev: document.getElementById('subPrevBtn'),
                      next: document.getElementById('subNextBtn'), apply: document.getElementById('subApply'),
                      form: document.getElementById('subForm'),
                    };
                    const state = { page: 1, pageSize: 25 };
                    let totalPages = 1;

                    function statusBadge(s) {
                      if (s === 'SUSPENDED') return '<span class="badge badge-warning"><i class="dot"></i>Suspended</span>';
                      if (s === 'BLOCKED') return '<span class="badge badge-critical"><i class="dot"></i>Blocked</span>';
                      return '<span class="badge badge-good"><i class="dot"></i>Active</span>';
                    }

                    function statusActions(sub) {
                      const options = ['ACTIVE', 'SUSPENDED', 'BLOCKED'].filter(s => s !== sub.status);
                      return options.map(s =>
                        `<button type="button" class="btn-ghost btn-sm" data-status-id="${sub.id}" data-status-to="${s}">${s.charAt(0) + s.slice(1).toLowerCase()}</button>`
                      ).join('');
                    }

                    function rowHtml(s) {
                      return `<tr data-row-id="${s.id}">
                        <td class="mono">${escapeHtml(s.msisdn)}</td>
                        <td>${escapeHtml(s.displayName || '')}</td>
                        <td class="mono muted">${escapeHtml(s.imsi || '')}</td>
                        <td>${statusBadge(s.status)}</td>
                        <td class="muted">${formatDateTime(s.createdAt)}</td>
                        <td class="row-actions">
                          ${statusActions(s)}
                          <button type="button" class="btn-ghost btn-sm" data-del-msisdn="${escapeHtml(s.msisdn)}">Remove</button>
                          <button type="button" class="btn-ghost btn-sm" data-list="/api/blocklist" data-msisdn="${escapeHtml(s.msisdn)}" data-name="Blacklist">Blacklist</button>
                          <button type="button" class="btn-ghost btn-sm" data-list="/api/whitelist" data-msisdn="${escapeHtml(s.msisdn)}" data-name="Whitelist">Whitelist</button>
                        </td>
                      </tr>`;
                    }

                    const apiPath = "%s";
                    async function load() {
                      els.wrap.classList.add('loading');
                      try {
                        const params = new URLSearchParams({ status: els.status.value, query: els.query.value, page: state.page, pageSize: state.pageSize });
                        const data = await api(apiPath + '?' + params.toString());
                        totalPages = Math.max(1, Math.ceil(data.totalItems / data.pageSize));
                        els.body.innerHTML = data.items.length === 0
                          ? '<tr><td colspan="6"><div class="empty-state"><div class="glyph">&#9711;</div>No subscribers match these filters.</div></td></tr>'
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

                    els.form.addEventListener('submit', async (e) => {
                      e.preventDefault();
                      const payload = {
                        msisdn: document.getElementById('subMsisdn').value.trim(),
                        imsi: document.getElementById('subImsi').value.trim() || null,
                        displayName: document.getElementById('subDisplayName').value.trim() || null,
                        status: document.getElementById('subStatusCreate').value,
                      };
                      try {
                        await api(apiPath, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(payload) });
                        showToast('Subscriber added', 'ok');
                        els.form.reset();
                        state.page = 1;
                        load();
                      } catch (err) {
                        showToast(err.message, 'error');
                      }
                    });

                    els.apply.addEventListener('click', () => { state.page = 1; load(); });
                    els.query.addEventListener('keydown', (e) => { if (e.key === 'Enter') { state.page = 1; load(); } });
                    els.prev.addEventListener('click', () => { if (state.page > 1) { state.page--; load(); } });
                    els.next.addEventListener('click', () => { if (state.page < totalPages) { state.page++; load(); } });
                    els.body.addEventListener('click', async (e) => {
                      const listBtn = e.target.closest('[data-list]');
                      if (listBtn) { quickAddToList(listBtn.dataset.list, listBtn.dataset.msisdn, listBtn.dataset.name); return; }

                      const removeBtn = e.target.closest('[data-del-msisdn]');
                      if (removeBtn) {
                        const msisdn = removeBtn.dataset.delMsisdn;
                        const ok = await confirmModal('Remove subscriber?', 'This will delete the subscriber record for ' + msisdn + '.');
                        if (!ok) return;
                        try {
                          await api(apiPath + '?msisdn=' + encodeURIComponent(msisdn), { method: 'DELETE' });
                          showToast('Subscriber removed', 'ok');
                          load();
                        } catch (err) {
                          showToast(err.message, 'error');
                        }
                        return;
                      }

                      const statusBtn = e.target.closest('[data-status-id]');
                      if (!statusBtn) return;
                      const id = statusBtn.dataset.statusId, to = statusBtn.dataset.statusTo;
                      const ok = await confirmModal('Change status?', 'Set this subscriber to ' + to + '.');
                      if (!ok) return;
                      try {
                        await api(apiPath, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ id, status: to }) });
                        showToast('Status updated', 'ok');
                        load();
                      } catch (err) {
                        showToast(err.message, 'error');
                      }
                    });
                    load();
                  })();
                </script>
                """.formatted(
                WebPage.escape(query == null ? "" : query),
                "ACTIVE".equals(status) ? " selected" : "", "SUSPENDED".equals(status) ? " selected" : "",
                "BLOCKED".equals(status) ? " selected" : "",
                rows, result.getPage(), result.getTotalPages(), result.getTotalItems(), API_PATH);
    }

    private String row(Subscriber s) {
        String actions = java.util.stream.Stream.of("ACTIVE", "SUSPENDED", "BLOCKED")
                .filter(v -> !v.equalsIgnoreCase(s.getStatus()))
                .map(v -> "<button type=\"button\" class=\"btn-ghost btn-sm\" data-status-id=\"" + WebPage.escape(s.getId())
                        + "\" data-status-to=\"" + v + "\">" + v.charAt(0) + v.substring(1).toLowerCase() + "</button>")
                .collect(Collectors.joining());

        return """
                <tr data-row-id="%s">
                  <td class="mono">%s</td>
                  <td>%s</td>
                  <td class="mono muted">%s</td>
                  <td>%s</td>
                  <td class="muted">%s</td>
                  <td class="row-actions">
                    %s
                    <button type="button" class="btn-ghost btn-sm" data-del-msisdn="%s">Remove</button>
                    <button type="button" class="btn-ghost btn-sm" data-list="/api/blocklist" data-msisdn="%s" data-name="Blacklist">Blacklist</button>
                    <button type="button" class="btn-ghost btn-sm" data-list="/api/whitelist" data-msisdn="%s" data-name="Whitelist">Whitelist</button>
                  </td>
                </tr>
                """.formatted(WebPage.escape(s.getId()), WebPage.escape(s.getMsisdn()),
                WebPage.escape(s.getDisplayName() == null ? "" : s.getDisplayName()),
                WebPage.escape(s.getImsi() == null ? "" : s.getImsi()),
                WebPage.subscriberStatusBadge(s.getStatus()), WebPage.formatDateTime(s.getCreatedAt()),
                actions, WebPage.escape(s.getMsisdn()), WebPage.escape(s.getMsisdn()), WebPage.escape(s.getMsisdn()));
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
