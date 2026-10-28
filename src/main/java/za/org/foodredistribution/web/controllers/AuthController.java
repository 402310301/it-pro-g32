package za.org.foodredistribution.web.controllers;

import java.util.List;
import java.util.Map;
import za.org.foodredistribution.auth.AuthService;
import za.org.foodredistribution.auth.Role;
import za.org.foodredistribution.auth.User;
import za.org.foodredistribution.service.ValidationException;
import za.org.foodredistribution.web.Model;
import za.org.foodredistribution.web.Request;
import za.org.foodredistribution.web.Response;
import za.org.foodredistribution.web.Router;
import za.org.foodredistribution.web.View;

/** The login page, logging out, and the "My account" page. */
public final class AuthController {

    /** Demo sign-ins shown on the login page while demo data is switched on. */
    public record DemoAccount(String username, String password, String role, String description) {
        public String roleLabel() {
            return Role.label(role);
        }

        public String roleClass() {
            return role.toLowerCase();
        }
    }

    private static final List<DemoAccount> DEMO_ACCOUNTS = List.of(
        new DemoAccount("admin", "admin123", Role.ADMIN, "Everything, including users and settings"),
        new DemoAccount("coordinator", "coord123", Role.COORDINATOR, "Matching, transport and reports"),
        new DemoAccount("hotel", "donor123", Role.DONOR, "Table Bay Harvest Hotel"),
        new DemoAccount("kitchen", "recipient123", Role.RECIPIENT, "Ubuntu Community Kitchen"));

    private final AuthService auth;
    private final View view;
    private final boolean showDemoAccounts;

    public AuthController(AuthService auth, View view, boolean showDemoAccounts) {
        this.auth = auth;
        this.view = view;
        this.showDemoAccounts = showDemoAccounts;
    }

    public void register(Router router) {
        router.publicGet("/login", request -> {
            if (request.user() != null) {
                return Response.redirect("/");
            }
            return loginPage(request, "", request.query("err"));
        });
        router.publicPost("/login", request -> {
            String token = auth.login(request.field("username"), request.field("password"));
            if (token == null) {
                return loginPage(request, request.field("username"), "Wrong username or password, or the account is disabled.");
            }
            return Response.redirect(safeNext(request.field("next")))
                .withCookie(AuthService.COOKIE + "=" + token + "; Path=/; HttpOnly; SameSite=Lax; Max-Age=28800");
        });
        router.publicPost("/logout", request -> {
            auth.logout(request.cookie(AuthService.COOKIE));
            return Response.redirect("/login?msg=signed-out")
                .withCookie(AuthService.COOKIE + "=; Path=/; HttpOnly; SameSite=Lax; Max-Age=0");
        });
        router.get("/account", request -> accountPage(request, Map.of()));
        router.post("/account/password", request -> {
            try {
                auth.changePassword(request.user(), request.form());
                return Response.success("/account", "Your password was changed.");
            } catch (ValidationException e) {
                return accountPage(request, e.errors());
            }
        });
    }

    private Response loginPage(Request request, String username, String error) {
        String next = request.query("next").isEmpty() ? request.field("next") : request.query("next");
        return view.standalone("login", Model.of(
            "username", username,
            "next", next,
            "error", error,
            "signedOut", "signed-out".equals(request.query("msg")),
            "showDemo", showDemoAccounts,
            "demoAccounts", DEMO_ACCOUNTS));
    }

    private Response accountPage(Request request, Map<String, String> errors) {
        User user = request.user();
        return view.page(request, "My account", "account", "account", Model.of(
            "user", auth.users().findById(user.id()),
            "errors", errors,
            "hasErrors", !errors.isEmpty()));
    }

    /** Only allow redirects to pages on this site (stops "open redirect" tricks). */
    private static String safeNext(String next) {
        if (next == null || !next.startsWith("/") || next.startsWith("//") || next.startsWith("/login")) {
            return "/";
        }
        return next;
    }
}
