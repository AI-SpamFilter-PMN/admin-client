package com.spamfilter.adminclient.servlet;

import com.spamfilter.adminclient.auth.AdminSessionUtil;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

/**
 * GET /logout
 */
public class LogoutServlet extends HttpServlet {

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        AdminSessionUtil.logout(req);
        resp.sendRedirect("/login");
    }
}
