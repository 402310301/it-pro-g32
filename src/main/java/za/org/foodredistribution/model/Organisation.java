package za.org.foodredistribution.model;

import java.time.LocalDateTime;

/** A donor (hotel, bakery, ...) or a recipient (food bank, shelter, ...). */
public record Organisation(
    long id,
    String name,
    String role,
    String organisationType,
    String area,
    String address,
    double latitude,
    double longitude,
    String contactName,
    String approvalStatus,
    LocalDateTime createdAt
) {
    public static final String DONOR = "DONOR";
    public static final String RECIPIENT = "RECIPIENT";
    public static final String APPROVED = "APPROVED";
    public static final String PENDING = "PENDING";

    public boolean approved() {
        return APPROVED.equals(approvalStatus);
    }

    public String roleLabel() {
        return DONOR.equals(role) ? "Donor" : "Recipient";
    }

    public String statusLabel() {
        return approved() ? "Approved" : "Pending approval";
    }
}
