package za.org.foodredistribution.model;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** Small helpers for showing numbers and dates on the web pages. */
public final class Format {

    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm", Locale.ENGLISH);
    private static final DateTimeFormatter SHORT = DateTimeFormatter.ofPattern("d MMM, HH:mm", Locale.ENGLISH);
    private static final DateTimeFormatter INPUT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm");

    private Format() {
    }

    public static String dateTime(LocalDateTime value) {
        return value == null ? "" : DATE_TIME.format(value);
    }

    public static String shortDateTime(LocalDateTime value) {
        return value == null ? "" : SHORT.format(value);
    }

    /** Value for an HTML input of type datetime-local. */
    public static String inputDateTime(LocalDateTime value) {
        return value == null ? "" : INPUT.format(value);
    }

    public static LocalDateTime parseInputDateTime(String value) {
        return LocalDateTime.parse(value.trim(), INPUT);
    }

    /** 12.0 becomes "12", 12.5 stays "12.5". */
    public static String number(double value) {
        if (Math.abs(value - Math.rint(value)) < 0.005) {
            return String.valueOf((long) Math.rint(value));
        }
        return String.format(Locale.ENGLISH, "%.1f", value);
    }

    /** "in 5 h", "in 2 days", "3 h ago" relative to now. */
    public static String relative(LocalDateTime value) {
        if (value == null) {
            return "";
        }
        Duration duration = Duration.between(LocalDateTime.now(), value);
        boolean future = !duration.isNegative();
        long minutes = Math.abs(duration.toMinutes());
        String amount;
        if (minutes < 60) {
            amount = minutes + " min";
        } else if (minutes < 48 * 60) {
            amount = (minutes / 60) + " h";
        } else {
            amount = (minutes / (60 * 24)) + " days";
        }
        return future ? "in " + amount : amount + " ago";
    }

    /** Turns "Transport Assigned" into "transport-assigned" for use as a CSS class. */
    public static String cssClass(String value) {
        return value == null ? "" : value.toLowerCase().replaceAll("[^a-z0-9]+", "-");
    }
}
