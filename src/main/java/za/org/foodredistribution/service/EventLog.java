package za.org.foodredistribution.service;

import java.time.LocalDateTime;
import za.org.foodredistribution.repository.AuditRepository;
import za.org.foodredistribution.repository.NotificationRepository;

/** Writes audit-trail entries and notifications. */
public class EventLog {

    private final AuditRepository audit;
    private final NotificationRepository notifications;

    public EventLog(AuditRepository audit, NotificationRepository notifications) {
        this.audit = audit;
        this.notifications = notifications;
    }

    public void audit(String actor, String action, String recordType, long recordId, String from, String to) {
        audit.insert(actor, action, recordType, recordId, from, to, LocalDateTime.now());
    }

    public void notify(Long donationId, String type, String title, String message) {
        notifications.insert(donationId, title, message, type, false, LocalDateTime.now());
    }
}
