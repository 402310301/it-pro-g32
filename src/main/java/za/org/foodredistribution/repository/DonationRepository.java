package za.org.foodredistribution.repository;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import za.org.foodredistribution.db.Database;
import za.org.foodredistribution.db.Row;
import za.org.foodredistribution.model.Donation;
import za.org.foodredistribution.model.DonationStatus;

public class DonationRepository {

    private static final String SELECT = """
        SELECT d.*, donor.name AS donor_name, recipient.name AS recipient_name
        FROM food_donations d
        JOIN organisations donor ON donor.id = d.donor_organisation_id
        LEFT JOIN organisations recipient ON recipient.id = d.recipient_organisation_id
        """;

    private final Database db;

    public DonationRepository(Database db) {
        this.db = db;
    }

    /** All donations, newest first. Empty or null filters are ignored. */
    public List<Donation> findAll(String status, String category) {
        StringBuilder sql = new StringBuilder(SELECT).append(" WHERE 1 = 1");
        List<Object> params = new ArrayList<>();
        if (status != null && !status.isBlank()) {
            sql.append(" AND d.status = ?");
            params.add(status);
        }
        if (category != null && !category.isBlank()) {
            sql.append(" AND d.category = ?");
            params.add(category);
        }
        sql.append(" ORDER BY d.created_at DESC, d.id DESC");
        return db.query(sql.toString(), params.toArray()).stream().map(this::map).toList();
    }

    public List<Donation> findAll() {
        return findAll(null, null);
    }

    public Donation findById(long id) {
        Row row = db.queryOne(SELECT + " WHERE d.id = ?", id);
        return row == null ? null : map(row);
    }

    /** Donations whose expiry time has passed but which are still waiting to be collected. */
    public List<Donation> findOverdue(LocalDateTime now) {
        return db.query(SELECT + " WHERE d.expiry_at < ? AND d.status NOT IN (?, ?, ?, ?)",
                now, DonationStatus.COMPLETED, DonationStatus.EXPIRED, DonationStatus.CANCELLED, DonationStatus.COLLECTED)
            .stream().map(this::map).toList();
    }

    public long countByReferencePrefix(String prefix) {
        return db.queryOne("SELECT COUNT(*) AS total FROM food_donations WHERE reference LIKE ?", prefix + "%")
            .getLong("total");
    }

    public long insert(String reference, long donorId, String category, String description, double quantity,
                       String unit, String storage, LocalDateTime expiryAt, LocalDateTime collectionDeadline,
                       String area, String address, double latitude, double longitude, boolean transportRequired,
                       String status, LocalDateTime createdAt) {
        return db.insert("""
            INSERT INTO food_donations
              (reference, donor_organisation_id, category, description, quantity, unit, storage_requirement,
               expiry_at, collection_deadline, area, address, latitude, longitude, transport_required,
               status, quantity_allocated, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""",
            reference, donorId, category, description, quantity, unit, storage, expiryAt, collectionDeadline,
            area, address, latitude, longitude, transportRequired, status, 0.0, createdAt, createdAt);
    }

    public void updateStatus(long id, String status, LocalDateTime now) {
        db.update("UPDATE food_donations SET status = ?, updated_at = ? WHERE id = ?", status, now, id);
    }

    public void assignRecipient(long id, Long recipientOrganisationId, double quantityAllocated, String status,
                                LocalDateTime now) {
        db.update("""
            UPDATE food_donations
            SET recipient_organisation_id = ?, quantity_allocated = ?, status = ?, updated_at = ?
            WHERE id = ?""",
            recipientOrganisationId, quantityAllocated, status, now, id);
    }

    private Donation map(Row r) {
        return new Donation(
            r.getLong("id"), r.getString("reference"), r.getLong("donor_organisation_id"), r.getString("donor_name"),
            r.getString("category"), r.getString("description"), r.getDouble("quantity"), r.getString("unit"),
            r.getString("storage_requirement"), r.getDateTime("expiry_at"), r.getDateTime("collection_deadline"),
            r.getString("area"), r.getString("address"), r.getDouble("latitude"), r.getDouble("longitude"),
            r.getBoolean("transport_required"), r.getString("status"), r.getNullableLong("recipient_organisation_id"),
            r.getString("recipient_name"), r.getDouble("quantity_allocated"), r.getDateTime("created_at"),
            r.getDateTime("updated_at"));
    }
}
