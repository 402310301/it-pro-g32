package za.org.foodredistribution.repository;

import java.time.LocalDateTime;
import java.util.List;
import za.org.foodredistribution.db.Database;
import za.org.foodredistribution.db.Row;
import za.org.foodredistribution.model.Organisation;

public class OrganisationRepository {

    private final Database db;

    public OrganisationRepository(Database db) {
        this.db = db;
    }

    public List<Organisation> findAll() {
        return db.query("SELECT * FROM organisations ORDER BY role, name").stream().map(this::map).toList();
    }

    public List<Organisation> findApprovedByRole(String role) {
        return db.query("SELECT * FROM organisations WHERE role = ? AND approval_status = ? ORDER BY name",
            role, Organisation.APPROVED).stream().map(this::map).toList();
    }

    public Organisation findById(long id) {
        Row row = db.queryOne("SELECT * FROM organisations WHERE id = ?", id);
        return row == null ? null : map(row);
    }

    public boolean existsByName(String name) {
        return db.queryOne("SELECT id FROM organisations WHERE LOWER(name) = LOWER(?)", name) != null;
    }

    public long count() {
        return db.queryOne("SELECT COUNT(*) AS total FROM organisations").getLong("total");
    }

    public long insert(String name, String role, String type, String area, String address,
                       double latitude, double longitude, String contactName, String approvalStatus,
                       LocalDateTime createdAt) {
        return db.insert("""
            INSERT INTO organisations
              (name, role, organisation_type, area, address, latitude, longitude, contact_name, approval_status, created_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""",
            name, role, type, area, address, latitude, longitude, contactName, approvalStatus, createdAt);
    }

    public void approve(long id) {
        db.update("UPDATE organisations SET approval_status = ? WHERE id = ?", Organisation.APPROVED, id);
    }

    private Organisation map(Row r) {
        return new Organisation(
            r.getLong("id"), r.getString("name"), r.getString("role"), r.getString("organisation_type"),
            r.getString("area"), r.getString("address"), r.getDouble("latitude"), r.getDouble("longitude"),
            r.getString("contact_name"), r.getString("approval_status"), r.getDateTime("created_at"));
    }
}
