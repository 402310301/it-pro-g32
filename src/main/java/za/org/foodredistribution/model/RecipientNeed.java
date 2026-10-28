package za.org.foodredistribution.model;

import java.time.LocalDateTime;

/** What a recipient organisation needs, and what it can store. */
public record RecipientNeed(
    long id,
    long organisationId,
    String organisationName,
    String organisationType,
    String area,
    double latitude,
    double longitude,
    boolean organisationApproved,
    String category,
    String description,
    double quantityNeeded,
    double maxPerDonation,
    double storageCapacity,
    String storageType,
    String opensAt,
    String closesAt,
    String urgency,
    boolean active,
    LocalDateTime createdAt
) {
    public String availability() {
        return opensAt + " - " + closesAt;
    }

    public String urgencyClass() {
        return Format.cssClass(urgency);
    }

    public String quantityNeededLabel() {
        return Format.number(quantityNeeded);
    }

    public String maxPerDonationLabel() {
        return Format.number(maxPerDonation);
    }

    public String storageCapacityLabel() {
        return Format.number(storageCapacity);
    }

    /** A copy that is treated as open and still needing food (used by the comparison report). */
    public RecipientNeed asOpen() {
        return new RecipientNeed(id, organisationId, organisationName, organisationType, area, latitude, longitude,
            true, category, description, Math.max(quantityNeeded, maxPerDonation), maxPerDonation, storageCapacity,
            storageType, opensAt, closesAt, urgency, true, createdAt);
    }
}
