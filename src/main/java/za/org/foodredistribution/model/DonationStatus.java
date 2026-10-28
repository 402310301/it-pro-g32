package za.org.foodredistribution.model;

import java.util.List;

/**
 * Donation lifecycle:
 * Listed -> Recipient Recommended (or Matching when nobody fits) -> Accepted / Transport Required
 * -> Transport Assigned -> Collected -> Completed.  A donation can also end as Expired or Cancelled.
 */
public final class DonationStatus {

    public static final String LISTED = "Listed";
    public static final String MATCHING = "Matching";
    public static final String RECIPIENT_RECOMMENDED = "Recipient Recommended";
    public static final String ACCEPTED = "Accepted";
    public static final String TRANSPORT_REQUIRED = "Transport Required";
    public static final String TRANSPORT_ASSIGNED = "Transport Assigned";
    public static final String COLLECTED = "Collected";
    public static final String COMPLETED = "Completed";
    public static final String EXPIRED = "Expired";
    public static final String CANCELLED = "Cancelled";

    public static final List<String> ALL = List.of(
        LISTED, MATCHING, RECIPIENT_RECOMMENDED, ACCEPTED, TRANSPORT_REQUIRED,
        TRANSPORT_ASSIGNED, COLLECTED, COMPLETED, EXPIRED, CANCELLED);

    /** Statuses after which nothing else happens to a donation. */
    public static final List<String> FINISHED = List.of(COMPLETED, EXPIRED, CANCELLED);

    /** Statuses in which recipients can still be (re)matched. */
    public static final List<String> MATCHABLE = List.of(LISTED, MATCHING, RECIPIENT_RECOMMENDED);

    private DonationStatus() {
    }

    public static boolean isFinished(String status) {
        return FINISHED.contains(status);
    }
}
