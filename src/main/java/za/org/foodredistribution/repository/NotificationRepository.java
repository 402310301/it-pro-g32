package za.org.foodredistribution.repository;

import java.time.LocalDateTime;
import java.util.List;
import za.org.foodredistribution.db.Database;
import za.org.foodredistribution.db.Row;
import za.org.foodredistribution.model.Notification;

public class NotificationRepository {

    private final Database db;

    public NotificationRepository(Database db) {
        this.db = db;
    }

    public List<Notification> findAll() {
        return db.query("SELECT * FROM notifications ORDER BY created_at DESC, id DESC").stream().map(this::map).toList();
    }

    public long countUnread() {
        return db.queryOne("SELECT COUNT(*) AS total FROM notifications WHERE is_read = ?", false).getLong("total");
    }

    public long insert(Long donationId, String title, String message, String type, boolean read, LocalDateTime createdAt) {
        return db.insert("""
            INSERT INTO notifications (donation_id, title, message, notification_type, is_read, created_at)
            VALUES (?, ?, ?, ?, ?, ?)""",
            donationId, title, message, type, read, createdAt);
    }

    public void markRead(long id) {
        db.update("UPDATE notifications SET is_read = ? WHERE id = ?", true, id);
    }

    public void markAllRead() {
        db.update("UPDATE notifications SET is_read = ? WHERE is_read = ?", true, false);
    }

    private Notification map(Row r) {
        return new Notification(r.getLong("id"), r.getNullableLong("donation_id"), r.getString("title"),
            r.getString("message"), r.getString("notification_type"), r.getBoolean("is_read"),
            r.getDateTime("created_at"));
    }
}
