package za.org.foodredistribution;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import za.org.foodredistribution.auth.AuthService;
import za.org.foodredistribution.auth.UserRepository;
import za.org.foodredistribution.db.Database;
import za.org.foodredistribution.service.AdminService;
import za.org.foodredistribution.service.DonationService;
import za.org.foodredistribution.service.EventLog;
import za.org.foodredistribution.service.MatchingEngine;
import za.org.foodredistribution.service.ReportService;
import za.org.foodredistribution.service.Repositories;
import za.org.foodredistribution.service.TransportService;
import za.org.foodredistribution.web.Router;
import za.org.foodredistribution.web.Template;
import za.org.foodredistribution.web.View;
import za.org.foodredistribution.web.controllers.AdminController;
import za.org.foodredistribution.web.controllers.AuthController;
import za.org.foodredistribution.web.controllers.DashboardController;
import za.org.foodredistribution.web.controllers.DonationController;
import za.org.foodredistribution.web.controllers.NeedController;
import za.org.foodredistribution.web.controllers.NotificationController;
import za.org.foodredistribution.web.controllers.ReportController;
import za.org.foodredistribution.web.controllers.TransportController;

/**
 * Starts the Intelligent Surplus Food Redistribution System web app.
 * In VS Code: open this file and click "Run" above the main method (or press F5).
 */
public final class App {

    private App() {
    }

    public static void main(String[] args) throws Exception {
        AppConfig config = new AppConfig();
        Database db = new Database(config.get("db.url", ""), config.get("db.user", "root"), config.get("db.password", ""));

        System.out.println("Connecting to MySQL...");
        db.checkConnection();
        db.runScript("db/schema.sql");

        HttpServer server = start(db, config.getInt("server.port", 8080), config.getBoolean("demo.seed", true), false);
        System.out.println();
        System.out.println("  Intelligent Surplus Food Redistribution System is running.");
        System.out.println("  Open http://localhost:" + server.getAddress().getPort() + " in your browser.");
        System.out.println("  Press Ctrl+C (or the red stop button in VS Code) to stop.");
    }

    /** Wires everything together and starts the web server. Also used by tests with a different Database. */
    public static HttpServer start(Database db, int port, boolean seedDemoData, boolean strictTemplates) throws Exception {
        Repositories repos = Repositories.create(db);
        MatchingEngine engine = new MatchingEngine();
        EventLog events = new EventLog(repos.audit(), repos.notifications());
        DonationService donations = new DonationService(repos, engine, events);
        TransportService transport = new TransportService(repos, donations, events);
        AdminService admin = new AdminService(repos, donations, events);
        ReportService reports = new ReportService(repos, engine);

        UserRepository users = new UserRepository(db);
        AuthService auth = new AuthService(users, repos.organisations(), repos.audit());
        if (seedDemoData) {
            DemoData demo = new DemoData(repos, donations, transport, engine);
            if (demo.seedIfEmpty()) {
                System.out.println("Demo data loaded into the empty database.");
            }
            demo.seedUsers(users);
        }
        if (auth.ensureAdminExists()) {
            System.out.println("Created the first user: admin / admin123 (change this password after signing in).");
        }

        View view = new View(new Template(strictTemplates), repos.notifications());
        Router router = new Router(view, auth);
        new AuthController(auth, view, seedDemoData).register(router);
        new DashboardController(repos, reports, view).register(router);
        new DonationController(repos, donations, view).register(router);
        new NeedController(repos, admin, view).register(router);
        new TransportController(repos, transport, view).register(router);
        new NotificationController(repos, view).register(router);
        new ReportController(reports, view).register(router);
        new AdminController(repos, admin, reports, auth, view).register(router);

        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/", router);
        server.setExecutor(Executors.newFixedThreadPool(8));
        server.start();

        // Every minute, mark donations whose expiry time has passed.
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "expiry-check");
            thread.setDaemon(true);
            return thread;
        });
        scheduler.scheduleAtFixedRate(() -> {
            try {
                int expired = donations.expireOverdue();
                if (expired > 0) {
                    System.out.println(expired + " donation(s) expired.");
                }
            } catch (RuntimeException e) {
                System.err.println("Expiry check failed: " + e.getMessage());
            }
        }, 1, 1, TimeUnit.MINUTES);
        return server;
    }
}
