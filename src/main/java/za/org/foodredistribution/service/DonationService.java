package za.org.foodredistribution.service;

import za.org.foodredistribution.auth.Actor;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import za.org.foodredistribution.model.AuditEntry;
import za.org.foodredistribution.model.Catalog;
import za.org.foodredistribution.model.Donation;
import za.org.foodredistribution.model.DonationStatus;
import za.org.foodredistribution.model.Format;
import za.org.foodredistribution.model.Match;
import za.org.foodredistribution.model.MatchingWeights;
import za.org.foodredistribution.model.Organisation;
import za.org.foodredistribution.model.RecipientNeed;

/** Everything that happens to a donation: listing, matching, accepting, collecting, expiring. */
public class DonationService {

    public static final String SYSTEM = "System";
    public static final String MATCHING_ENGINE = "Matching engine";
    public static final String COORDINATOR = "Coordinator";

    private final Repositories repos;
    private final MatchingEngine engine;
    private final EventLog events;

    public DonationService(Repositories repos, MatchingEngine engine, EventLog events) {
        this.repos = repos;
        this.engine = engine;
        this.events = events;
    }

    public Donation get(long id) {
        Donation donation = repos.donations().findById(id);
        if (donation == null) {
            throw new NotFoundException("Donation #" + id + " was not found.");
        }
        return donation;
    }

    // ------------------------------------------------------------------ create

    /** Validates the "Offer surplus food" form, saves the donation and runs the matching engine. */
    public long create(Map<String, String> form) {
        FormReader f = new FormReader(form);
        long donorId = f.id("donorId", "Donor organisation");
        String category = f.oneOf("category", "Category", Catalog.CATEGORIES);
        String description = f.required("description", "Description", 1000);
        double quantity = f.number("quantity", "Quantity", 0, false);
        String unit = f.oneOf("unit", "Unit", Catalog.UNITS);
        String storage = f.oneOf("storageRequirement", "Storage requirement", Catalog.STORAGE_TYPES);
        LocalDateTime expiryAt = f.dateTime("expiryAt", "Expiry");
        LocalDateTime deadline = f.dateTime("collectionDeadline", "Collection deadline");
        String area = f.oneOf("area", "Area", List.copyOf(Catalog.AREAS.keySet()));
        String address = f.required("address", "Collection address", 255);
        boolean transportRequired = f.checkbox("transportRequired");
        if (!f.checkbox("foodSafetyConfirmed")) {
            f.error("foodSafetyConfirmed", "You must confirm the food is safe to eat.");
        }

        LocalDateTime now = LocalDateTime.now();
        if (expiryAt != null && !expiryAt.isAfter(now)) {
            f.error("expiryAt", "Expiry must be in the future.");
        }
        if (deadline != null && !deadline.isAfter(now)) {
            f.error("collectionDeadline", "Collection deadline must be in the future.");
        }
        if (expiryAt != null && deadline != null && deadline.isAfter(expiryAt)) {
            f.error("collectionDeadline", "Food must be collected before it expires.");
        }
        Organisation donor = donorId > 0 ? repos.organisations().findById(donorId) : null;
        if (!f.hasError("donorId") && (donor == null || !Organisation.DONOR.equals(donor.role()) || !donor.approved())) {
            f.error("donorId", "Choose an approved donor organisation.");
        }
        f.throwIfErrors();

        return repos.db().inTransaction(() -> {
            double[] coordinates = Catalog.coordinatesOf(area);
            long id = repos.donations().insert(nextReference(now), donorId, category, description, quantity, unit,
                storage, expiryAt, deadline, area, address, coordinates[0], coordinates[1], transportRequired,
                DonationStatus.LISTED, now);
            events.audit(donor.name(), "Donation listed", "Donation", id, null, DonationStatus.LISTED);
            int found = runMatching(get(id));
            Donation saved = get(id);
            events.notify(id, "donation", "Donation listed",
                saved.reference() + " was listed and matched with " + found + " suitable organisation"
                    + (found == 1 ? "." : "s."));
            return id;
        });
    }

    private String nextReference(LocalDateTime now) {
        String prefix = "DON-" + now.format(DateTimeFormatter.ofPattern("yyMMdd")) + "-";
        long next = repos.donations().countByReferencePrefix(prefix) + 1;
        return prefix + String.format("%03d", next);
    }

    // ------------------------------------------------------------------ matching

    /**
     * (Re)calculates recommendations for a donation. Recipients who already accepted or declined
     * keep their decision. Returns the number of recommended recipients.
     */
    public int runMatching(Donation donation) {
        if (!donation.canRematch()) {
            throw new ValidationException("Matches can only be recalculated before a recipient accepts.");
        }
        return repos.db().inTransaction(() -> {
            LocalDateTime now = LocalDateTime.now();
            repos.matches().deleteUndecided(donation.id());
            Set<Long> decided = repos.matches().decidedNeedIds(donation.id());
            List<RecipientNeed> needs = repos.needs().findMatchable(donation.category()).stream()
                .filter(need -> !decided.contains(need.id()))
                .toList();
            MatchingWeights weights = repos.config().load();
            List<MatchingEngine.Candidate> ranked = engine.rank(donation, needs,
                repos.transporters().findActive(), weights, now);

            int rank = 1;
            for (MatchingEngine.Candidate c : ranked) {
                repos.matches().insert(donation.id(), c.need().id(), rank++, c.score(), c.distanceKm(),
                    c.quantityCanAccept(), c.factors(), c.explanation(), now);
            }
            String newStatus = ranked.isEmpty() ? DonationStatus.MATCHING : DonationStatus.RECIPIENT_RECOMMENDED;
            if (!newStatus.equals(donation.status())) {
                repos.donations().updateStatus(donation.id(), newStatus, now);
                events.audit(MATCHING_ENGINE, ranked.isEmpty() ? "No suitable recipient yet" : "Matches generated",
                    "Donation", donation.id(), donation.status(), newStatus);
            }
            boolean newlyRecommended = !DonationStatus.RECIPIENT_RECOMMENDED.equals(donation.status());
            if (newlyRecommended && !ranked.isEmpty() && ranked.get(0).score() >= 80) {
                MatchingEngine.Candidate top = ranked.get(0);
                events.notify(donation.id(), "match", "Strong recipient match found",
                    top.need().organisationName() + " scored " + Format.number(top.score()) + "% for "
                        + donation.reference() + ".");
            }
            return ranked.size();
        });
    }

    public int recalculate(long donationId) {
        return runMatching(get(donationId));
    }

    /** Re-runs matching for every donation still waiting for a recipient (e.g. after new needs or weights). */
    public void rematchOpenDonations(String categoryOrNull) {
        List<Donation> open = repos.donations().findAll(null, categoryOrNull).stream()
            .filter(Donation::canRematch)
            .toList();
        for (Donation donation : open) {
            runMatching(donation);
        }
    }

    public List<Match> matchesFor(long donationId) {
        return repos.matches().findByDonation(donationId);
    }

    // ------------------------------------------------------------------ recipient decisions

    public long acceptMatch(long matchId, String allocatedText) {
        Match match = repos.matches().findById(matchId);
        if (match == null) {
            throw new NotFoundException("Match #" + matchId + " was not found.");
        }
        Donation donation = get(match.donationId());
        if (!match.recommended() || !donation.canRematch()) {
            throw new ValidationException("This recommendation is no longer open.");
        }
        double allocated = match.quantityCanAccept();
        if (allocatedText != null && !allocatedText.isBlank()) {
            try {
                allocated = Double.parseDouble(allocatedText.trim());
            } catch (NumberFormatException e) {
                throw new ValidationException("Allocated quantity must be a number.");
            }
        }
        if (allocated <= 0 || allocated > match.quantityCanAccept()) {
            throw new ValidationException("Allocated quantity must be between 1 and "
                + Format.number(match.quantityCanAccept()) + ".");
        }
        double finalAllocated = allocated;
        repos.db().inTransaction(() -> {
            LocalDateTime now = LocalDateTime.now();
            repos.matches().updateStatus(match.id(), Match.ACCEPTED, null);
            repos.matches().closeOtherRecommendations(donation.id(), match.id());
            String newStatus = donation.transportRequired() ? DonationStatus.TRANSPORT_REQUIRED : DonationStatus.ACCEPTED;
            repos.donations().assignRecipient(donation.id(), match.recipientOrganisationId(), finalAllocated, newStatus, now);

            RecipientNeed need = repos.needs().findById(match.needId());
            double remaining = Math.max(0, need.quantityNeeded() - finalAllocated);
            repos.needs().updateQuantityNeeded(need.id(), remaining, remaining > 0);

            events.audit(match.recipientName(), "Match accepted", "Donation", donation.id(), donation.status(), newStatus);
            events.notify(donation.id(), "match", "Recipient accepted",
                match.recipientName() + " accepted " + Format.number(finalAllocated) + " " + donation.unit() + " of "
                    + donation.reference() + (donation.transportRequired() ? ". A volunteer driver is now needed." : "."));
        });
        return donation.id();
    }

    public long declineMatch(long matchId, String reason) {
        Match match = repos.matches().findById(matchId);
        if (match == null) {
            throw new NotFoundException("Match #" + matchId + " was not found.");
        }
        Donation donation = get(match.donationId());
        if (!match.recommended()) {
            throw new ValidationException("This recommendation is no longer open.");
        }
        String cleanReason = reason == null || reason.isBlank() ? "No reason given" : reason.trim();
        repos.db().inTransaction(() -> {
            repos.matches().updateStatus(match.id(), Match.DECLINED,
                cleanReason.length() > 255 ? cleanReason.substring(0, 255) : cleanReason);
            events.audit(match.recipientName(), "Match declined", "Match", match.id(), Match.RECOMMENDED, Match.DECLINED);
            runMatching(get(donation.id()));
            List<Match> remaining = repos.matches().findByDonation(donation.id()).stream().filter(Match::recommended).toList();
            events.notify(donation.id(), "reassignment", "Donation reassigned",
                remaining.isEmpty()
                    ? donation.reference() + " was declined by " + match.recipientName() + " and returned to the matching pool."
                    : donation.reference() + " was declined by " + match.recipientName() + " and is now recommended to "
                        + remaining.get(0).recipientName() + ".");
        });
        return donation.id();
    }

    // ------------------------------------------------------------------ donor actions

    public void cancel(long donationId) {
        Donation donation = get(donationId);
        if (!donation.canCancel()) {
            throw new ValidationException("This donation can no longer be cancelled.");
        }
        repos.db().inTransaction(() -> {
            changeStatus(donation, DonationStatus.CANCELLED, Actor.nameOr(COORDINATOR), "Donation cancelled");
            repos.matches().closeAllRecommendations(donationId);
            repos.assignments().findByDonation(donationId).stream()
                .filter(a -> a.active())
                .forEach(a -> repos.assignments().updateStatus(a.id(), "CANCELLED", LocalDateTime.now()));
        });
    }

    public void markCollected(long donationId) {
        Donation donation = get(donationId);
        if (!donation.canMarkCollected()) {
            throw new ValidationException("Only accepted self-collection donations can be marked as collected here.");
        }
        changeStatus(donation, DonationStatus.COLLECTED, donation.recipientName(), "Collected by recipient");
    }

    public void markCompleted(long donationId) {
        Donation donation = get(donationId);
        if (!donation.canMarkCompleted()) {
            throw new ValidationException("Only collected self-collection donations can be completed here.");
        }
        changeStatus(donation, DonationStatus.COMPLETED, donation.recipientName(), "Delivery confirmed");
        events.notify(donationId, "delivery", "Delivery confirmed",
            donation.recipientName() + " received " + donation.allocatedLabel() + " from " + donation.donorName() + ".");
    }

    void changeStatus(Donation donation, String newStatus, String actor, String action) {
        repos.donations().updateStatus(donation.id(), newStatus, LocalDateTime.now());
        events.audit(actor, action, "Donation", donation.id(), donation.status(), newStatus);
    }

    /** Marks donations whose expiry time has passed. Runs every minute in the background. */
    public int expireOverdue() {
        List<Donation> overdue = repos.donations().findOverdue(LocalDateTime.now());
        for (Donation donation : overdue) {
            repos.db().inTransaction(() -> {
                changeStatus(donation, DonationStatus.EXPIRED, SYSTEM, "Donation expired");
                repos.matches().closeAllRecommendations(donation.id());
                events.notify(donation.id(), "expiry", "Donation expired",
                    donation.reference() + " from " + donation.donorName() + " expired before it was collected.");
            });
        }
        return overdue.size();
    }

    // ------------------------------------------------------------------ timeline

    public record Stage(String label, String state, String time) {
    }

    /** The progress steps shown on the donation page, with times taken from the audit trail. */
    public List<Stage> timeline(Donation donation) {
        List<String> stages = new ArrayList<>(List.of(DonationStatus.LISTED, DonationStatus.RECIPIENT_RECOMMENDED,
            DonationStatus.ACCEPTED, DonationStatus.TRANSPORT_ASSIGNED, DonationStatus.COLLECTED, DonationStatus.COMPLETED));
        if (!donation.transportRequired()) {
            stages.remove(DonationStatus.TRANSPORT_ASSIGNED);
        }
        List<AuditEntry> history = repos.audit().findForRecord("Donation", donation.id());
        String[] times = new String[stages.size()];
        int reached = 0;
        for (AuditEntry entry : history) {
            int index = stageIndex(stages, entry.newStatus());
            if (index >= 0) {
                if (times[index] == null) {
                    times[index] = Format.shortDateTime(entry.createdAt());
                }
                reached = Math.max(reached, index);
            }
        }
        reached = Math.max(reached, Math.max(0, stageIndex(stages, donation.status())));
        if (times[0] == null) {
            times[0] = Format.shortDateTime(donation.createdAt());
        }

        boolean ended = donation.status().equals(DonationStatus.EXPIRED) || donation.status().equals(DonationStatus.CANCELLED);
        List<Stage> result = new ArrayList<>();
        for (int i = 0; i < stages.size(); i++) {
            String state;
            if (i < reached || (i == reached && donation.status().equals(DonationStatus.COMPLETED))) {
                state = "done";
            } else if (i == reached) {
                state = ended ? "done" : "current";
            } else {
                state = "pending";
            }
            result.add(new Stage(stages.get(i), state, times[i] == null ? "" : times[i]));
        }
        if (ended) {
            result.add(new Stage(donation.status(), "ended", Format.shortDateTime(donation.updatedAt())));
        }
        return result;
    }

    private static int stageIndex(List<String> stages, String status) {
        if (status == null) {
            return -1;
        }
        String stage = switch (status) {
            case DonationStatus.MATCHING -> DonationStatus.LISTED;
            case DonationStatus.TRANSPORT_REQUIRED -> DonationStatus.ACCEPTED;
            default -> status;
        };
        return stages.indexOf(stage);
    }
}
