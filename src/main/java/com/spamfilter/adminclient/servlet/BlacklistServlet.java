package com.spamfilter.adminclient.servlet;

import com.fasterxml.jackson.databind.JsonNode;
import com.spamfilter.adminclient.auth.AdminSessionUtil;
import com.spamfilter.adminclient.db.BlocklistRepository;
import com.spamfilter.adminclient.model.BlocklistEntry;
import com.spamfilter.adminclient.model.Page;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.io.PrintWriter;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Map;

/**
 * GET /blacklist (HTML page) and GET/POST/DELETE /api/blocklist (JSON) -
 * numbers blocked outright, checked before classification runs. Registered
 * under both paths in Main.java against the same instance; which one was
 * hit is told apart by req.getServletPath().
 */
public class BlacklistServlet extends HttpServlet {

    private static final String API_PATH = "/api/blocklist";

    private final BlocklistRepository blocklistRepository;

    public BlacklistServlet(BlocklistRepository blocklistRepository) {
        this.blocklistRepository = blocklistRepository;
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
            Page<BlocklistEntry> page = blocklistRepository.search(req.getParameter("query"), 1, 20);
            body = WebPage.entryListPage("Blacklist", API_PATH, page);
        } catch (IllegalStateException e) {
            body = "<div class=\"card\"><div class=\"banner error\">Could not load the blacklist: "
                    + WebPage.escape(e.getMessage()) + "</div></div>";
        }

        resp.setContentType("text/html; charset=UTF-8");
        try (PrintWriter out = resp.getWriter()) {
            out.write(WebPage.shell("Blacklist", "Numbers blocked outright, checked before classification runs.",
                    "/blacklist", AdminSessionUtil.currentAdminEmail(req), body));
        }
    }

    private void apiSearch(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        int page = parseInt(req.getParameter("page"), 1);
        int pageSize = Math.min(100, parseInt(req.getParameter("pageSize"), 20));
        try {
            Page<BlocklistEntry> result = blocklistRepository.search(req.getParameter("query"), page, pageSize);
            JsonSupport.writeJson(resp, HttpServletResponse.SC_OK, Map.of(
                    "items", result.getItems(), "page", result.getPage(),
                    "pageSize", result.getPageSize(), "totalItems", result.getTotalItems()));
        } catch (IllegalStateException e) {
            JsonSupport.writeError(resp, HttpServletResponse.SC_SERVICE_UNAVAILABLE, e.getMessage());
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
        if (msisdn == null || msisdn.isBlank()) {
            JsonSupport.writeError(resp, HttpServletResponse.SC_BAD_REQUEST, "msisdn is required");
            return;
        }
        String reason = json.path("reason").isNull() ? null : json.path("reason").asText(null);
        Instant expiresAt;
        try {
            String raw = json.path("expiresAt").asText(null);
            expiresAt = (raw == null || raw.isBlank()) ? null : Instant.parse(raw);
        } catch (DateTimeParseException e) {
            JsonSupport.writeError(resp, HttpServletResponse.SC_BAD_REQUEST, "expiresAt must be an ISO-8601 timestamp");
            return;
        }

        try {
            blocklistRepository.add(msisdn.trim(), reason, expiresAt);
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
            blocklistRepository.delete(id);
            JsonSupport.writeJson(resp, HttpServletResponse.SC_OK, Map.of("ok", true));
        } catch (IllegalStateException e) {
            JsonSupport.writeError(resp, HttpServletResponse.SC_SERVICE_UNAVAILABLE, e.getMessage());
        }
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
