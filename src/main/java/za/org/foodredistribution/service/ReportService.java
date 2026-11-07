package za.org.foodredistribution.service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Predicate;
import za.org.foodredistribution.model.AuditEntry;
import za.org.foodredistribution.model.Donation;
import za.org.foodredistribution.model.DonationStatus;
import za.org.foodredistribution.model.Format;
import za.org.foodredistribution.model.Match;
import za.org.foodredistribution.model.MatchingWeights;
import za.org.foodredistribution.model.Organisation;
import za.org.foodredistribution.model.RecipientNeed;
import za.org.foodredistribution.model.Transporter;
import za.org.foodredistribution.model.TransportAssignment;

/** Numbers for the dashboard, the impact report and the admin overview. All calculated from the database. */
public class ReportService {

    private final Repositories repos;
    private final MatchingEngine engine;

    public ReportService(Repositories repos, MatchingEngine engine) {
        this.repos = repos;
        this.engine = engine;
    }

    // ------------------------------------------------------------------ dashboard

    public record Summary(long active, long awaitingDecision, long awaitingDriver, long awaitingCollection,
                          long completed, long expired, long total, List<String> redistributed, long unread) {
        public boolean hasUnread() {
            return unread > 0;
        }
    }

    public Summary summary() {
        List<Donation> all = repos.donations().findAll();
        return new Summary(
            count(all, d -> !d.finished()),
            count(all, d -> DonationStatus.MATCHABLE.contains(d.status())),
            count(all, d -> DonationStatus.TRANSPORT_REQUIRED.equals(d.status())),
            count(all, d -> DonationStatus.TRANSPORT_ASSIGNED.equals(d.status()) || DonationStatus.ACCEPTED.equals(d.status())),
            count(all, d -> DonationStatus.COMPLETED.equals(d.status())),
            count(all, d -> DonationStatus.EXPIRED.equals(d.status())),
            all.size(),
            redistributedByUnit(all),
            repos.notifications().countUnread());
    }

    /** Open donations that expire within the next 24 hours, soonest first. */
    public List<Donation> expiringSoon() {
        LocalDateTime limit = LocalDateTime.now().plusHours(24);
        return repos.donations().findAll().stream()
            .filter(d -> !d.finished() && !DonationStatus.COLLECTED.equals(d.status()) && d.expiryAt().isBefore(limit))
            .sorted(Comparator.comparing(Donation::expiryAt))
            .toList();
    }

    // ------------------------------------------------------------------ impact report

    public record Bar(String label, long listed, long completed, long listedHeight, long completedHeight) {
    }

    public record Impact(long total, long completed, long expired, long cancelled, List<String> redistributed,
                         String matchRate, String collectionRate, String expiredRate, String averageDistance,
                         String adequateStorageRate, String averageAcceptance, List<Bar> months) {
    }

    public Impact impact() {
        List<Donation> all = repos.donations().findAll();
        long matched = count(all, d -> d.recipientOrganisationId() != null);
        long completed = count(all, d -> DonationStatus.COMPLETED.equals(d.status()));
        long expired = count(all, d -> DonationStatus.EXPIRED.equals(d.status()));
        long cancelled = count(all, d -> DonationStatus.CANCELLED.equals(d.status()));

        List<Match> accepted = repos.matches().findByStatus(Match.ACCEPTED);
        double averageDistance = accepted.stream().mapToDouble(Match::distanceKm).average().orElse(0);
        long adequateStorage = accepted.stream()
            .filter(m -> m.factors().foodSuitability() >= 80 && m.factors().storageCapacity() >= 100).count();

        Map<Long, Donation> byId = new LinkedHashMap<>();
        all.forEach(d -> byId.put(d.id(), d));
        List<Double> acceptanceHours = new ArrayList<>();
        for (AuditEntry entry : repos.audit().findByAction("Match accepted")) {
            Donation donation = byId.get(entry.recordId());
            if (donation != null) {
                acceptanceHours.add(Duration.between(donation.createdAt(), entry.createdAt()).toMinutes() / 60.0);
            }
        }
        double averageAcceptance = acceptanceHours.stream().mapToDouble(Double::doubleValue).average().orElse(0);

        return new Impact(all.size(), completed, expired, cancelled, redistributedByUnit(all),
            percent(matched, all.size()), percent(completed, matched), percent(expired, all.size()),
            accepted.isEmpty() ? "-" : Format.number(averageDistance) + " km",
            percent(adequateStorage, accepted.size()),
            acceptanceHours.isEmpty() ? "-" : Format.number(averageAcceptance) + " h",
            monthlyBars(all));
    }

    private List<Bar> monthlyBars(List<Donation> all) {
        YearMonth current = YearMonth.now();
        DateTimeFormatter label = DateTimeFormatter.ofPattern("MMM", Locale.ENGLISH);
        List<long[]> counts = new ArrayList<>();
        List<String> labels = new ArrayList<>();
        for (int i = 5; i >= 0; i--) {
            YearMonth month = current.minusMonths(i);
            long listed = count(all, d -> YearMonth.from(d.createdAt()).equals(month));
            long done = count(all, d -> DonationStatus.COMPLETED.equals(d.status())
                && YearMonth.from(d.updatedAt()).equals(month));
            counts.add(new long[] {listed, done});
            labels.add(month.format(label));
        }
        long max = Math.max(1, counts.stream().mapToLong(c -> Math.max(c[0], c[1])).max().orElse(1));
        List<Bar> bars = new ArrayList<>();
        for (int i = 0; i < counts.size(); i++) {
            long[] c = counts.get(i);
            bars.add(new Bar(labels.get(i), c[0], c[1], c[0] * 100 / max, c[1] * 100 / max));
        }
        return bars;
    }

    // ------------------------------------------------------------------ intelligent vs first-come-first-served

    public record Strategy(String matchedRate, String averageDistance, String adequateStorageRate,
                           String averageQuantityFit, String averageScore) {
    }

    public record Scenario(String reference, String category, String intelligent, String baseline) {
    }

    public record Comparison(int donationsCompared, Strategy intelligent, Strategy baseline, List<Scenario> scenarios) {
    }

    /**
     * Replays every donation against today's recipient needs twice: once with the matching engine
     * (best score, unsafe storage rejected) and once "first come, first served" (the earliest-registered
     * recipient in the same category, whatever its storage or distance).
     */
    public Comparison comparison() {
        MatchingWeights weights = repos.config().load();
        List<Transporter> transporters = repos.transporters().findActive();
        List<RecipientNeed> needs = repos.needs().findAll().stream()
            .filter(RecipientNeed::organisationApproved)
            .map(RecipientNeed::asOpen)
            .toList();

        List<MatchingEngine.Candidate> smart = new ArrayList<>();
        List<MatchingEngine.Candidate> fcfs = new ArrayList<>();
        List<Scenario> scenarios = new ArrayList<>();
        int compared = 0;
        for (Donation donation : repos.donations().findAll()) {
            List<RecipientNeed> sameCategory = needs.stream().filter(n -> n.category().equals(donation.category())).toList();
            if (sameCategory.isEmpty() || DonationStatus.CANCELLED.equals(donation.status())) {
                continue;
            }
            compared++;
            LocalDateTime listedAt = donation.createdAt();
            List<MatchingEngine.Candidate> ranked = engine.rank(donation, sameCategory, transporters, weights, listedAt);
            MatchingEngine.Candidate best = ranked.isEmpty() ? null : ranked.get(0);
            MatchingEngine.Candidate first = engine.assess(donation, sameCategory.get(0), transporters, weights, listedAt);
            if (best != null) {
                smart.add(best);
            }
            fcfs.add(first);
            if (best != null && best.need().id() != first.need().id() && scenarios.size() < 4) {
                scenarios.add(new Scenario(donation.reference(), donation.category(), describe(best), describe(first)));
            }
        }
        return new Comparison(compared, strategy(smart, compared), strategy(fcfs, compared), scenarios);
    }

    private static String describe(MatchingEngine.Candidate c) {
        boolean safe = c.factors().foodSuitability() >= MatchingEngine.MIN_FOOD_SUITABILITY;
        return c.need().organisationName() + " - " + Format.number(c.distanceKm()) + " km away, "
            + c.need().storageType().toLowerCase(Locale.ENGLISH) + " storage"
            + (safe ? "" : " (not safe for this food)")
            + ", takes " + Format.number(c.factors().quantityCompatibility()) + "% of the donation.";
    }

    private static Strategy strategy(List<MatchingEngine.Candidate> picks, int compared) {
        if (picks.isEmpty()) {
            return new Strategy("0%", "-", "-", "-", "-");
        }
        long safe = picks.stream()
            .filter(c -> c.factors().foodSuitability() >= MatchingEngine.MIN_FOOD_SUITABILITY && c.quantityCanAccept() > 0)
            .count();
        long adequate = picks.stream()
            .filter(c -> c.factors().foodSuitability() >= 80 && c.factors().storageCapacity() >= 100).count();
        return new Strategy(
            percent(safe, compared),
            Format.number(picks.stream().mapToDouble(MatchingEngine.Candidate::distanceKm).average().orElse(0)) + " km",
            percent(adequate, picks.size()),
            Format.number(picks.stream().mapToDouble(c -> c.factors().quantityCompatibility()).average().orElse(0)) + "%",
            Format.number(picks.stream().mapToDouble(MatchingEngine.Candidate::score).average().orElse(0)));
    }

    // ------------------------------------------------------------------ admin overview

    public record AdminOverview(long approvedOrganisations, long pendingOrganisations, long donors, long recipients,
                                long activeDrivers, long tripsInProgress, long activeNeeds, long activeDonations) {
    }

    public AdminOverview adminOverview() {
        List<Organisation> organisations = repos.organisations().findAll();
        List<TransportAssignment> trips = repos.assignments().findAll();
        return new AdminOverview(
            count(organisations, Organisation::approved),
            count(organisations, o -> !o.approved()),
            count(organisations, o -> Organisation.DONOR.equals(o.role())),
            count(organisations, o -> Organisation.RECIPIENT.equals(o.role())),
            repos.transporters().findActive().size(),
            count(trips, TransportAssignment::active),
            count(repos.needs().findAll(), RecipientNeed::active),
            count(repos.donations().findAll(), d -> !d.finished()));
    }

    // ------------------------------------------------------------------ helpers

    /** e.g. ["640 kg", "120 meals"] - completed donations, per unit. */
    private static List<String> redistributedByUnit(List<Donation> all) {
        Map<String, Double> totals = new LinkedHashMap<>();
        all.stream()
            .filter(d -> DonationStatus.COMPLETED.equals(d.status()))
            .forEach(d -> totals.merge(d.unit(), d.quantityAllocated(), Double::sum));
        List<String> result = new ArrayList<>();
        totals.forEach((unit, total) -> result.add(Format.number(total) + " " + unit));
        if (result.isEmpty()) {
            result.add("0 kg");
        }
        return result;
    }

    private static <T> long count(List<T> items, Predicate<T> test) {
        return items.stream().filter(Objects::nonNull).filter(test).count();
    }

    private static String percent(long part, long whole) {
        return whole == 0 ? "-" : Math.round(part * 100.0 / whole) + "%";
    }
}
