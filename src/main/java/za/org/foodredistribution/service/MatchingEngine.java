package za.org.foodredistribution.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import za.org.foodredistribution.model.Donation;
import za.org.foodredistribution.model.FactorScores;
import za.org.foodredistribution.model.Format;
import za.org.foodredistribution.model.MatchingWeights;
import za.org.foodredistribution.model.RecipientNeed;
import za.org.foodredistribution.model.Transporter;

/**
 * The "intelligent" part of the platform. For one donation it scores every candidate recipient need
 * on eight factors (each 0-100), combines them with the admin-configured weights, drops unsafe
 * matches, and returns the candidates best-first.
 *
 * This class has no database code, so it is easy to read and to test on its own.
 */
public final class MatchingEngine {

    /** Below this food-suitability score the recipient cannot store the food safely, so it is never recommended. */
    public static final double MIN_FOOD_SUITABILITY = 50;

    public record Candidate(
        RecipientNeed need,
        FactorScores factors,
        double score,
        double distanceKm,
        double quantityCanAccept,
        String explanation
    ) {
    }

    public List<Candidate> rank(Donation donation, List<RecipientNeed> needs, List<Transporter> transporters,
                                MatchingWeights weights, LocalDateTime now) {
        weights.validate();
        List<Candidate> candidates = new ArrayList<>();
        for (RecipientNeed need : needs) {
            Candidate candidate = evaluate(donation, need, transporters, weights, now);
            if (candidate != null) {
                candidates.add(candidate);
            }
        }
        candidates.sort(Comparator.comparingDouble(Candidate::score).reversed()
            .thenComparingDouble(Candidate::distanceKm));
        return candidates;
    }

    /** Scores one recipient need, or returns null when it is not a safe or possible match. */
    public Candidate evaluate(Donation donation, RecipientNeed need, List<Transporter> transporters,
                              MatchingWeights weights, LocalDateTime now) {
        if (!need.active() || !need.organisationApproved() || need.quantityNeeded() <= 0) {
            return null;
        }
        Candidate candidate = assess(donation, need, transporters, weights, now);
        if (candidate == null
            || candidate.factors().foodSuitability() < MIN_FOOD_SUITABILITY
            || candidate.quantityCanAccept() <= 0
            || candidate.factors().collectionCompatibility() <= 0) {
            return null;
        }
        return candidate;
    }

    /**
     * Calculates all factor scores for a donation and a need of the same category, without
     * rejecting unsafe pairs. Used directly by the "first come, first served" comparison report.
     */
    public Candidate assess(Donation donation, RecipientNeed need, List<Transporter> transporters,
                            MatchingWeights weights, LocalDateTime now) {
        if (!need.category().equals(donation.category())) {
            return null;
        }
        double distance = distanceKm(donation.latitude(), donation.longitude(), need.latitude(), need.longitude());
        double offered = Math.min(donation.quantity(), need.maxPerDonation());
        double canAccept = Math.min(offered, need.storageCapacity());

        FactorScores factors = round(new FactorScores(
            expiryUrgency(donation.expiryAt(), now),
            demandScore(need.urgency()),
            storageSuitability(donation.storageRequirement(), need.storageType()),
            offered <= 0 ? 0 : Math.min(100, need.storageCapacity() / offered * 100),
            Math.min(100, canAccept / donation.quantity() * 100),
            Math.max(20, 100 - distance * 4),
            transportScore(donation, transporters),
            collectionScore(now, donation.collectionDeadline(), need.opensAt(), need.closesAt())));

        double score = factors.weightedScore(weights);
        String explanation = String.format(Locale.ENGLISH,
            "%s%% match because %s has %s demand for %s, can accept %s %s with %s storage, is open %s - %s "
                + "and is %s km from the donor.",
            Format.number(score), need.organisationName(), need.urgency().toLowerCase(Locale.ENGLISH),
            donation.category().toLowerCase(Locale.ENGLISH), Format.number(canAccept), donation.unit(),
            need.storageType().toLowerCase(Locale.ENGLISH), need.opensAt(), need.closesAt(), Format.number(distance));
        return new Candidate(need, factors, score, distance, canAccept, explanation);
    }

    // ------------------------------------------------------------------ factor rules

    /** The sooner the food expires, the more urgent it is to place it. */
    static double expiryUrgency(LocalDateTime expiryAt, LocalDateTime now) {
        double hours = ChronoUnit.MINUTES.between(now, expiryAt) / 60.0;
        if (hours <= 6) {
            return 100;
        }
        if (hours <= 12) {
            return 90;
        }
        if (hours <= 24) {
            return 75;
        }
        if (hours <= 72) {
            return 55;
        }
        return 30;
    }

    static double demandScore(String urgency) {
        return switch (urgency) {
            case "Critical" -> 100;
            case "High" -> 85;
            case "Medium" -> 65;
            default -> 40;
        };
    }

    /** Can the recipient keep this food safe? Colder storage can hold warmer food, not the other way round. */
    static double storageSuitability(String foodNeeds, String recipientHas) {
        if (foodNeeds.equalsIgnoreCase(recipientHas)) {
            return 100;
        }
        return switch (foodNeeds + "->" + recipientHas) {
            case "Ambient->Cool" -> 90;
            case "Ambient->Refrigerated" -> 80;
            case "Ambient->Frozen" -> 30;
            case "Cool->Refrigerated" -> 90;
            case "Cool->Ambient" -> 30;
            case "Cool->Frozen" -> 20;
            case "Refrigerated->Cool" -> 40;
            case "Refrigerated->Frozen" -> 30;
            default -> 0;
        };
    }

    /** Is a volunteer vehicle available that can carry this donation? */
    static double transportScore(Donation donation, List<Transporter> transporters) {
        if (!donation.transportRequired()) {
            return 100;
        }
        boolean needsCold = donation.storageRequirement().equals("Refrigerated")
            || donation.storageRequirement().equals("Frozen");
        boolean suitable = transporters.stream().anyMatch(t -> t.active()
            && t.capacityKg() >= donation.quantity()
            && (!needsCold || t.refrigerated()));
        if (suitable) {
            return 90;
        }
        return transporters.stream().anyMatch(Transporter::active) ? 60 : 20;
    }

    /**
     * How many of the recipient's opening hours fall between now and the donor's collection deadline.
     * Two or more hours of overlap is ideal; no overlap at all means the food cannot be handed over.
     */
    static double collectionScore(LocalDateTime now, LocalDateTime deadline, String opensAt, String closesAt) {
        if (!deadline.isAfter(now)) {
            return 0;
        }
        LocalTime open = LocalTime.parse(opensAt);
        LocalTime close = LocalTime.parse(closesAt);
        long overlapMinutes = 0;
        LocalDate day = now.toLocalDate();
        for (int i = 0; i < 8 && !day.isAfter(deadline.toLocalDate()); i++, day = day.plusDays(1)) {
            LocalDateTime windowStart = max(day.atTime(open), now);
            LocalDateTime windowEnd = min(day.atTime(close), deadline);
            if (windowEnd.isAfter(windowStart)) {
                overlapMinutes += ChronoUnit.MINUTES.between(windowStart, windowEnd);
            }
        }
        if (overlapMinutes >= 120) {
            return 100;
        }
        if (overlapMinutes >= 60) {
            return 80;
        }
        if (overlapMinutes > 0) {
            return 60;
        }
        return 15;
    }

    /** Straight-line (haversine) distance in kilometres, rounded to 0.1 km. */
    public static double distanceKm(double lat1, double lon1, double lat2, double lon2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double h = Math.pow(Math.sin(dLat / 2), 2)
            + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) * Math.pow(Math.sin(dLon / 2), 2);
        double km = 6371 * 2 * Math.atan2(Math.sqrt(h), Math.sqrt(1 - h));
        return Math.round(km * 10) / 10.0;
    }

    private static FactorScores round(FactorScores f) {
        return new FactorScores(r(f.expiryUrgency()), r(f.recipientDemand()), r(f.foodSuitability()),
            r(f.storageCapacity()), r(f.quantityCompatibility()), r(f.distance()), r(f.transportAvailability()),
            r(f.collectionCompatibility()));
    }

    private static double r(double value) {
        return Math.round(value * 10) / 10.0;
    }

    private static LocalDateTime max(LocalDateTime a, LocalDateTime b) {
        return a.isAfter(b) ? a : b;
    }

    private static LocalDateTime min(LocalDateTime a, LocalDateTime b) {
        return a.isBefore(b) ? a : b;
    }
}
