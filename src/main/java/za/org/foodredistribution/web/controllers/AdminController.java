package za.org.foodredistribution.web.controllers;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import za.org.foodredistribution.auth.AuthService;
import za.org.foodredistribution.auth.Role;
import za.org.foodredistribution.model.Catalog;
import za.org.foodredistribution.model.MatchingWeights;
import za.org.foodredistribution.model.Organisation;
import za.org.foodredistribution.service.AdminService;
import za.org.foodredistribution.service.Repositories;
import za.org.foodredistribution.service.ReportService;
import za.org.foodredistribution.service.ValidationException;
import za.org.foodredistribution.web.Model;
import za.org.foodredistribution.web.Request;
import za.org.foodredistribution.web.Response;
import za.org.foodredistribution.web.Router;
import za.org.foodredistribution.web.View;

public final class AdminController {

    private final Repositories repos;
    private final AdminService admin;
    private final ReportService reports;
    private final AuthService auth;
    private final View view;

    public AdminController(Repositories repos, AdminService admin, ReportService reports, AuthService auth, View view) {
        this.repos = repos;
        this.admin = admin;
        this.reports = reports;
        this.auth = auth;
        this.view = view;
    }

    /** Which form on the admin page was submitted, with its values and errors. */
    private record FormState(String which, Map<String, String> values, Map<String, String> errors) {
        static final FormState NONE = new FormState("", Map.of(), Map.of());
    }

    public void register(Router router) {
        router.get("/admin", request -> {
            request.require(Role.ADMIN);
            return page(request, FormState.NONE);
        });
        router.post("/admin/weights", request -> {
            request.require(Role.ADMIN);
            try {
                admin.updateWeights(request.form());
                return Response.success("/admin", "Matching weights saved. Open donations were re-matched.");
            } catch (ValidationException e) {
                return page(request, new FormState("weights", request.form(), Map.of("general", e.getMessage())));
            }
        });
        router.post("/admin/organisations", request -> {
            request.require(Role.ADMIN);
            try {
                admin.addOrganisation(request.form());
                return Response.success("/admin", "Organisation registered. Approve it below to activate it.");
            } catch (ValidationException e) {
                return page(request, new FormState("organisation", request.form(), errors(e)));
            }
        });
        router.post("/admin/organisations/{id}/approve", request -> {
            request.require(Role.ADMIN);
            admin.approveOrganisation(request.id("id"));
            return Response.success("/admin", "Organisation approved.");
        });
        router.post("/admin/transporters", request -> {
            request.require(Role.ADMIN);
            try {
                admin.addTransporter(request.form());
                return Response.success("/admin", "Driver added.");
            } catch (ValidationException e) {
                return page(request, new FormState("driver", request.form(), errors(e)));
            }
        });
        router.post("/admin/users", request -> {
            request.require(Role.ADMIN);
            try {
                auth.createUser(request.form());
                return Response.success("/admin#users", "User account created.");
            } catch (ValidationException e) {
                return page(request, new FormState("user", request.form(), errors(e)));
            }
        });
        router.post("/admin/users/{id}/toggle", request -> {
            request.require(Role.ADMIN);
            boolean enable = "true".equals(request.field("active"));
            auth.setActive(request.user(), request.id("id"), enable);
            return Response.success("/admin#users", enable ? "User enabled." : "User disabled. They can no longer sign in.");
        });
        router.post("/admin/transporters/{id}/toggle", request -> {
            request.require(Role.ADMIN);
            boolean activate = "true".equals(request.field("active"));
            admin.setTransporterActive(request.id("id"), activate);
            return Response.success("/admin", activate ? "Driver activated." : "Driver deactivated.");
        });
    }

    private static Map<String, String> errors(ValidationException e) {
        return e.errors().isEmpty() ? Map.of("general", e.getMessage()) : e.errors();
    }

    private Response page(Request request, FormState state) {
        MatchingWeights saved = admin.weights();
        Map<String, String> weightValues = new HashMap<>();
        weightValues.put("expiryUrgency", String.valueOf(saved.expiryUrgency()));
        weightValues.put("recipientDemand", String.valueOf(saved.recipientDemand()));
        weightValues.put("foodSuitability", String.valueOf(saved.foodSuitability()));
        weightValues.put("storageCapacity", String.valueOf(saved.storageCapacity()));
        weightValues.put("quantityCompatibility", String.valueOf(saved.quantityCompatibility()));
        weightValues.put("distance", String.valueOf(saved.distance()));
        weightValues.put("transportAvailability", String.valueOf(saved.transportAvailability()));
        weightValues.put("collectionCompatibility", String.valueOf(saved.collectionCompatibility()));
        if (state.which().equals("weights")) {
            weightValues.putAll(state.values());
        }
        List<Map<String, Object>> weightFields = List.of(
            weight("expiryUrgency", "Expiry urgency", "Food that expires sooner is placed first", weightValues),
            weight("recipientDemand", "Recipient demand", "How urgently the recipient needs this food", weightValues),
            weight("foodSuitability", "Food suitability", "Recipient storage keeps this food safe", weightValues),
            weight("storageCapacity", "Storage capacity", "Room to store the quantity offered", weightValues),
            weight("quantityCompatibility", "Quantity fit", "Share of the donation the recipient can take", weightValues),
            weight("distance", "Distance", "Closer recipients score higher", weightValues),
            weight("transportAvailability", "Transport", "A suitable volunteer vehicle is available", weightValues),
            weight("collectionCompatibility", "Collection window", "Recipient is open before the deadline", weightValues));

        Map<String, String> orgValues = state.which().equals("organisation") ? state.values() : Map.of("role", Organisation.RECIPIENT);
        Map<String, String> driverValues = state.which().equals("driver") ? state.values() : Map.of();
        Map<String, String> userValues = state.which().equals("user") ? state.values() : Map.of("role", Role.COORDINATOR);
        String userRole = userValues.getOrDefault("role", "");
        String userOrg = userValues.getOrDefault("organisationId", "");
        String role = orgValues.getOrDefault("role", Organisation.RECIPIENT);
        String type = orgValues.getOrDefault("organisationType", "");

        return view.page(request, "Admin", "admin", "admin/overview", Model.of(
            "overview", reports.adminOverview(),
            "weightFields", weightFields,
            "weightError", state.which().equals("weights") ? state.errors().get("general") : null,
            "weightTotal", MatchingWeights.DEFAULT.total(),
            "organisations", repos.organisations().findAll(),
            "orgForm", orgValues,
            "orgErrors", state.which().equals("organisation") ? state.errors() : Map.of(),
            "roleOptions", List.of(
                new Model.Option(Organisation.RECIPIENT, "Recipient", !Organisation.DONOR.equals(role)),
                new Model.Option(Organisation.DONOR, "Donor", Organisation.DONOR.equals(role))),
            "recipientTypeOptions", Model.options(Catalog.RECIPIENT_TYPES, type),
            "donorTypeOptions", Model.options(Catalog.DONOR_TYPES, type),
            "areaOptions", Model.options(List.copyOf(Catalog.AREAS.keySet()), orgValues.getOrDefault("area", "")),
            "transporters", repos.transporters().findAll(),
            "driverForm", driverValues,
            "driverErrors", state.which().equals("driver") ? state.errors() : Map.of(),
            "users", auth.allUsers(),
            "userForm", userValues,
            "userErrors", state.which().equals("user") ? state.errors() : Map.of(),
            "userRoleOptions", Role.ALL.stream()
                .map(r -> new Model.Option(r, Role.label(r), r.equals(userRole))).toList(),
            "userDonorOrgOptions", Model.options(repos.organisations().findAll().stream()
                .filter(o -> Organisation.DONOR.equals(o.role())).toList(), Organisation::id, Organisation::name, userOrg),
            "userRecipientOrgOptions", Model.options(repos.organisations().findAll().stream()
                .filter(o -> Organisation.RECIPIENT.equals(o.role())).toList(), Organisation::id, Organisation::name, userOrg),
            "audit", repos.audit().findRecent(30)));
    }

    private static Map<String, Object> weight(String name, String label, String help, Map<String, String> values) {
        return Model.of("name", name, "label", label, "help", help, "value", values.getOrDefault(name, "0"));
    }
}
