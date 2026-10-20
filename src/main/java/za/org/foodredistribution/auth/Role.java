package za.org.foodredistribution.auth;

import java.util.List;

/** What a signed-in person is allowed to do. */
public final class Role {

    /** Everything, including users, organisations, drivers and matching weights. */
    public static final String ADMIN = "ADMIN";
    /** Runs day-to-day operations: matching, transport and reports. */
    public static final String COORDINATOR = "COORDINATOR";
    /** Works for a donor organisation and lists surplus food. */
    public static final String DONOR = "DONOR";
    /** Works for a recipient organisation, registers needs and accepts food. */
    public static final String RECIPIENT = "RECIPIENT";

    public static final List<String> ALL = List.of(ADMIN, COORDINATOR, DONOR, RECIPIENT);

    private Role() {
    }

    public static String label(String role) {
        return switch (role) {
            case ADMIN -> "Administrator";
            case COORDINATOR -> "Coordinator";
            case DONOR -> "Donor";
            case RECIPIENT -> "Recipient";
            default -> role;
        };
    }
}
