package za.org.foodredistribution.db;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;

/** One row returned by a query, with small typed getters. Column names are lower case. */
public final class Row {

    private static final DateTimeFormatter SQL_DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final Map<String, Object> values;

    public Row(Map<String, Object> values) {
        this.values = values;
    }

    public Object get(String column) {
        String key = column.toLowerCase();
        if (!values.containsKey(key)) {
            throw new IllegalArgumentException("Column '" + column + "' is not in the result. Columns: " + values.keySet());
        }
        return values.get(key);
    }

    public String getString(String column) {
        Object value = get(column);
        return value == null ? null : value.toString();
    }

    public long getLong(String column) {
        Object value = get(column);
        if (value == null) {
            return 0;
        }
        return value instanceof Number n ? n.longValue() : Long.parseLong(value.toString());
    }

    public Long getNullableLong(String column) {
        return get(column) == null ? null : getLong(column);
    }

    public int getInt(String column) {
        return (int) getLong(column);
    }

    public double getDouble(String column) {
        Object value = get(column);
        if (value == null) {
            return 0;
        }
        return value instanceof Number n ? n.doubleValue() : Double.parseDouble(value.toString());
    }

    public boolean getBoolean(String column) {
        Object value = get(column);
        if (value == null) {
            return false;
        }
        if (value instanceof Boolean b) {
            return b;
        }
        if (value instanceof Number n) {
            return n.intValue() != 0;
        }
        String text = value.toString();
        return text.equals("1") || text.equalsIgnoreCase("true");
    }

    public LocalDateTime getDateTime(String column) {
        Object value = get(column);
        if (value == null) {
            return null;
        }
        if (value instanceof LocalDateTime dateTime) {
            return dateTime;
        }
        if (value instanceof Timestamp timestamp) {
            return timestamp.toLocalDateTime();
        }
        String text = value.toString().replace('T', ' ');
        if (text.length() > 19) {
            text = text.substring(0, 19);
        }
        if (text.length() == 16) {
            text = text + ":00";
        }
        return LocalDateTime.parse(text, SQL_DATE_TIME);
    }
}
