package za.org.foodredistribution.web.controllers;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import za.org.foodredistribution.auth.ForbiddenException;
import za.org.foodredistribution.auth.Role;
import za.org.foodredistribution.auth.User;
import za.org.foodredistribution.model.Catalog;
import za.org.foodredistribution.model.Donation;
import za.org.foodredistribution.model.DonationStatus;
import za.org.foodredistribution.model.Format;
import za.org.foodredistribution.model.Match;
import za.org.foodredistribution.model.Organisation;
import za.org.foodredistribution.service.DonationService;
import za.org.foodredistribution.service.Repositories;
import za.org.foodredistribution.service.ValidationException;
import za.org.foodredistribution.web.Model;
import za.org.foodredistribution.web.Request;
import za.org.foodredistribution.web.Response;
import za.org.foodredistribution.web.Router;
import za.org.foodredistribution.web.View;

public final class DonationController {

    private final Repositories repos;
    private final DonationService donations;
    private final View view;

    public DonationController(Repositories repos, DonationService donations, View view) {
        this.repos = repos;
        this.donations = donations;
        this.view = view;
    }

    public void register(Router router) {
        router.get("/donations", this::list);
        router.get("/donations/new", request -> {
            request.require(Role.ADMIN, Role.COORDINATOR, Role.DONOR);
            return form(request, defaults(request.user()), Map.of());
        });
        router.post("/donations", this::create);
        router.get("/donations/{id}", this::detail);
        router.post("/donations/{id}/recalculate", request -> {
            request.require(Role.ADMIN, Role.COORDINATOR);
            long id = request.id("id");
            int found = donations.recalculate(id);
            return Response.success("/donations/" + id, "Matches recalculated: " + found + " suitable recipient"
                + (found == 1 ? "." : "s."));
        });
        router.post("/donations/{id}/cancel", request -> {
            checkMayCancel(request.user(), donations.get(request.id("id")));
            donations.cancel(request.id("id"));
            return Response.success("/donations/" + request.id("id"), "Donation cancelled.");
        });
        router.post("/donations/{id}/collected", request -> {
            checkMayConfirm(request.user(), donations.get(request.id("id")));
            donations.markCollected(request.id("id"));
            return Response.success("/donations/" + request.id("id"), "Marked as collected.");
        });
        router.post("/donations/{id}/completed", request -> {
            checkMayConfirm(request.user(), donations.get(request.id("id")));
            donations.markCompleted(request.id("id"));
            return Response.success("/donations/" + request.id("id"), "Delivery confirmed. Thank you!");
        });
        router.post("/matches/{id}/accept", request -> {
            checkMayDecide(request.user(), request.id("id"));
            long donationId = donations.acceptMatch(request.id("id"), request.field("allocatedQuantity"));
            return Response.success("/donations/" + donationId, "Match accepted.");
        });
        router.post("/matches/{id}/decline", request -> {
            checkMayDecide(request.user(), request.id("id"));
            long donationId = donations.declineMatch(request.id("id"), request.field("reason"));
            return Response.success("/donations/" + donationId, "Match declined. Recommendations were updated.");
        });
    }

    private Response list(Request request) {
        String status = request.query("status");
        String category = request.query("category");
        List<Donation> items = repos.donations().findAll(status, category);
        return view.page(request, "Donations", "donations", "donations/list", Model.of(
            "donations", items,
            "count", items.size(),
            "statusOptions", Model.options(DonationStatus.ALL, status),
            "categoryOptions", Model.options(Catalog.CATEGORIES, category),
            "filtered", !status.isEmpty() || !category.isEmpty()));
    }

    private Map<String, String> defaults(User user) {
        LocalDateTime now = LocalDateTime.now().withSecond(0).withNano(0);
        Map<String, String> values = new HashMap<>();
        values.put("expiryAt", Format.inputDateTime(now.plusHours(24)));
        values.put("collectionDeadline", Format.inputDateTime(now.plusHours(6)));
        values.put("unit", "kg");
        values.put("storageRequirement", "Ambient");
        values.put("area", "Cape Town CBD");
        if (user.isDonor()) {
            Organisation own = repos.organisations().findById(user.organisationId());
            values.put("donorId", String.valueOf(own.id()));
            values.put("area", own.area());
            values.put("address", own.address());
        }
        return values;
    }

    // ------------------------------------------------------------------ who may do what

    /** Donors may only act on their own organisation's donations; admins and coordinators on all. */
    private static boolean ownsAsDonor(User user, Donation donation) {
        return user.isDonor() && user.organisationId() != null && user.organisationId() == donation.donorOrganisationId();
    }

    private static boolean ownsAsRecipient(User user, Donation donation) {
        return user.isRecipient() && user.organisationId() != null
            && user.organisationId().equals(donation.recipientOrganisationId());
    }

    private static boolean mayCancel(User user, Donation donation) {
        return user.canManage() || ownsAsDonor(user, donation);
    }

    private static boolean mayConfirm(User user, Donation donation) {
        return user.canManage() || ownsAsDonor(user, donation) || ownsAsRecipient(user, donation);
    }

    private static boolean mayDecide(User user, Match match) {
        return user.canManage() || (user.isRecipient() && user.organisationId() != null
            && user.organisationId() == match.recipientOrganisationId());
    }

    private static void checkMayCancel(User user, Donation donation) {
        if (!mayCancel(user, donation)) {
            throw new ForbiddenException("Only the donor or a coordinator can cancel this donation.");
        }
    }

    private static void checkMayConfirm(User user, Donation donation) {
        if (!mayConfirm(user, donation)) {
            throw new ForbiddenException("Only the donor, the recipient or a coordinator can confirm this.");
        }
    }

    private void checkMayDecide(User user, long matchId) {
        Match match = repos.matches().findById(matchId);
        if (match != null && !mayDecide(user, match)) {
            throw new ForbiddenException("Only " + match.recipientName() + " or a coordinator can answer this recommendation.");
        }
    }

    /** A match plus whether the signed-in user may accept or decline it. Used by the template. */
    public record MatchCard(Match m, boolean decide) {
    }

    private Response form(Request request, Map<String, String> values, Map<String, String> errors) {
        User user = request.user();
        List<Organisation> donors = repos.organisations().findApprovedByRole(Organisation.DONOR).stream()
            .filter(o -> !user.isDonor() || user.organisationId() == o.id())
            .toList();
        return view.page(request, "Offer surplus food", "donations", "donations/new", Model.of(
            "form", values,
            "errors", errors,
            "hasErrors", !errors.isEmpty(),
            "donorOptions", Model.options(donors, Organisation::id, Organisation::name, values.getOrDefault("donorId", "")),
            "categoryOptions", Model.options(Catalog.CATEGORIES, values.getOrDefault("category", "")),
            "unitOptions", Model.options(Catalog.UNITS, values.getOrDefault("unit", "")),
            "storageOptions", Model.options(Catalog.STORAGE_TYPES, values.getOrDefault("storageRequirement", "")),
            "areaOptions", Model.options(List.copyOf(Catalog.AREAS.keySet()), values.getOrDefault("area", ""))));
    }

    private Response create(Request request) {
        User user = request.require(Role.ADMIN, Role.COORDINATOR, Role.DONOR);
        Map<String, String> submitted = new HashMap<>(request.form());
        if (user.isDonor()) {
            submitted.put("donorId", String.valueOf(user.organisationId())); // donors always list for their own organisation
        }
        try {
            long id = donations.create(submitted);
            Donation donation = donations.get(id);
            return Response.success("/donations/" + id, donation.reference() + " listed. The matching engine found "
                + donations.matchesFor(id).size() + " suitable recipient(s).");
        } catch (ValidationException e) {
            return form(request, submitted, e.errors().isEmpty() ? Map.of("general", e.getMessage()) : e.errors());
        }
    }

    private Response detail(Request request) {
        Donation donation = donations.get(request.id("id"));
        User user = request.user();
        List<Match> matches = donations.matchesFor(donation.id());
        return view.page(request, donation.reference(), "donations", "donations/detail", Model.of(
            "donation", donation,
            "timeline", donations.timeline(donation),
            "matches", matches.stream().map(m -> new MatchCard(m, mayDecide(user, m))).toList(),
            "mayCancel", mayCancel(user, donation),
            "mayConfirm", mayConfirm(user, donation),
            "openMatches", matches.stream().anyMatch(Match::recommended),
            "canDecide", donation.canRematch(),
            "trips", repos.assignments().findByDonation(donation.id()),
            "history", repos.audit().findForRecord("Donation", donation.id()),
            "awaitingDriver", DonationStatus.TRANSPORT_REQUIRED.equals(donation.status())));
    }
}
