package za.org.foodredistribution.model;

/** A recommended pairing between a donation and a recipient need. */
public record Match(
    long id,
    long donationId,
    long needId,
    long recipientOrganisationId,
    String recipientName,
    String recipientType,
    String recipientArea,
    String demand,
    String storageType,
    double storageCapacityAvailable,
    String availability,
    int rank,
    double score,
    double distanceKm,
    double quantityCanAccept,
    FactorScores factors,
    String explanation,
    String status,
    String declineReason
) {
    public static final String RECOMMENDED = "RECOMMENDED";
    public static final String ACCEPTED = "ACCEPTED";
    public static final String DECLINED = "DECLINED";
    public static final String NOT_SELECTED = "NOT_SELECTED";

    public String scoreLabel() {
        return Format.number(score);
    }

    public String distanceLabel() {
        return Format.number(distanceKm) + " km";
    }

    public String quantityLabel() {
        return Format.number(quantityCanAccept);
    }

    public boolean recommended() {
        return RECOMMENDED.equals(status);
    }

    public boolean topMatch() {
        return rank == 1 && recommended();
    }

    public String statusLabel() {
        return switch (status) {
            case ACCEPTED -> "Accepted";
            case DECLINED -> "Declined";
            case NOT_SELECTED -> "Not selected";
            default -> "Recommended";
        };
    }

    public String statusClass() {
        return Format.cssClass(statusLabel());
    }

    public String scoreClass() {
        return score >= 80 ? "high" : score >= 60 ? "medium" : "low";
    }
}
