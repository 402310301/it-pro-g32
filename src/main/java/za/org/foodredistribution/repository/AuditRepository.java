package za.org.foodredistribution.repository;

import java.time.LocalDateTime;
import java.util.List;
import za.org.foodredistribution.db.Database;
import za.org.foodredistribution.db.Row;
import za.org.foodredistribution.model.AuditEntry;

public class AuditRepository {

    private final Database db;

    public AuditRepository(Database db) {
        this.db = db;
    }

    public void insert(String actor, String action, String recordType, long recordId, String previousStatus,
                       String newStatus, LocalDateTime createdAt) {
        db.insert("""
            INSERT INTO audit_logs (actor, action, record_type, record_id, previous_status, new_status, created_at)
            VALUES (?, ?, ?, ?, ?, ?, ?)""",
            actor, action, recordType, recordId, previousStatus, newStatus, createdAt);
    }

    public List<AuditEntry> findRecent(int limit) {
        return db.query("SELECT * FROM audit_logs ORDER BY created_at DESC, id DESC LIMIT ?", limit)
            .stream().map(this::map).toList();
    }

    /** History of one record, oldest first. */
    public List<AuditEntry> findForRecord(String recordType, long recordId) {
        return db.query("SELECT * FROM audit_logs WHERE record_type = ? AND record_id = ? ORDER BY created_at, id",
            recordType, recordId).stream().map(this::map).toList();
    }

    public List<AuditEntry> findByAction(String action) {
        return db.query("SELECT * FROM audit_logs WHERE action = ? ORDER BY created_at", action)
            .stream().map(this::map).toList();
    }

    private AuditEntry map(Row r) {
        return new AuditEntry(r.getLong("id"), r.getString("actor"), r.getString("action"), r.getString("record_type"),
            r.getLong("record_id"), r.getString("previous_status"), r.getString("new_status"),
            r.getDateTime("created_at"));
    }
}
