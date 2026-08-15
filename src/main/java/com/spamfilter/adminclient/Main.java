package com.spamfilter.adminclient;

import com.spamfilter.adminclient.config.AdminConfig;
import com.spamfilter.adminclient.db.AdminRepository;
import com.spamfilter.adminclient.db.BlocklistRepository;
import com.spamfilter.adminclient.db.CallRepository;
import com.spamfilter.adminclient.db.DashboardRepository;
import com.spamfilter.adminclient.db.Database;
import com.spamfilter.adminclient.db.LogRepository;
import com.spamfilter.adminclient.db.MessageRepository;
import com.spamfilter.adminclient.db.WhitelistRepository;
import com.spamfilter.adminclient.servlet.BlacklistServlet;
import com.spamfilter.adminclient.servlet.CallsServlet;
import com.spamfilter.adminclient.servlet.DashboardServlet;
import com.spamfilter.adminclient.servlet.LoginServlet;
import com.spamfilter.adminclient.servlet.LogoutServlet;
import com.spamfilter.adminclient.servlet.LogsServlet;
import com.spamfilter.adminclient.servlet.MessagesServlet;
import com.spamfilter.adminclient.servlet.WhitelistServlet;
import org.apache.catalina.Context;
import org.apache.catalina.startup.Tomcat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Entry point for the admin site: a self-contained Java SE app that starts
 * an embedded Tomcat and serves a login-protected dashboard for moderating
 * traffic on the shared Neon database (subscribers, messages, calls, logs,
 * blocklist/whitelist) - independent of and never touching sms-client.
 */
public class Main {

    private static final Logger log = LoggerFactory.getLogger(Main.class);

    public static void main(String[] args) throws Exception {
        AdminConfig config = new AdminConfig();
        DataSource dataSource = Database.connect(config);

        AdminRepository adminRepository = new AdminRepository(dataSource);
        DashboardRepository dashboardRepository = new DashboardRepository(dataSource);
        BlocklistRepository blocklistRepository = new BlocklistRepository(dataSource);
        WhitelistRepository whitelistRepository = new WhitelistRepository(dataSource);
        MessageRepository messageRepository = new MessageRepository(dataSource);
        CallRepository callRepository = new CallRepository(dataSource);
        LogRepository logRepository = new LogRepository(dataSource);

        Tomcat tomcat = new Tomcat();
        Path baseDir = Files.createTempDirectory("admin-client-tomcat");
        tomcat.setBaseDir(baseDir.toString());
        tomcat.setPort(config.serverPort());
        tomcat.getConnector();

        Context ctx = tomcat.addContext("", baseDir.toFile().getAbsolutePath());

        Tomcat.addServlet(ctx, "login", new LoginServlet(adminRepository));
        ctx.addServletMappingDecoded("/login", "login");

        Tomcat.addServlet(ctx, "logout", new LogoutServlet());
        ctx.addServletMappingDecoded("/logout", "logout");

        Tomcat.addServlet(ctx, "dashboard", new DashboardServlet(dashboardRepository));
        ctx.addServletMappingDecoded("/", "dashboard");

        BlacklistServlet blacklistServlet = new BlacklistServlet(blocklistRepository, messageRepository, callRepository);
        Tomcat.addServlet(ctx, "blacklistPage", blacklistServlet);
        ctx.addServletMappingDecoded("/blacklist", "blacklistPage");
        Tomcat.addServlet(ctx, "blacklistApi", blacklistServlet);
        ctx.addServletMappingDecoded("/api/blocklist", "blacklistApi");
        Tomcat.addServlet(ctx, "blacklistDetailsApi", blacklistServlet);
        ctx.addServletMappingDecoded("/api/blocklist/details", "blacklistDetailsApi");

        WhitelistServlet whitelistServlet = new WhitelistServlet(whitelistRepository);
        Tomcat.addServlet(ctx, "whitelistPage", whitelistServlet);
        ctx.addServletMappingDecoded("/whitelist", "whitelistPage");
        Tomcat.addServlet(ctx, "whitelistApi", whitelistServlet);
        ctx.addServletMappingDecoded("/api/whitelist", "whitelistApi");

        MessagesServlet messagesServlet = new MessagesServlet(messageRepository);
        Tomcat.addServlet(ctx, "messagesPage", messagesServlet);
        ctx.addServletMappingDecoded("/messages", "messagesPage");
        Tomcat.addServlet(ctx, "messagesApi", messagesServlet);
        ctx.addServletMappingDecoded("/api/messages", "messagesApi");

        CallsServlet callsServlet = new CallsServlet(callRepository);
        Tomcat.addServlet(ctx, "callsPage", callsServlet);
        ctx.addServletMappingDecoded("/calls", "callsPage");
        Tomcat.addServlet(ctx, "callsApi", callsServlet);
        ctx.addServletMappingDecoded("/api/calls", "callsApi");

        LogsServlet logsServlet = new LogsServlet(logRepository);
        Tomcat.addServlet(ctx, "logsPage", logsServlet);
        ctx.addServletMappingDecoded("/logs", "logsPage");
        Tomcat.addServlet(ctx, "logsApi", logsServlet);
        ctx.addServletMappingDecoded("/api/logs", "logsApi");

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            log.info("Shutting down admin client...");
            try {
                tomcat.stop();
            } catch (Exception e) {
                log.warn("Error stopping Tomcat", e);
            }
        }));

        tomcat.start();
        log.info("Admin client listening on http://localhost:{}", config.serverPort());
        tomcat.getServer().await();
    }
}
