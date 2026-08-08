package com.spamfilter.adminclient.servlet;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.Map;

/**
 * Shared JSON request/response helpers for the /api/... endpoints - every
 * list/filter/mutate servlet needs the same read-body/write-response
 * plumbing, so it lives here once instead of six near-identical copies.
 */
final class JsonSupport {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private JsonSupport() {
    }

    static JsonNode readJson(HttpServletRequest req) throws IOException {
        try {
            return MAPPER.readTree(req.getInputStream());
        } catch (Exception e) {
            return null;
        }
    }

    static void writeError(HttpServletResponse resp, int status, String message) throws IOException {
        writeJson(resp, status, Map.of("error", message));
    }

    /**
     * Serializes to a String first, then writes it in one shot - a failure
     * mid-serialization must never leave a half-written JSON body on the
     * wire, which the client can't parse.
     */
    static void writeJson(HttpServletResponse resp, int status, Object payload) throws IOException {
        String json;
        try {
            json = MAPPER.writeValueAsString(payload);
        } catch (Exception e) {
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            resp.setContentType("application/json");
            try (PrintWriter out = resp.getWriter()) {
                out.write("{\"error\":\"Internal error building response\"}");
            }
            return;
        }
        resp.setStatus(status);
        resp.setContentType("application/json");
        try (PrintWriter out = resp.getWriter()) {
            out.write(json);
        }
    }
}
