package com.spamfilter.adminclient.servlet;

import com.spamfilter.adminclient.auth.AdminSessionUtil;
import com.spamfilter.adminclient.model.BlocklistEntry;
import com.spamfilter.adminclient.model.Page;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * Shared HTML shell (sidebar + top bar + theme) so every admin page looks
 * consistent, plus a login guard reused by every page, small view-helpers
 * (badges) shared across servlets, and the JS runtime (toasts, confirm
 * modal, fetch helpers) every page relies on.
 */
final class WebPage {

    private WebPage() {
    }

    static String requireAdminLogin(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String adminId = AdminSessionUtil.currentAdminId(req);
        if (adminId == null) {
            resp.sendRedirect("/login");
            return null;
        }
        return adminId;
    }

    record NavItem(String path, String icon, String label) {
    }

    private static final NavItem[] NAV = {
            new NavItem("/", "◈", "Dashboard"),
            new NavItem("/messages", "✉", "Messages"),
            new NavItem("/calls", "☎", "Calls"),
            new NavItem("/blacklist", "⛔", "Blacklist"),
            new NavItem("/whitelist", "✓", "Whitelist"),
            new NavItem("/logs", "≡", "Logs"),
    };

    static String shell(String title, String subtitle, String activePath, String adminUsername, String bodyHtml) {
        StringBuilder nav = new StringBuilder();
        for (NavItem item : NAV) {
            boolean active = item.path().equals(activePath);
            nav.append("""
                    <a class="nav-item%s" href="%s">
                      <span class="nav-icon">%s</span>
                      <span class="nav-label">%s</span>
                    </a>
                    """.formatted(active ? " active" : "", item.path(), item.icon(), item.label()));
        }

        String header = """
                <header class="topbar">
                  <div>
                    <h1 class="page-title">%s</h1>
                    <p class="page-subtitle">%s</p>
                  </div>
                </header>
                """.formatted(escape(title), escape(subtitle));

        return """
                <!doctype html>
                <html lang="en">
                <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1">
                <title>%s &middot; Warden Admin</title>
                <style>%s</style>
                </head>
                <body>
                <div class="app-shell">
                  <aside class="sidebar">
                    <a class="brand" href="/">
                      <span class="brand-mark">&#128737;</span>
                      <span class="brand-text">Warden</span>
                    </a>
                    <nav class="nav">
                      %s
                    </nav>
                    <div class="sidebar-footer">
                      <div class="admin-chip">
                        <span class="admin-avatar">%s</span>
                        <span class="admin-name">%s</span>
                      </div>
                      <a class="nav-item logout" href="/logout">
                        <span class="nav-icon">&#10148;</span>
                        <span class="nav-label">Log out</span>
                      </a>
                    </div>
                  </aside>
                  <div class="main">
                    %s
                    <main class="content">
                      %s
                    </main>
                  </div>
                </div>
                <div id="toastStack" class="toast-stack"></div>
                <div id="modalRoot"></div>
                <script>%s</script>
                </body>
                </html>
                """.formatted(escape(title), CSS, nav, initials(adminUsername), escape(adminUsername),
                header, bodyHtml, RUNTIME_JS);
    }

    static String bareShell(String title, String bodyHtml) {
        return """
                <!doctype html>
                <html lang="en">
                <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1">
                <title>%s &middot; Warden Admin</title>
                <style>%s</style>
                </head>
                <body class="auth-body">
                %s
                </body>
                </html>
                """.formatted(escape(title), CSS, bodyHtml);
    }

    private static String initials(String username) {
        if (username == null || username.isBlank()) {
            return "?";
        }
        return username.substring(0, 1).toUpperCase();
    }

    static String escape(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }

    static String formatDateTime(String isoDate) {
        if (isoDate == null || isoDate.isBlank()) {
            return "&mdash;";
        }
        try {
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd MMM yyyy, h:mm a", Locale.ENGLISH)
                    .withZone(ZoneId.systemDefault());
            return formatter.format(Instant.parse(isoDate));
        } catch (Exception e) {
            return isoDate;
        }
    }

    /** role: good | warning | serious | critical | info | muted */
    static String badge(String label, String role) {
        return "<span class=\"badge badge-%s\"><i class=\"dot\"></i>%s</span>".formatted(role, escape(label));
    }

    static String spamHamBadge(String label) {
        return "spam".equalsIgnoreCase(label) ? badge("Spam", "critical") : badge("Ham", "good");
    }

    static String messageStatusBadge(String status) {
        return "BLOCKED".equalsIgnoreCase(status) ? badge("Blocked", "critical") : badge("Delivered", "good");
    }

    static String severityBadge(String severity) {
        return switch (severity == null ? "" : severity.toUpperCase()) {
            case "ERROR" -> badge("Error", "critical");
            case "WARN" -> badge("Warn", "warning");
            default -> badge("Info", "info");
        };
    }

    static String subscriberStatusBadge(String status) {
        return switch (status == null ? "" : status.toUpperCase()) {
            case "SUSPENDED" -> badge("Suspended", "warning");
            case "BLOCKED" -> badge("Blocked", "critical");
            default -> badge("Active", "good");
        };
    }

    static String callStatusBadge(String status) {
        return switch (status == null ? "" : status.toUpperCase()) {
            case "BLOCKED" -> badge("Blocked", "critical");
            case "MISSED" -> badge("Missed", "warning");
            case "FAILED" -> badge("Failed", "serious");
            default -> badge("Completed", "good");
        };
    }

    /**
     * Body markup for the Blacklist page (msisdn/reason/expiry against the
     * shared blocklist table). Whitelist has a different shape
     * (whitelisted_senders: sender_id/alias/description/added_by, no
     * expiry) so WhitelistServlet builds its own markup instead of sharing this.
     */
    static String entryListPage(String kind, String apiUrl, Page<BlocklistEntry> pageResult) {
        String rows = pageResult.getItems().isEmpty()
                ? emptyRow()
                : pageResult.getItems().stream().map(WebPage::entryRow).collect(Collectors.joining());

        return """
                <div class="card">
                  <div class="card-head"><h2>Add to %s</h2></div>
                  <form id="listForm">
                    <div class="filter-bar" style="align-items:flex-start;">
                      <div class="field">
                        <label for="entryMsisdn">MSISDN</label>
                        <input id="entryMsisdn" name="msisdn" required placeholder="e.g. 2000">
                      </div>
                      <div class="field">
                        <label for="entryReason">Reason</label>
                        <input id="entryReason" name="reason" placeholder="Optional">
                      </div>
                      <div class="field" style="max-width:220px;">
                        <label for="entryExpires">Expires (optional)</label>
                        <input id="entryExpires" name="expiresAt" type="datetime-local">
                      </div>
                      <button type="submit">Add</button>
                    </div>
                  </form>
                </div>

                <div class="card">
                  <div class="card-head">
                    <h2>%s &middot; %d total</h2>
                    <input id="listSearch" placeholder="Search MSISDN or reason..." style="max-width:260px; margin-top:0;">
                  </div>
                  <div class="table-wrap" id="listTableWrap">
                    <table>
                      <thead><tr><th>MSISDN</th><th>Reason</th><th>Added</th><th>Expires</th><th></th></tr></thead>
                      <tbody id="listTableBody">%s</tbody>
                    </table>
                  </div>
                  <div class="pagination">
                    <span id="listPaginationInfo">Page %d of %d &middot; %d total</span>
                    <button type="button" class="btn-ghost btn-sm" id="listPrevBtn">&larr; Prev</button>
                    <button type="button" class="btn-ghost btn-sm" id="listNextBtn">Next &rarr;</button>
                  </div>
                </div>

                <script>window.addEventListener('DOMContentLoaded', () => initEntryListPage(%s));</script>
                """.formatted(escape(kind), escape(kind), pageResult.getTotalItems(), rows,
                pageResult.getPage(), pageResult.getTotalPages(), pageResult.getTotalItems(),
                jsString(apiUrl));
    }

    private static String entryRow(BlocklistEntry e) {
        return """
                <tr data-id="%d">
                  <td class="mono">%s</td>
                  <td>%s</td>
                  <td class="muted">%s</td>
                  <td class="muted">%s</td>
                  <td class="row-actions"><button type="button" class="btn-ghost btn-sm" data-del="%d">Remove</button></td>
                </tr>
                """.formatted(e.getId(), escape(e.getMsisdn()), escape(e.getReason() == null ? "" : e.getReason()),
                escape(e.getCreatedAt()), e.getExpiresAt() == null ? "&mdash;" : escape(e.getExpiresAt()), e.getId());
    }

    private static String emptyRow() {
        return "<tr><td colspan=\"5\"><div class=\"empty-state\"><div class=\"glyph\">&#9711;</div>No entries found.</div></td></tr>";
    }

    private static String jsString(String s) {
        return "'" + s.replace("\\", "\\\\").replace("'", "\\'") + "'";
    }

    private static final String CSS = """
            :root {
              color-scheme: dark;
              --bg: #0a0b12;
              --bg-elevated: #10121c;
              --surface: #151827;
              --surface-2: #1a1e30;
              --chart-surface: #1a1a19;
              --border: rgba(255,255,255,0.08);
              --border-strong: rgba(255,255,255,0.16);
              --text: #f5f6fa;
              --text-secondary: #c3c2b7;
              --muted: #898781;
              --gridline: #2c2c2a;
              --baseline: #45455f;
              --accent-a: #7c5cff;
              --accent-b: #22d3ee;
              --accent-grad: linear-gradient(135deg, var(--accent-a), var(--accent-b));
              --good: #0ca30c;
              --warning: #fab219;
              --serious: #ec835a;
              --critical: #d03b3b;
              --info: #3987e5;
              --radius: 14px;
              --radius-lg: 20px;
              --shadow-glow: 0 8px 30px rgba(124,92,255,0.18);
            }
            * { box-sizing: border-box; }
            html, body { height: 100%; }
            body {
              margin: 0; font-family: -apple-system, "Segoe UI", Roboto, sans-serif;
              background:
                radial-gradient(1200px 600px at 90% -10%, rgba(124,92,255,0.16), transparent 60%),
                radial-gradient(900px 500px at -10% 10%, rgba(34,211,238,0.10), transparent 55%),
                var(--bg);
              color: var(--text); -webkit-font-smoothing: antialiased;
            }
            a { color: inherit; }
            ::selection { background: rgba(124,92,255,0.35); }

            .app-shell { display: flex; min-height: 100vh; }
            .sidebar {
              width: 236px; flex-shrink: 0; background: var(--bg-elevated);
              border-right: 1px solid var(--border); padding: 1.4rem 1rem;
              display: flex; flex-direction: column; position: sticky; top: 0; height: 100vh;
            }
            .brand { display: flex; align-items: center; gap: 0.6rem; text-decoration: none; padding: 0 0.4rem 1.4rem; }
            .brand-mark { font-size: 1.4rem; filter: drop-shadow(0 0 10px rgba(124,92,255,0.6)); }
            .brand-text {
              font-weight: 800; font-size: 1.15rem; letter-spacing: 0.02em;
              background: var(--accent-grad); -webkit-background-clip: text; background-clip: text; color: transparent;
            }
            .nav { display: flex; flex-direction: column; gap: 0.2rem; margin-top: 0.4rem; }
            .nav-item {
              display: flex; align-items: center; gap: 0.75rem; padding: 0.65rem 0.75rem; border-radius: 10px;
              text-decoration: none; color: var(--text-secondary); font-size: 0.92rem; font-weight: 600;
              border: 1px solid transparent; transition: background 0.15s, color 0.15s, border-color 0.15s;
            }
            .nav-item:hover { background: rgba(255,255,255,0.05); color: var(--text); }
            .nav-item.active {
              color: var(--text); background: linear-gradient(90deg, rgba(124,92,255,0.18), rgba(34,211,238,0.06));
              border-color: rgba(124,92,255,0.35);
            }
            .nav-icon { width: 1.2rem; text-align: center; opacity: 0.9; }
            .sidebar-footer { margin-top: auto; padding-top: 1rem; border-top: 1px solid var(--border); }
            .admin-chip { display: flex; align-items: center; gap: 0.6rem; padding: 0.4rem 0.5rem 0.8rem; }
            .admin-avatar {
              width: 30px; height: 30px; border-radius: 50%; display: flex; align-items: center; justify-content: center;
              background: var(--accent-grad); font-weight: 700; font-size: 0.85rem; color: #0a0b12;
            }
            .admin-name { font-size: 0.85rem; color: var(--text-secondary); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
            .nav-item.logout:hover { color: var(--critical); }

            .main { flex: 1; min-width: 0; display: flex; flex-direction: column; }
            .topbar { padding: 1.75rem 2rem 0.5rem; }
            .page-title { margin: 0; font-size: 1.5rem; font-weight: 800; letter-spacing: -0.01em; }
            .page-subtitle { margin: 0.25rem 0 0; color: var(--muted); font-size: 0.9rem; }
            .content { padding: 1.25rem 2rem 3rem; flex: 1; }

            .card {
              background: linear-gradient(180deg, rgba(255,255,255,0.035), rgba(255,255,255,0.015));
              backdrop-filter: blur(16px); border: 1px solid var(--border); border-radius: var(--radius-lg);
              padding: 1.4rem; margin-bottom: 1.25rem;
            }
            .card-head { display: flex; align-items: baseline; justify-content: space-between; margin-bottom: 1rem; gap: 1rem; flex-wrap: wrap; }
            .card-head h2 { margin: 0; font-size: 1.02rem; font-weight: 700; }
            .card-head .muted { margin: 0; }

            .kpi-grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(190px, 1fr)); gap: 1rem; margin-bottom: 1.25rem; }
            .kpi-tile {
              position: relative; overflow: hidden; background: var(--surface); border: 1px solid var(--border);
              border-radius: var(--radius); padding: 1.1rem 1.2rem;
            }
            .kpi-tile::before {
              content: ""; position: absolute; inset: 0 0 auto 0; height: 3px; background: var(--accent-grad);
              opacity: 0.9;
            }
            .kpi-tile.tone-critical::before { background: var(--critical); }
            .kpi-tile.tone-warning::before { background: var(--warning); }
            .kpi-tile.tone-good::before { background: var(--good); }
            .kpi-label { font-size: 0.72rem; text-transform: uppercase; letter-spacing: 0.08em; color: var(--muted); font-weight: 700; }
            .kpi-value { font-size: 1.9rem; font-weight: 800; margin-top: 0.35rem; font-variant-numeric: tabular-nums; }
            .kpi-foot { margin-top: 0.4rem; font-size: 0.8rem; color: var(--text-secondary); }

            .grid-2 { display: grid; grid-template-columns: 2fr 1fr; gap: 1.25rem; align-items: start; }
            @media (max-width: 1000px) { .grid-2 { grid-template-columns: 1fr; } }

            h1, h2, h3 { color: var(--text); }
            .muted { color: var(--muted); font-size: 0.88rem; }
            .link { color: var(--accent-b); text-decoration: none; font-weight: 600; }
            .link:hover { text-decoration: underline; }

            label { display: block; margin-top: 0.9rem; font-weight: 600; font-size: 0.82rem; color: var(--text-secondary); }
            input, select, textarea {
              width: 100%; padding: 0.6rem 0.75rem; margin-top: 0.3rem; font: inherit; color: var(--text);
              background: var(--surface-2); border: 1px solid var(--border); border-radius: 10px; outline: none;
            }
            input::placeholder { color: var(--muted); }
            input:focus, select:focus, textarea:focus { border-color: var(--accent-a); box-shadow: 0 0 0 3px rgba(124,92,255,0.18); }

            button, .btn {
              font: inherit; font-weight: 700; cursor: pointer; border-radius: 10px; border: 1px solid transparent;
              padding: 0.6rem 1.1rem; color: #fff; background: var(--accent-grad); transition: filter 0.15s, transform 0.05s;
            }
            button:hover, .btn:hover { filter: brightness(1.12); }
            button:active, .btn:active { transform: translateY(1px); }
            button:disabled { opacity: 0.5; cursor: not-allowed; filter: none; }
            .btn-ghost { background: transparent; border-color: var(--border-strong); color: var(--text); }
            .btn-ghost:hover { background: rgba(255,255,255,0.05); filter: none; }
            .btn-danger { background: var(--critical); }
            .btn-sm { padding: 0.35rem 0.7rem; font-size: 0.8rem; border-radius: 8px; }
            .btn-row { display: flex; gap: 0.6rem; margin-top: 1.1rem; flex-wrap: wrap; }

            .filter-bar { display: flex; gap: 0.75rem; flex-wrap: wrap; align-items: flex-end; margin-bottom: 1.1rem; }
            .filter-bar .field { min-width: 160px; flex: 1; }
            .filter-bar label { margin-top: 0; }
            .filter-bar button { margin-top: 0.3rem; height: 2.55rem; }

            .table-wrap { overflow-x: auto; border: 1px solid var(--border); border-radius: var(--radius); position: relative; }
            table { width: 100%; border-collapse: collapse; font-size: 0.86rem; min-width: 640px; }
            thead th {
              text-align: left; padding: 0.7rem 0.9rem; background: rgba(255,255,255,0.03); color: var(--muted);
              font-size: 0.72rem; text-transform: uppercase; letter-spacing: 0.06em; border-bottom: 1px solid var(--border);
              position: sticky; top: 0;
            }
            tbody td { padding: 0.65rem 0.9rem; border-bottom: 1px solid var(--border); vertical-align: middle; }
            tbody tr:last-child td { border-bottom: none; }
            tbody tr:hover { background: rgba(255,255,255,0.025); }
            .mono { font-family: ui-monospace, SFMono-Regular, Menlo, monospace; font-size: 0.83rem; }
            .row-actions { display: flex; gap: 0.4rem; }

            .badge {
              display: inline-flex; align-items: center; gap: 0.4rem; padding: 0.22rem 0.6rem; border-radius: 999px;
              font-size: 0.78rem; font-weight: 700; background: rgba(255,255,255,0.05); border: 1px solid var(--border);
            }
            .badge .dot { width: 7px; height: 7px; border-radius: 50%; flex-shrink: 0; }
            .badge-good { color: var(--good); } .badge-good .dot { background: var(--good); }
            .badge-warning { color: var(--warning); } .badge-warning .dot { background: var(--warning); }
            .badge-serious { color: var(--serious); } .badge-serious .dot { background: var(--serious); }
            .badge-critical { color: var(--critical); } .badge-critical .dot { background: var(--critical); }
            .badge-info { color: var(--info); } .badge-info .dot { background: var(--info); }
            .badge-muted { color: var(--muted); } .badge-muted .dot { background: var(--muted); }

            .banner { margin-bottom: 1rem; padding: 0.8rem 1rem; border-radius: 10px; font-size: 0.88rem; border: 1px solid transparent; }
            .banner.error { background: rgba(208,59,59,0.12); border-color: rgba(208,59,59,0.35); color: #ff8f8f; }
            .banner.ok { background: rgba(12,163,12,0.12); border-color: rgba(12,163,12,0.35); color: #7bdc7b; }
            .banner.warn { background: rgba(250,178,25,0.10); border-color: rgba(250,178,25,0.3); color: #ffcf6b; }

            .pagination { display: flex; align-items: center; justify-content: flex-end; gap: 0.75rem; margin-top: 0.9rem; font-size: 0.85rem; color: var(--muted); }

            .empty-state { display: flex; flex-direction: column; align-items: center; justify-content: center; padding: 3rem 1rem; color: var(--muted); text-align: center; gap: 0.4rem; }
            .empty-state .glyph { font-size: 2rem; opacity: 0.6; }

            .legend { display: flex; gap: 1.1rem; margin-bottom: 0.6rem; flex-wrap: wrap; }
            .legend-item { display: flex; align-items: center; gap: 0.45rem; font-size: 0.82rem; color: var(--text-secondary); }
            .legend-swatch { width: 10px; height: 10px; border-radius: 3px; }

            .chart-tooltip {
              position: fixed; pointer-events: none; background: #0d0e18; border: 1px solid var(--border-strong);
              border-radius: 10px; padding: 0.55rem 0.75rem; font-size: 0.78rem; box-shadow: 0 10px 30px rgba(0,0,0,0.5);
              opacity: 0; transform: translateY(4px); transition: opacity 0.1s, transform 0.1s; z-index: 50; white-space: nowrap;
            }
            .chart-tooltip.show { opacity: 1; transform: translateY(0); }
            .chart-tooltip strong { display: block; margin-bottom: 0.2rem; }

            .ring-wrap { display: flex; flex-direction: column; align-items: center; gap: 0.6rem; }
            .ring-value { font-size: 1.7rem; font-weight: 800; }
            .ring-caption { font-size: 0.8rem; color: var(--muted); text-align: center; }

            .table-wrap.loading::after {
              content: ""; position: absolute; inset: 0; background: rgba(10,11,18,0.35); backdrop-filter: blur(1px);
            }
            .table-wrap.loading::before {
              content: ""; position: absolute; top: 14px; right: 14px; width: 18px; height: 18px; z-index: 2;
              border-radius: 50%; border: 2px solid rgba(255,255,255,0.25); border-top-color: var(--accent-b);
              animation: spin 0.7s linear infinite;
            }
            @keyframes spin { to { transform: rotate(360deg); } }

            .toast-stack { position: fixed; right: 1.25rem; bottom: 1.25rem; display: flex; flex-direction: column; gap: 0.6rem; z-index: 100; }
            .toast {
              min-width: 260px; max-width: 360px; background: #12141f; border: 1px solid var(--border-strong);
              border-left: 3px solid var(--accent-b); border-radius: 10px; padding: 0.75rem 0.9rem; font-size: 0.85rem;
              box-shadow: 0 10px 30px rgba(0,0,0,0.45); animation: toast-in 0.2s ease-out;
            }
            .toast.ok { border-left-color: var(--good); }
            .toast.error { border-left-color: var(--critical); }
            @keyframes toast-in { from { opacity: 0; transform: translateY(8px); } to { opacity: 1; transform: translateY(0); } }

            .modal-backdrop {
              position: fixed; inset: 0; background: rgba(6,7,12,0.6); backdrop-filter: blur(3px);
              display: flex; align-items: center; justify-content: center; z-index: 90; animation: fade-in 0.15s ease-out;
            }
            @keyframes fade-in { from { opacity: 0; } to { opacity: 1; } }
            .modal-card {
              width: min(400px, 90vw); background: var(--surface); border: 1px solid var(--border-strong);
              border-radius: var(--radius-lg); padding: 1.4rem; box-shadow: var(--shadow-glow);
            }
            .modal-card h3 { margin: 0 0 0.5rem; }
            .modal-card p { color: var(--text-secondary); font-size: 0.9rem; }

            .auth-body {
              display: flex; align-items: center; justify-content: center; min-height: 100vh;
              background: radial-gradient(1200px 700px at 50% -10%, rgba(124,92,255,0.22), transparent 60%), var(--bg);
            }
            .auth-card { width: min(380px, 92vw); }
            .auth-card .brand { justify-content: center; padding-bottom: 1.6rem; }
            .auth-card .brand-mark { font-size: 2rem; }
            .auth-card .brand-text { font-size: 1.4rem; }
            .auth-card h1 { text-align: center; font-size: 1.15rem; margin: 0 0 0.25rem; }
            .auth-card .muted.center { text-align: center; }

            @media (max-width: 860px) {
              .sidebar { width: 72px; padding: 1.2rem 0.5rem; }
              .brand-text, .nav-label, .admin-name { display: none; }
              .brand { justify-content: center; }
              .nav-item { justify-content: center; }
              .admin-chip { justify-content: center; }
              .content { padding: 1rem; }
              .topbar { padding: 1.25rem 1rem 0.25rem; }
            }
            """;

    private static final String RUNTIME_JS = """
            function escapeHtml(s) {
              return String(s == null ? '' : s).replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');
            }

            function showToast(message, kind) {
              const stack = document.getElementById('toastStack');
              const toast = document.createElement('div');
              toast.className = 'toast' + (kind ? ' ' + kind : '');
              toast.textContent = message;
              stack.appendChild(toast);
              setTimeout(() => { toast.style.opacity = '0'; toast.style.transition = 'opacity 0.25s'; }, 3200);
              setTimeout(() => toast.remove(), 3500);
            }

            function confirmModal(title, body) {
              return new Promise((resolve) => {
                const root = document.getElementById('modalRoot');
                root.innerHTML = `
                  <div class="modal-backdrop">
                    <div class="modal-card">
                      <h3>${escapeHtml(title)}</h3>
                      <p>${escapeHtml(body)}</p>
                      <div class="btn-row">
                        <button type="button" class="btn-danger" data-act="yes">Confirm</button>
                        <button type="button" class="btn-ghost" data-act="no">Cancel</button>
                      </div>
                    </div>
                  </div>`;
                const backdrop = root.querySelector('.modal-backdrop');
                const close = (result) => { root.innerHTML = ''; resolve(result); };
                backdrop.addEventListener('click', (e) => { if (e.target === backdrop) close(false); });
                root.querySelector('[data-act=yes]').addEventListener('click', () => close(true));
                root.querySelector('[data-act=no]').addEventListener('click', () => close(false));
              });
            }

            function showTextModal(title, content) {
              const root = document.getElementById('modalRoot');
              root.innerHTML = `
                <div class="modal-backdrop">
                  <div class="modal-card" style="max-width: 760px; width: min(80vw, 760px);">
                    <h3>${escapeHtml(title)}</h3>
                    <pre style="white-space: pre-wrap; word-break: break-word; margin: 0 0 1rem; max-height: 60vh; overflow: auto;">${escapeHtml(content || 'No content available.')}</pre>
                    <div class="btn-row">
                      <button type="button" class="btn-ghost" data-act="close">Close</button>
                    </div>
                  </div>
                </div>`;
              const backdrop = root.querySelector('.modal-backdrop');
              const close = () => { root.innerHTML = ''; };
              backdrop.addEventListener('click', (e) => { if (e.target === backdrop) close(); });
              root.querySelector('[data-act=close]').addEventListener('click', close);
            }

            function formatDateTime(value) {
              if (!value) return '&mdash;';
              const date = new Date(value);
              if (Number.isNaN(date.getTime())) return '&mdash;';
              return new Intl.DateTimeFormat('en-US', {
                month: 'short', day: 'numeric', year: 'numeric',
                hour: 'numeric', minute: '2-digit'
              }).format(date);
            }

            async function api(url, options) {
              const res = await fetch(url, options);
              let data = null;
              try { data = await res.json(); } catch (e) { /* no body */ }
              if (!res.ok) {
                const message = (data && data.error) ? data.error : ('Request failed (' + res.status + ')');
                throw new Error(message);
              }
              return data;
            }

            async function quickAddToList(apiUrl, msisdn, label) {
              const ok = await confirmModal('Add to ' + label + '?', msisdn + ' will be added to the ' + label.toLowerCase() + '.');
              if (!ok) return;
              const payload = apiUrl.endsWith('/whitelist')
                ? { senderId: msisdn, description: 'Added from ' + label + ' quick action' }
                : { msisdn: msisdn, reason: 'Added from ' + label + ' quick action' };
              try {
                await api(apiUrl, {
                  method: 'POST', headers: { 'Content-Type': 'application/json' },
                  body: JSON.stringify(payload)
                });
                showToast(msisdn + ' added to ' + label, 'ok');
              } catch (err) {
                showToast(err.message, 'error');
              }
            }

            /** Behavior for the Blacklist page (see WebPage.entryListPage). */
            function initEntryListPage(apiUrl) {
              const searchInput = document.getElementById('listSearch');
              const tableWrap = document.getElementById('listTableWrap');
              const tbody = document.getElementById('listTableBody');
              const info = document.getElementById('listPaginationInfo');
              const prevBtn = document.getElementById('listPrevBtn');
              const nextBtn = document.getElementById('listNextBtn');
              const form = document.getElementById('listForm');
              const state = { page: 1, pageSize: 20, query: '' };
              let totalPages = 1;

              function rowHtml(e) {
                const expires = e.expiresAt ? formatDateTime(e.expiresAt) : '&mdash;';
                return `<tr data-id="${e.id}">
                  <td class="mono">${escapeHtml(e.msisdn)}</td>
                  <td>${escapeHtml(e.reason || '')}</td>
                  <td class="muted">${formatDateTime(e.createdAt)}</td>
                  <td class="muted">${expires}</td>
                  <td class="row-actions"><button type="button" class="btn-ghost btn-sm" data-del="${e.id}">Remove</button></td>
                </tr>`;
              }

              async function load() {
                tableWrap.classList.add('loading');
                try {
                  const data = await api(apiUrl + '?query=' + encodeURIComponent(state.query) + '&page=' + state.page + '&pageSize=' + state.pageSize);
                  totalPages = Math.max(1, Math.ceil(data.totalItems / data.pageSize));
                  tbody.innerHTML = data.items.length === 0
                    ? '<tr><td colspan="5"><div class="empty-state"><div class="glyph">&#9711;</div>No entries found.</div></td></tr>'
                    : data.items.map(rowHtml).join('');
                  info.textContent = 'Page ' + data.page + ' of ' + totalPages + ' \\u00b7 ' + data.totalItems + ' total';
                  prevBtn.disabled = data.page <= 1;
                  nextBtn.disabled = data.page >= totalPages;
                } catch (err) {
                  showToast(err.message, 'error');
                } finally {
                  tableWrap.classList.remove('loading');
                }
              }

              let searchTimer;
              searchInput.addEventListener('input', () => {
                clearTimeout(searchTimer);
                searchTimer = setTimeout(() => { state.query = searchInput.value; state.page = 1; load(); }, 300);
              });

              prevBtn.addEventListener('click', () => { if (state.page > 1) { state.page--; load(); } });
              nextBtn.addEventListener('click', () => { if (state.page < totalPages) { state.page++; load(); } });

              tbody.addEventListener('click', async (e) => {
                const btn = e.target.closest('[data-del]');
                if (!btn) return;
                const id = btn.getAttribute('data-del');
                const ok = await confirmModal('Remove entry?', 'This number will no longer be affected by this list.');
                if (!ok) return;
                try {
                  await api(apiUrl + '?id=' + id, { method: 'DELETE' });
                  showToast('Removed', 'ok');
                  load();
                } catch (err) {
                  showToast(err.message, 'error');
                }
              });

              form.addEventListener('submit', async (e) => {
                e.preventDefault();
                const msisdn = document.getElementById('entryMsisdn').value.trim();
                const reason = document.getElementById('entryReason').value.trim();
                const expiresLocal = document.getElementById('entryExpires').value;
                const payload = { msisdn: msisdn, reason: reason || null, expiresAt: expiresLocal ? new Date(expiresLocal).toISOString() : null };
                try {
                  await api(apiUrl, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(payload) });
                  showToast('Added', 'ok');
                  form.reset();
                  state.page = 1;
                  load();
                } catch (err) {
                  showToast(err.message, 'error');
                }
              });
              load();
            }
            """;
}
