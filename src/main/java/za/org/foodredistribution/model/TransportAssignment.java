package za.org.foodredistribution.model;

import java.time.LocalDateTime;
import java.util.List;

/** A volunteer driver collecting a donation and delivering it to the recipient. */
public record TransportAssignment(
    long id,
    long donationId,
    String reference,
    String donorName,
    String pickupAddress,
    String recipientName,
    String deliveryArea,
    String quantityLabel,
    String storageRequirement,
    long transporterId,
    String transporterName,
    String vehicle,
    double capacityKg,
    String pickupWindow,
    String status,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {
    public static final String OFFERED = "OFFERED";
    public static final String ACCEPTED = "ACCEPTED";
    public static final String PICKED_UP = "PICKED_UP";
    public static final String DELIVERED = "DELIVERED";
    public static final String CANCELLED = "CANCELLED";

    public static String label(String status) {
        return switch (status) {
            case OFFERED -> "Offered";
            case ACCEPTED -> "Accepted";
            case PICKED_UP -> "Picked up";
            case DELIVERED -> "Delivered";
            case CANCELLED -> "Cancelled";
            default -> status;
        };
    }

    public String statusLabel() {
        return label(status);
    }

    public String statusClass() {
        return Format.cssClass(statusLabel());
    }

    public String capacityLabel() {
        return Format.number(capacityKg) + " kg";
    }

    public String updatedLabel() {
        return Format.shortDateTime(updatedAt);
    }

    /** The buttons a coordinator can press next. */
    public List<StatusOption> nextSteps() {
        return switch (status) {
            case OFFERED -> List.of(new StatusOption(ACCEPTED, "Driver accepted", false), new StatusOption(CANCELLED, "Cancel", true));
            case ACCEPTED -> List.of(new StatusOption(PICKED_UP, "Confirm pickup", false), new StatusOption(CANCELLED, "Cancel", true));
            case PICKED_UP -> List.of(new StatusOption(DELIVERED, "Confirm delivery", false));
            default -> List.of();
        };
    }

    public boolean active() {
        return !DELIVERED.equals(status) && !CANCELLED.equals(status);
    }

    public record StatusOption(String value, String label, boolean danger) {
    }
}
