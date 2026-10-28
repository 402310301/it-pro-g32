package za.org.foodredistribution.model;

import java.time.Duration;
import java.time.LocalDateTime;

/** Surplus food offered by a donor. */
public record Donation(
    long id,
    String reference,
    long donorOrganisationId,
    String donorName,
    String category,
    String description,
    double quantity,
    String unit,
    String storageRequirement,
    LocalDateTime expiryAt,
    LocalDateTime collectionDeadline,
    String area,
    String address,
    double latitude,
    double longitude,
    boolean transportRequired,
    String status,
    Long recipientOrganisationId,
    String recipientName,
    double quantityAllocated,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {
    public double hoursToExpiry() {
        return Duration.between(LocalDateTime.now(), expiryAt).toMinutes() / 60.0;
    }

    /** Urgency is derived from how soon the food expires. */
    public String urgency() {
        if (DonationStatus.isFinished(status)) {
            return "-";
        }
        double hours = hoursToExpiry();
        if (hours <= 12) {
            return "Critical";
        }
        if (hours <= 24) {
            return "High";
        }
        if (hours <= 72) {
            return "Medium";
        }
        return "Low";
    }

    public String urgencyClass() {
        return Format.cssClass(urgency());
    }

    public String statusClass() {
        return Format.cssClass(status);
    }

    /** Picture for this food category, e.g. /static/images/categories/bakery.png (replaceable). */
    public String imageUrl() {
        return "/static/images/categories/" + Format.cssClass(category) + ".png";
    }

    public String quantityLabel() {
        return Format.number(quantity) + " " + unit;
    }

    public String allocatedLabel() {
        return Format.number(quantityAllocated) + " " + unit;
    }

    public String expiryLabel() {
        return Format.dateTime(expiryAt);
    }

    public String expiryRelative() {
        return Format.relative(expiryAt);
    }

    public String deadlineLabel() {
        return Format.dateTime(collectionDeadline);
    }

    public String deadlineRelative() {
        return Format.relative(collectionDeadline);
    }

    public String createdLabel() {
        return Format.dateTime(createdAt);
    }

    public boolean finished() {
        return DonationStatus.isFinished(status);
    }

    public boolean canRematch() {
        return DonationStatus.MATCHABLE.contains(status);
    }

    public boolean canCancel() {
        return !finished() && !DonationStatus.COLLECTED.equals(status);
    }

    /** Self-collection (no volunteer transport): the donor confirms pickup and delivery here. */
    public boolean canMarkCollected() {
        return DonationStatus.ACCEPTED.equals(status);
    }

    public boolean canMarkCompleted() {
        return DonationStatus.COLLECTED.equals(status) && !transportRequired;
    }

    public boolean hasRecipient() {
        return recipientName != null;
    }
}
