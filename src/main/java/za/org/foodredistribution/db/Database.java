package za.org.foodredistribution.db;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * The single place in the application that talks JDBC.
 * Repositories call {@link #query}, {@link #update} and {@link #insert} with plain SQL
 * and "?" parameters, and get back simple {@link Row} objects.
 */
public class Database {

    private final String url;
    private final String user;
    private final String password;

    /** When a transaction is running, every call on the same thread shares this connection. */
    private final ThreadLocal<Connection> currentTransaction = new ThreadLocal<>();

    public Database(String url, String user, String password) {
        this.url = url;
        this.user = user;
        this.password = password;
    }

    /** Protected constructor for subclasses that replace the storage (used by tests). */
    protected Database() {
        this(null, null, null);
    }

    public void checkConnection() {
        try (Connection connection = open()) {
            if (!connection.isValid(5)) {
                throw new SQLException("Connection is not valid");
            }
        } catch (SQLException e) {
            throw new DatabaseException("Could not connect to MySQL at " + url + " as user '" + user + "'. "
                + "Check that MySQL is running and that db.user / db.password in application.properties are correct. "
                + "Details: " + e.getMessage(), e);
        }
    }

    /** Runs a .sql file from the classpath, statement by statement (statements end with ';'). */
    public void runScript(String classpathResource) {
        String script = readResource(classpathResource);
        StringBuilder withoutComments = new StringBuilder();
        for (String line : script.split("\\R")) {
            if (!line.trim().startsWith("--")) {
                withoutComments.append(line).append('\n');
            }
        }
        for (String statement : withoutComments.toString().split(";")) {
            if (!statement.isBlank()) {
                update(statement.trim());
            }
        }
    }

    public List<Row> query(String sql, Object... params) {
        return withConnection(connection -> {
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                bind(ps, params);
                try (ResultSet rs = ps.executeQuery()) {
                    ResultSetMetaData meta = rs.getMetaData();
                    List<Row> rows = new ArrayList<>();
                    while (rs.next()) {
                        Map<String, Object> values = new LinkedHashMap<>();
                        for (int i = 1; i <= meta.getColumnCount(); i++) {
                            values.put(meta.getColumnLabel(i).toLowerCase(), rs.getObject(i));
                        }
                        rows.add(new Row(values));
                    }
                    return rows;
                }
            }
        });
    }

    /** Returns the first row, or null when the query returns nothing. */
    public Row queryOne(String sql, Object... params) {
        List<Row> rows = query(sql, params);
        return rows.isEmpty() ? null : rows.get(0);
    }

    /** Runs an UPDATE / DELETE / DDL statement and returns the number of affected rows. */
    public int update(String sql, Object... params) {
        return withConnection(connection -> {
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                bind(ps, params);
                return ps.executeUpdate();
            }
        });
    }

    /** Runs an INSERT and returns the AUTO_INCREMENT id of the new row. */
    public long insert(String sql, Object... params) {
        return withConnection(connection -> {
            try (PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                bind(ps, params);
                ps.executeUpdate();
                try (ResultSet keys = ps.getGeneratedKeys()) {
                    if (keys.next()) {
                        return keys.getLong(1);
                    }
                    throw new SQLException("INSERT did not return a generated id");
                }
            }
        });
    }

    /**
     * Runs the work inside one database transaction: either everything is saved, or nothing is.
     * Nested calls simply join the outer transaction.
     */
    public <T> T inTransaction(Supplier<T> work) {
        if (currentTransaction.get() != null) {
            return work.get();
        }
        try (Connection connection = open()) {
            connection.setAutoCommit(false);
            currentTransaction.set(connection);
            try {
                T result = work.get();
                connection.commit();
                return result;
            } catch (RuntimeException e) {
                connection.rollback();
                throw e;
            } finally {
                currentTransaction.remove();
            }
        } catch (SQLException e) {
            throw new DatabaseException("Transaction failed: " + e.getMessage(), e);
        }
    }

    public void inTransaction(Runnable work) {
        inTransaction(() -> {
            work.run();
            return null;
        });
    }

    // ------------------------------------------------------------------ helpers

    protected Connection open() throws SQLException {
        return DriverManager.getConnection(url, user, password);
    }

    private interface SqlWork<T> {
        T run(Connection connection) throws SQLException;
    }

    private <T> T withConnection(SqlWork<T> work) {
        Connection shared = currentTransaction.get();
        try {
            if (shared != null) {
                return work.run(shared);
            }
            try (Connection connection = open()) {
                return work.run(connection);
            }
        } catch (SQLException e) {
            throw new DatabaseException("Database error: " + e.getMessage(), e);
        }
    }

    private static void bind(PreparedStatement ps, Object[] params) throws SQLException {
        for (int i = 0; i < params.length; i++) {
            Object value = params[i];
            if (value instanceof Double d) {
                value = BigDecimal.valueOf(d);
            }
            ps.setObject(i + 1, value);
        }
    }

    protected static String readResource(String classpathResource) {
        try (InputStream in = Database.class.getClassLoader().getResourceAsStream(classpathResource)) {
            if (in == null) {
                throw new IllegalStateException("Missing resource: " + classpathResource);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Could not read " + classpathResource, e);
        }
    }
}
