package za.org.foodredistribution.web.controllers;

import java.util.HashMap;
import java.util.Map;
import za.org.foodredistribution.auth.ForbiddenException;
import za.org.foodredistribution.auth.Role;
import za.org.foodredistribution.auth.User;
import za.org.foodredistribution.model.Catalog;
import za.org.foodredistribution.model.Organisation;
import za.org.foodredistribution.service.AdminService;
import za.org.foodredistribution.service.Repositories;
import za.org.foodredistribution.service.ValidationException;
import za.org.foodredistribution.web.Model;
import za.org.foodredistribution.web.Request;
import za.org.foodredistribution.web.Response;
import za.org.foodredistribution.web.Router;
import za.org.foodredistribution.web.View;

public final class NeedController {

    private final Repositories repos;
    private final AdminService admin;
    private final View view;

    public NeedController(Repositories repos, AdminService admin, View view) {
        this.repos = repos;
        this.admin = admin;
        this.view = view;
    }

    public void register(Router router) {
        router.get("/needs", request -> {
            User user = request.require(Role.ADMIN, Role.COORDINATOR, Role.RECIPIENT);
            Map<String, String> defaults = new HashMap<>(Map.of("opensAt", "08:00", "closesAt", "17:00",
                "storageType", "Ambient", "urgency", "High"));
            if (user.isRecipient()) {
                defaults.put("organisationId", String.valueOf(user.organisationId()));
            }
            return page(request, defaults, Map.of());
        });
        router.post("/needs", request -> {
            User user = request.require(Role.ADMIN, Role.COORDINATOR, Role.RECIPIENT);
            Map<String, String> submitted = new HashMap<>(request.form());
            if (user.isRecipient()) {
                submitted.put("organisationId", String.valueOf(user.organisationId()));
            }
            try {
                admin.addNeed(submitted);
                return Response.success("/needs", "Need registered. Open donations were re-matched.");
            } catch (ValidationException e) {
                return page(request, submitted, e.errors().isEmpty() ? Map.of("general", e.getMessage()) : e.errors());
            }
        });
        router.post("/needs/{id}/close", request -> {
            checkOwnNeed(request, request.id("id"));
            admin.setNeedActive(request.id("id"), false);
            return Response.success("/needs", "Need closed.");
        });
        router.post("/needs/{id}/reopen", request -> {
            checkOwnNeed(request, request.id("id"));
            admin.setNeedActive(request.id("id"), true);
            return Response.success("/needs", "Need reopened.");
        });
    }

    /** Recipients may only change their own organisation's needs. */
    private void checkOwnNeed(Request request, long needId) {
        User user = request.require(Role.ADMIN, Role.COORDINATOR, Role.RECIPIENT);
        var need = repos.needs().findById(needId);
        if (user.isRecipient() && need != null && !user.organisationId().equals(need.organisationId())) {
            throw new ForbiddenException("You can only change your own organisation's needs.");
        }
    }

    private Response page(Request request, Map<String, String> values, Map<String, String> errors) {
        User user = request.user();
        return view.page(request, "Recipient needs", "needs", "needs/list", Model.of(
            "needs", repos.needs().findAll().stream()
                .filter(n -> !user.isRecipient() || user.organisationId().equals(n.organisationId()))
                .toList(),
            "form", values,
            "errors", errors,
            "hasErrors", !errors.isEmpty(),
            "recipientOptions", Model.options(repos.organisations().findAll().stream()
                    .filter(o -> Organisation.RECIPIENT.equals(o.role()))
                    .filter(o -> !user.isRecipient() || user.organisationId() == o.id())
                    .toList(),
                Organisation::id, o -> o.name() + (o.approved() ? "" : " (pending approval)"),
                values.getOrDefault("organisationId", "")),
            "categoryOptions", Model.options(Catalog.CATEGORIES, values.getOrDefault("category", "")),
            "storageOptions", Model.options(Catalog.STORAGE_TYPES, values.getOrDefault("storageType", "")),
            "urgencyOptions", Model.options(Catalog.URGENCY_LEVELS, values.getOrDefault("urgency", ""))));
    }
}
