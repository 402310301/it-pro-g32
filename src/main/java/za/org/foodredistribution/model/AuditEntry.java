package za.org.foodredistribution.model;

import java.time.LocalDateTime;

/** One line in the audit trail: who changed what, and when. */
public record AuditEntry(
    long id,
    String actor,
    String action,
    String recordType,
    long recordId,
    String previousStatus,
    String newStatus,
    LocalDateTime createdAt
) {
    public String createdLabel() {
        return Format.dateTime(createdAt);
    }

    public String createdRelative() {
        return Format.relative(createdAt);
    }

    public boolean hasChange() {
        return newStatus != null;
    }

    public String previousLabel() {
        return previousStatus == null ? "(new)" : previousStatus;
    }
}
