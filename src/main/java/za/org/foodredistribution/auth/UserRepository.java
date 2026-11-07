package za.org.foodredistribution.auth;

import java.time.LocalDateTime;
import java.util.List;
import za.org.foodredistribution.db.Database;
import za.org.foodredistribution.db.Row;

public class UserRepository {

    private static final String SELECT = """
        SELECT u.*, o.name AS organisation_name
        FROM users u
        LEFT JOIN organisations o ON o.id = u.organisation_id
        """;

    private final Database db;

    public UserRepository(Database db) {
        this.db = db;
    }

    public List<User> findAll() {
        return db.query(SELECT + " ORDER BY u.role, u.full_name").stream().map(this::map).toList();
    }

    public User findById(long id) {
        Row row = db.queryOne(SELECT + " WHERE u.id = ?", id);
        return row == null ? null : map(row);
    }

    public User findByUsername(String username) {
        Row row = db.queryOne(SELECT + " WHERE LOWER(u.username) = LOWER(?)", username);
        return row == null ? null : map(row);
    }

    public String passwordHash(long id) {
        Row row = db.queryOne("SELECT password_hash FROM users WHERE id = ?", id);
        return row == null ? null : row.getString("password_hash");
    }

    public long count() {
        return db.queryOne("SELECT COUNT(*) AS total FROM users").getLong("total");
    }

    public long insert(String username, String fullName, String passwordHash, String role, Long organisationId,
                       LocalDateTime createdAt) {
        return db.insert("""
            INSERT INTO users (username, full_name, password_hash, role, organisation_id, active, created_at)
            VALUES (?, ?, ?, ?, ?, ?, ?)""",
            username, fullName, passwordHash, role, organisationId, true, createdAt);
    }

    public void updatePassword(long id, String passwordHash) {
        db.update("UPDATE users SET password_hash = ? WHERE id = ?", passwordHash, id);
    }

    public void setActive(long id, boolean active) {
        db.update("UPDATE users SET active = ? WHERE id = ?", active, id);
    }

    public void recordLogin(long id, LocalDateTime at) {
        db.update("UPDATE users SET last_login_at = ? WHERE id = ?", at, id);
    }

    private User map(Row r) {
        return new User(r.getLong("id"), r.getString("username"), r.getString("full_name"), r.getString("role"),
            r.getNullableLong("organisation_id"), r.getString("organisation_name"), r.getBoolean("active"),
            r.getDateTime("last_login_at"));
    }
}
