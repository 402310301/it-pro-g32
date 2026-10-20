package za.org.foodredistribution.repository;

import java.time.LocalDateTime;
import java.util.List;
import za.org.foodredistribution.db.Database;
import za.org.foodredistribution.db.Row;
import za.org.foodredistribution.model.Organisation;
import za.org.foodredistribution.model.RecipientNeed;

public class NeedRepository {

    private static final String SELECT = """
        SELECT n.*, o.name AS organisation_name, o.organisation_type, o.area, o.latitude, o.longitude,
               o.approval_status
        FROM recipient_needs n
        JOIN organisations o ON o.id = n.recipient_organisation_id
        """;

    private final Database db;

    public NeedRepository(Database db) {
        this.db = db;
    }

    public List<RecipientNeed> findAll() {
        return db.query(SELECT + " ORDER BY n.active DESC, n.id").stream().map(this::map).toList();
    }

    /** Active needs from approved organisations: the candidates the matching engine looks at. */
    public List<RecipientNeed> findMatchable(String category) {
        return db.query(SELECT + " WHERE n.active = ? AND o.approval_status = ? AND n.category = ? ORDER BY n.id",
            true, Organisation.APPROVED, category).stream().map(this::map).toList();
    }

    public RecipientNeed findById(long id) {
        Row row = db.queryOne(SELECT + " WHERE n.id = ?", id);
        return row == null ? null : map(row);
    }

    public long insert(long organisationId, String category, String description, double quantityNeeded,
                       double maxPerDonation, double storageCapacity, String storageType, String opensAt,
                       String closesAt, String urgency, LocalDateTime createdAt) {
        return db.insert("""
            INSERT INTO recipient_needs
              (recipient_organisation_id, category, description, quantity_needed, max_per_donation,
               storage_capacity, storage_type, opens_at, closes_at, urgency, active, created_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""",
            organisationId, category, description, quantityNeeded, maxPerDonation, storageCapacity, storageType,
            opensAt, closesAt, urgency, true, createdAt);
    }

    public void updateQuantityNeeded(long id, double quantityNeeded, boolean active) {
        db.update("UPDATE recipient_needs SET quantity_needed = ?, active = ? WHERE id = ?", quantityNeeded, active, id);
    }

    public void setActive(long id, boolean active) {
        db.update("UPDATE recipient_needs SET active = ? WHERE id = ?", active, id);
    }

    private RecipientNeed map(Row r) {
        return new RecipientNeed(
            r.getLong("id"), r.getLong("recipient_organisation_id"), r.getString("organisation_name"),
            r.getString("organisation_type"), r.getString("area"), r.getDouble("latitude"), r.getDouble("longitude"),
            Organisation.APPROVED.equals(r.getString("approval_status")), r.getString("category"),
            r.getString("description"), r.getDouble("quantity_needed"), r.getDouble("max_per_donation"),
            r.getDouble("storage_capacity"), r.getString("storage_type"), r.getString("opens_at"),
            r.getString("closes_at"), r.getString("urgency"), r.getBoolean("active"), r.getDateTime("created_at"));
    }
}
