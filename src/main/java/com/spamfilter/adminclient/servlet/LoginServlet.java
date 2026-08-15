package com.spamfilter.adminclient.servlet;

import com.spamfilter.adminclient.auth.AdminSessionUtil;
import com.spamfilter.adminclient.db.AdminRepository;
import com.spamfilter.adminclient.model.Admin;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.io.PrintWriter;

/**
 * GET/POST /login
 */
public class LoginServlet extends HttpServlet {

    private final AdminRepository adminRepository;

    public LoginServlet(AdminRepository adminRepository) {
        this.adminRepository = adminRepository;
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        if (AdminSessionUtil.currentAdminId(req) != null) {
            resp.sendRedirect("/");
            return;
        }
        render(resp, null);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String email = req.getParameter("email");
        String password = req.getParameter("password");

        try {
            Admin admin = adminRepository.authenticate(
                    email == null ? "" : email.trim(), password == null ? "" : password);
            if (admin == null) {
                render(resp, "Invalid email/password, or this account doesn't have admin access");
                return;
            }
            AdminSessionUtil.login(req, admin.getId(), admin.getEmail());
            resp.sendRedirect("/");
        } catch (IllegalStateException e) {
            render(resp, "Login unavailable: " + e.getMessage());
        }
    }

    private void render(HttpServletResponse resp, String error) throws IOException {
        String body = """
                <div class="card auth-card">
                  <a class="brand" href="/">
                    <span class="brand-mark">&#128737;</span>
                    <span class="brand-text">Warden</span>
                  </a>
                  <h1>Admin sign in</h1>
                  <p class="muted center">Manage traffic and the block/allow lists.</p>
                  %s
                  <form method="post">
                    <label for="email">Email</label>
                    <input id="email" name="email" type="email" required autofocus>

                    <label for="password">Password</label>
                    <input id="password" name="password" type="password" required>

                    <button type="submit" style="width:100%%; margin-top:1.25rem;">Sign in</button>
                  </form>
                </div>
                """.formatted(error != null ? "<div class=\"banner error\">" + WebPage.escape(error) + "</div>" : "");

        resp.setContentType("text/html; charset=UTF-8");
        try (PrintWriter out = resp.getWriter()) {
            out.write(WebPage.bareShell("Sign in", body));
        }
    }
}
