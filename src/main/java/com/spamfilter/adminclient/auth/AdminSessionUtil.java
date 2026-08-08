package com.spamfilter.adminclient.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

/**
 * Reads/writes the logged-in admin's identity on the servlet session.
 */
public final class AdminSessionUtil {

    private static final String ADMIN_ID = "adminId";
    private static final String ADMIN_EMAIL = "adminEmail";

    private AdminSessionUtil() {
    }

    public static void login(HttpServletRequest req, String adminId, String email) {
        HttpSession session = req.getSession(true);
        session.setAttribute(ADMIN_ID, adminId);
        session.setAttribute(ADMIN_EMAIL, email);
    }

    public static String currentAdminId(HttpServletRequest req) {
        HttpSession session = req.getSession(false);
        return session == null ? null : (String) session.getAttribute(ADMIN_ID);
    }

    public static String currentAdminEmail(HttpServletRequest req) {
        HttpSession session = req.getSession(false);
        return session == null ? null : (String) session.getAttribute(ADMIN_EMAIL);
    }

    public static void logout(HttpServletRequest req) {
        HttpSession session = req.getSession(false);
        if (session != null) {
            session.invalidate();
        }
    }
}
