package za.org.foodredistribution.web.controllers;

import za.org.foodredistribution.service.Repositories;
import za.org.foodredistribution.web.Model;
import za.org.foodredistribution.web.Response;
import za.org.foodredistribution.web.Router;
import za.org.foodredistribution.web.View;

public final class NotificationController {

    private final Repositories repos;
    private final View view;

    public NotificationController(Repositories repos, View view) {
        this.repos = repos;
        this.view = view;
    }

    public void register(Router router) {
        router.get("/notifications", request -> view.page(request, "Notifications", "notifications",
            "notifications/list", Model.of(
                "notifications", repos.notifications().findAll(),
                "unread", repos.notifications().countUnread())));
        router.post("/notifications/{id}/read", request -> {
            repos.notifications().markRead(request.id("id"));
            return Response.redirect("/notifications");
        });
        router.post("/notifications/read-all", request -> {
            repos.notifications().markAllRead();
            return Response.success("/notifications", "All notifications marked as read.");
        });
    }
}
