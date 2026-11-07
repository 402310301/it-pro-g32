package za.org.foodredistribution.web;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import za.org.foodredistribution.auth.Role;
import za.org.foodredistribution.auth.User;
import za.org.foodredistribution.repository.NotificationRepository;

/**
 * Renders a page template inside the shared layout (sidebar, signed-in user, flash messages, unread badge).
 * Every page template can use {{me}} for the signed-in user, e.g. {{#me.canManage}}...{{/me.canManage}}.
 */
public final class View {

    public record NavItem(String label, String href, String icon, boolean active, long badge) {
        public boolean hasBadge() {
            return badge > 0;
        }
    }

    private final Template template;
    private final NotificationRepository notifications;

    public View(Template template, NotificationRepository notifications) {
        this.template = template;
        this.notifications = notifications;
    }

    public Response page(Request request, String title, String section, String templateName, Map<String, Object> model) {
        return Response.html(render(request, title, section, templateName, model));
    }

    public String render(Request request, String title, String section, String templateName, Map<String, Object> model) {
        User user = request == null ? null : request.user();
        model.put("me", user);
        String content = template.render(templateName, model);
        return template.render("layout", Model.of(
            "title", title,
            "me", user,
            "nav", user == null ? List.of() : navigation(user, section),
            "content", content,
            "message", request == null ? "" : request.query("msg"),
            "error", request == null ? "" : request.query("err")));
    }

    /** A page without the sidebar (the login screen). */
    public Response standalone(String templateName, Map<String, Object> model) {
        return Response.html(template.render(templateName, model));
    }

    /** The sidebar links this user is allowed to see. */
    private List<NavItem> navigation(User user, String section) {
        List<NavItem> nav = new ArrayList<>();
        nav.add(new NavItem("Dashboard", "/", "home", "dashboard".equals(section), 0));
        nav.add(new NavItem("Donations", "/donations", "box", "donations".equals(section), 0));
        if (user.canManageNeeds()) {
            nav.add(new NavItem("Recipient needs", "/needs", "users", "needs".equals(section), 0));
        }
        if (user.canManage()) {
            nav.add(new NavItem("Transport", "/transport", "truck", "transport".equals(section), 0));
        }
        nav.add(new NavItem("Notifications", "/notifications", "bell", "notifications".equals(section),
            notifications.countUnread()));
        if (user.canManage()) {
            nav.add(new NavItem("Reports", "/reports", "chart", "reports".equals(section), 0));
        }
        if (user.hasRole(Role.ADMIN)) {
            nav.add(new NavItem("Admin", "/admin", "settings", "admin".equals(section), 0));
        }
        nav.add(new NavItem("My account", "/account", "user", "account".equals(section), 0));
        return nav;
    }
}
