package za.org.foodredistribution.auth;

import java.time.LocalDateTime;
import za.org.foodredistribution.model.Format;

/** A person who can sign in. The methods below are used by the templates to show or hide buttons. */
public record User(
    long id,
    String username,
    String fullName,
    String role,
    Long organisationId,
    String organisationName,
    boolean active,
    LocalDateTime lastLoginAt
) {
    public boolean isAdmin() {
        return Role.ADMIN.equals(role);
    }

    public boolean isDonor() {
        return Role.DONOR.equals(role);
    }

    public boolean isRecipient() {
        return Role.RECIPIENT.equals(role);
    }

    /** Admins and coordinators run the network. */
    public boolean canManage() {
        return isAdmin() || Role.COORDINATOR.equals(role);
    }

    public boolean canOfferFood() {
        return canManage() || isDonor();
    }

    public boolean canManageNeeds() {
        return canManage() || isRecipient();
    }

    public boolean hasRole(String... roles) {
        for (String r : roles) {
            if (r.equals(role)) {
                return true;
            }
        }
        return false;
    }

    public String roleLabel() {
        return Role.label(role);
    }

    public String roleClass() {
        return Format.cssClass(role);
    }

    /** Shown under the name in the sidebar. */
    public String subtitle() {
        return organisationName != null ? organisationName : roleLabel();
    }

    public String initials() {
        String[] parts = fullName.trim().split("\\s+");
        String first = parts[0].isEmpty() ? "?" : parts[0].substring(0, 1);
        String last = parts.length > 1 ? parts[parts.length - 1].substring(0, 1) : "";
        return (first + last).toUpperCase();
    }

    public String lastLoginLabel() {
        return lastLoginAt == null ? "Never" : Format.dateTime(lastLoginAt);
    }
}
