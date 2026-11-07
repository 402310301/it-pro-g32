package za.org.foodredistribution.model;

import java.util.List;

/** How much each factor counts towards a match score. The eight weights must add up to 100. */
public record MatchingWeights(
    int expiryUrgency,
    int recipientDemand,
    int foodSuitability,
    int storageCapacity,
    int quantityCompatibility,
    int distance,
    int transportAvailability,
    int collectionCompatibility
) {
    public static final MatchingWeights DEFAULT = new MatchingWeights(25, 20, 15, 10, 10, 5, 5, 10);

    public int total() {
        return expiryUrgency + recipientDemand + foodSuitability + storageCapacity
            + quantityCompatibility + distance + transportAvailability + collectionCompatibility;
    }

    public void validate() {
        List<Integer> all = List.of(expiryUrgency, recipientDemand, foodSuitability, storageCapacity,
            quantityCompatibility, distance, transportAvailability, collectionCompatibility);
        if (all.stream().anyMatch(weight -> weight < 0)) {
            throw new IllegalArgumentException("Weights cannot be negative.");
        }
        if (total() != 100) {
            throw new IllegalArgumentException("Matching weights must total 100 (currently " + total() + ").");
        }
    }
}
