package com.spamfilter.adminclient.servlet;

import com.fasterxml.jackson.databind.JsonNode;
import com.spamfilter.adminclient.auth.AdminSessionUtil;
import com.spamfilter.adminclient.db.WhitelistRepository;
import com.spamfilter.adminclient.model.Page;
import com.spamfilter.adminclient.model.WhitelistedSender;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * GET /whitelist (HTML page) and GET/POST/DELETE /api/whitelist (JSON) -
 * trusted senders in the shared whitelisted_senders table. Different shape
 * from the blocklist (sender_id/alias/description/added_by, no expiry), so
 * this doesn't share BlacklistServlet's markup.
 */
public class WhitelistServlet extends HttpServlet {

    private static final String API_PATH = "/api/whitelist";

    private final WhitelistRepository whitelistRepository;

    public WhitelistServlet(WhitelistRepository whitelistRepository) {
        this.whitelistRepository = whitelistRepository;
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        if (API_PATH.equals(req.getServletPath())) {
            apiSearch(req, resp);
            return;
        }

        if (WebPage.requireAdminLogin(req, resp) == null) {
            return;
        }

        String body;
        try {
            Page<WhitelistedSender> page = whitelistRepository.search(req.getParameter("query"), 1, 20);
            body = render(page);
        } catch (IllegalStateException e) {
            body = "<div class=\"card\"><div class=\"banner error\">Could not load the whitelist: "
                    + WebPage.escape(e.getMessage()) + "</div></div>";
        }

        resp.setContentType("text/html; charset=UTF-8");
        try (PrintWriter out = resp.getWriter()) {
            out.write(WebPage.shell("Whitelist", "Trusted senders - they bypass spam classification.",
                    "/whitelist", AdminSessionUtil.currentAdminEmail(req), body));
        }
    }

    private void apiSearch(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        int page = parseInt(req.getParameter("page"), 1);
        int pageSize = Math.min(100, parseInt(req.getParameter("pageSize"), 20));
        try {
            Page<WhitelistedSender> result = whitelistRepository.search(req.getParameter("query"), page, pageSize);
            JsonSupport.writeJson(resp, HttpServletResponse.SC_OK, Map.of(
                    "items", result.getItems(), "page", result.getPage(),
                    "pageSize", result.getPageSize(), "totalItems", result.getTotalItems()));
        } catch (IllegalStateException e) {
            JsonSupport.writeError(resp, HttpServletResponse.SC_SERVICE_UNAVAILABLE, e.getMessage());
        }
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String adminEmail = AdminSessionUtil.currentAdminEmail(req);
        if (adminEmail == null) {
            JsonSupport.writeError(resp, HttpServletResponse.SC_UNAUTHORIZED, "Not logged in");
            return;
        }

        JsonNode json = JsonSupport.readJson(req);
        if (json == null) {
            JsonSupport.writeError(resp, HttpServletResponse.SC_BAD_REQUEST, "Malformed request body");
            return;
        }

        String senderId = json.path("senderId").asText(null);
        if (senderId == null || senderId.isBlank()) {
            JsonSupport.writeError(resp, HttpServletResponse.SC_BAD_REQUEST, "senderId is required");
            return;
        }
        String aliasName = json.path("aliasName").isNull() ? null : json.path("aliasName").asText(null);
        String description = json.path("description").isNull() ? null : json.path("description").asText(null);

        try {
            whitelistRepository.add(senderId.trim(), aliasName, description, adminEmail);
            JsonSupport.writeJson(resp, HttpServletResponse.SC_CREATED, Map.of("ok", true));
        } catch (IllegalArgumentException e) {
            JsonSupport.writeError(resp, HttpServletResponse.SC_CONFLICT, e.getMessage());
        } catch (IllegalStateException e) {
            JsonSupport.writeError(resp, HttpServletResponse.SC_SERVICE_UNAVAILABLE, e.getMessage());
        }
    }

    @Override
    protected void doDelete(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        long id;
        try {
            id = Long.parseLong(req.getParameter("id"));
        } catch (NumberFormatException e) {
            JsonSupport.writeError(resp, HttpServletResponse.SC_BAD_REQUEST, "A numeric id is required");
            return;
        }
        try {
            whitelistRepository.delete(id);
            JsonSupport.writeJson(resp, HttpServletResponse.SC_OK, Map.of("ok", true));
        } catch (IllegalStateException e) {
            JsonSupport.writeError(resp, HttpServletResponse.SC_SERVICE_UNAVAILABLE, e.getMessage());
        }
    }

    private String render(Page<WhitelistedSender> pageResult) {
        String rows = pageResult.getItems().isEmpty()
                ? "<tr><td colspan=\"5\"><div class=\"empty-state\"><div class=\"glyph\">&#9711;</div>No trusted senders yet.</div></td></tr>"
                : pageResult.getItems().stream().map(this::row).collect(Collectors.joining());

        return """
                <div class="card">
                  <div class="card-head"><h2>Add a trusted sender</h2></div>
                  <form id="wlForm">
                    <div class="filter-bar" style="align-items:flex-start;">
                      <div class="field">
                        <label for="wlSenderId">Sender ID</label>
                        <input id="wlSenderId" name="senderId" required placeholder="e.g. 01556701043">
                      </div>
                      <div class="field">
                        <label for="wlAlias">Alias</label>
                        <input id="wlAlias" name="aliasName" placeholder="Optional">
                      </div>
                      <div class="field">
                        <label for="wlDescription">Description</label>
                        <input id="wlDescription" name="description" placeholder="Optional">
                      </div>
                      <button type="submit">Add</button>
                    </div>
                  </form>
                </div>

                <div class="card">
                  <div class="card-head">
                    <h2>Whitelist &middot; %d total</h2>
                    <input id="wlSearch" placeholder="Search sender, alias or description..." style="max-width:280px; margin-top:0;">
                  </div>
                  <div class="table-wrap" id="wlTableWrap">
                    <table>
                      <thead><tr><th>Sender</th><th>Alias</th><th>Description</th><th>Added by</th><th></th></tr></thead>
                      <tbody id="wlTableBody">%s</tbody>
                    </table>
                  </div>
                  <div class="pagination">
                    <span id="wlPaginationInfo">Page %d of %d &middot; %d total</span>
                    <button type="button" class="btn-ghost btn-sm" id="wlPrevBtn">&larr; Prev</button>
                    <button type="button" class="btn-ghost btn-sm" id="wlNextBtn">Next &rarr;</button>
                  </div>
                </div>

                <script>
                  (function() {
                    const els = {
                      search: document.getElementById('wlSearch'), wrap: document.getElementById('wlTableWrap'),
                      body: document.getElementById('wlTableBody'), info: document.getElementById('wlPaginationInfo'),
                      prev: document.getElementById('wlPrevBtn'), next: document.getElementById('wlNextBtn'),
                      form: document.getElementById('wlForm'),
                    };
                    const apiPath = "%s";
                    const state = { page: 1, pageSize: 20, query: '' };
                    let totalPages = 1;

                    function rowHtml(w) {
                      return `<tr data-id="${w.id}">
                        <td class="mono">${escapeHtml(w.senderId)}</td>
                        <td>${escapeHtml(w.aliasName || '')}</td>
                        <td>${escapeHtml(w.description || '')}</td>
                        <td class="muted">${escapeHtml(w.addedBy)}</td>
                        <td class="row-actions"><button type="button" class="btn-ghost btn-sm" data-del="${w.id}">Remove</button></td>
                      </tr>`;
                    }

                    async function load() {
                      els.wrap.classList.add('loading');
                      try {
                        const data = await api(apiPath + '?query=' + encodeURIComponent(state.query) + '&page=' + state.page + '&pageSize=' + state.pageSize);
                        totalPages = Math.max(1, Math.ceil(data.totalItems / data.pageSize));
                        els.body.innerHTML = data.items.length === 0
                          ? '<tr><td colspan="5"><div class="empty-state"><div class="glyph">&#9711;</div>No trusted senders yet.</div></td></tr>'
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

                    let searchTimer;
                    els.search.addEventListener('input', () => {
                      clearTimeout(searchTimer);
                      searchTimer = setTimeout(() => { state.query = els.search.value; state.page = 1; load(); }, 300);
                    });
                    els.prev.addEventListener('click', () => { if (state.page > 1) { state.page--; load(); } });
                    els.next.addEventListener('click', () => { if (state.page < totalPages) { state.page++; load(); } });

                    els.body.addEventListener('click', async (e) => {
                      const btn = e.target.closest('[data-del]');
                      if (!btn) return;
                      const id = btn.getAttribute('data-del');
                      const ok = await confirmModal('Remove trusted sender?', 'This sender will go back through normal spam classification.');
                      if (!ok) return;
                      try {
                        await api(apiPath + '?id=' + id, { method: 'DELETE' });
                        showToast('Removed', 'ok');
                        load();
                      } catch (err) {
                        showToast(err.message, 'error');
                      }
                    });

                    els.form.addEventListener('submit', async (e) => {
                      e.preventDefault();
                      const payload = {
                        senderId: document.getElementById('wlSenderId').value.trim(),
                        aliasName: document.getElementById('wlAlias').value.trim() || null,
                        description: document.getElementById('wlDescription').value.trim() || null,
                      };
                      try {
                        await api(apiPath, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(payload) });
                        showToast('Added', 'ok');
                        els.form.reset();
                        state.page = 1;
                        load();
                      } catch (err) {
                        showToast(err.message, 'error');
                      }
                    });
                    load();
                  })();
                </script>
                """.formatted(pageResult.getTotalItems(), rows, pageResult.getPage(), pageResult.getTotalPages(),
                pageResult.getTotalItems(), API_PATH);
    }

    private String row(WhitelistedSender w) {
        return """
                <tr data-id="%d">
                  <td class="mono">%s</td>
                  <td>%s</td>
                  <td>%s</td>
                  <td class="muted">%s</td>
                  <td class="row-actions"><button type="button" class="btn-ghost btn-sm" data-del="%d">Remove</button></td>
                </tr>
                """.formatted(w.getId(), WebPage.escape(w.getSenderId()),
                WebPage.escape(w.getAliasName() == null ? "" : w.getAliasName()),
                WebPage.escape(w.getDescription() == null ? "" : w.getDescription()),
                WebPage.escape(w.getAddedBy()), w.getId());
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
