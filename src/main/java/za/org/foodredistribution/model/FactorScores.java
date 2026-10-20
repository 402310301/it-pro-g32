package za.org.foodredistribution.model;

import java.util.List;

/** The eight 0-100 factor scores behind one match. */
public record FactorScores(
    double expiryUrgency,
    double recipientDemand,
    double foodSuitability,
    double storageCapacity,
    double quantityCompatibility,
    double distance,
    double transportAvailability,
    double collectionCompatibility
) {
    /** Weighted average of the factors, rounded to one decimal place. */
    public double weightedScore(MatchingWeights weights) {
        double sum = expiryUrgency * weights.expiryUrgency()
            + recipientDemand * weights.recipientDemand()
            + foodSuitability * weights.foodSuitability()
            + storageCapacity * weights.storageCapacity()
            + quantityCompatibility * weights.quantityCompatibility()
            + distance * weights.distance()
            + transportAvailability * weights.transportAvailability()
            + collectionCompatibility * weights.collectionCompatibility();
        return Math.round(sum / weights.total() * 10.0) / 10.0;
    }

    /** Label + value pairs, used to draw the factor bars on the donation page. */
    public List<Factor> asList() {
        return List.of(
            new Factor("Expiry urgency", expiryUrgency),
            new Factor("Recipient demand", recipientDemand),
            new Factor("Food suitability", foodSuitability),
            new Factor("Storage capacity", storageCapacity),
            new Factor("Quantity fit", quantityCompatibility),
            new Factor("Distance", distance),
            new Factor("Transport", transportAvailability),
            new Factor("Collection window", collectionCompatibility));
    }

    public record Factor(String label, double value) {
        public String valueLabel() {
            return String.valueOf(Math.round(value));
        }

        public long width() {
            return Math.round(Math.max(0, Math.min(100, value)));
        }
    }
}
