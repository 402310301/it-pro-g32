package za.org.foodredistribution.repository;

import java.time.LocalDateTime;
import java.util.List;
import za.org.foodredistribution.db.Database;
import za.org.foodredistribution.db.Row;
import za.org.foodredistribution.model.Transporter;

public class TransporterRepository {

    private final Database db;

    public TransporterRepository(Database db) {
        this.db = db;
    }

    public List<Transporter> findAll() {
        return db.query("SELECT * FROM transporters ORDER BY name").stream().map(this::map).toList();
    }

    public List<Transporter> findActive() {
        return db.query("SELECT * FROM transporters WHERE active = ? ORDER BY name", true)
            .stream().map(this::map).toList();
    }

    public Transporter findById(long id) {
        Row row = db.queryOne("SELECT * FROM transporters WHERE id = ?", id);
        return row == null ? null : map(row);
    }

    public long insert(String name, String phone, String vehicle, double capacityKg, boolean refrigerated,
                       LocalDateTime createdAt) {
        return db.insert("""
            INSERT INTO transporters (name, phone, vehicle, capacity_kg, refrigerated, active, created_at)
            VALUES (?, ?, ?, ?, ?, ?, ?)""",
            name, phone, vehicle, capacityKg, refrigerated, true, createdAt);
    }

    public void setActive(long id, boolean active) {
        db.update("UPDATE transporters SET active = ? WHERE id = ?", active, id);
    }

    private Transporter map(Row r) {
        return new Transporter(r.getLong("id"), r.getString("name"), r.getString("phone"), r.getString("vehicle"),
            r.getDouble("capacity_kg"), r.getBoolean("refrigerated"), r.getBoolean("active"));
    }
}
