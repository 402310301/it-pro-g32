package za.org.foodredistribution.model;

import java.time.LocalDateTime;

public record Notification(
    long id,
    Long donationId,
    String title,
    String message,
    String type,
    boolean read,
    LocalDateTime createdAt
) {
    public String createdLabel() {
        return Format.dateTime(createdAt);
    }

    public String createdRelative() {
        return Format.relative(createdAt);
    }

    public String typeClass() {
        return Format.cssClass(type);
    }
}
