package za.org.foodredistribution.repository;

import java.time.LocalDateTime;
import java.util.List;
import za.org.foodredistribution.db.Database;
import za.org.foodredistribution.db.Row;
import za.org.foodredistribution.model.Format;
import za.org.foodredistribution.model.TransportAssignment;

public class AssignmentRepository {

    private static final String SELECT = """
        SELECT a.*, d.reference, d.address AS pickup_address, d.area AS pickup_area, d.quantity, d.unit,
               d.storage_requirement, donor.name AS donor_name, recipient.name AS recipient_name,
               recipient.area AS delivery_area, t.name AS transporter_name, t.vehicle, t.capacity_kg
        FROM transport_assignments a
        JOIN food_donations d ON d.id = a.donation_id
        JOIN organisations donor ON donor.id = d.donor_organisation_id
        LEFT JOIN organisations recipient ON recipient.id = d.recipient_organisation_id
        JOIN transporters t ON t.id = a.transporter_id
        """;

    private final Database db;

    public AssignmentRepository(Database db) {
        this.db = db;
    }

    /** Active assignments first, then finished ones; most recently updated first. */
    public List<TransportAssignment> findAll() {
        return db.query(SELECT + """
             ORDER BY CASE WHEN a.status IN ('DELIVERED', 'CANCELLED') THEN 1 ELSE 0 END, a.updated_at DESC, a.id DESC
            """).stream().map(this::map).toList();
    }

    public TransportAssignment findById(long id) {
        Row row = db.queryOne(SELECT + " WHERE a.id = ?", id);
        return row == null ? null : map(row);
    }

    public List<TransportAssignment> findByDonation(long donationId) {
        return db.query(SELECT + " WHERE a.donation_id = ? ORDER BY a.id DESC", donationId)
            .stream().map(this::map).toList();
    }

    public long insert(long donationId, long transporterId, String pickupWindow, String status, LocalDateTime now) {
        return db.insert("""
            INSERT INTO transport_assignments (donation_id, transporter_id, pickup_window, status, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, ?)""",
            donationId, transporterId, pickupWindow, status, now, now);
    }

    public void updateStatus(long id, String status, LocalDateTime now) {
        db.update("UPDATE transport_assignments SET status = ?, updated_at = ? WHERE id = ?", status, now, id);
    }

    private TransportAssignment map(Row r) {
        return new TransportAssignment(
            r.getLong("id"), r.getLong("donation_id"), r.getString("reference"), r.getString("donor_name"),
            r.getString("pickup_address") + ", " + r.getString("pickup_area"), r.getString("recipient_name"),
            r.getString("delivery_area"), Format.number(r.getDouble("quantity")) + " " + r.getString("unit"),
            r.getString("storage_requirement"), r.getLong("transporter_id"), r.getString("transporter_name"),
            r.getString("vehicle"), r.getDouble("capacity_kg"), r.getString("pickup_window"), r.getString("status"),
            r.getDateTime("created_at"), r.getDateTime("updated_at"));
    }
}
