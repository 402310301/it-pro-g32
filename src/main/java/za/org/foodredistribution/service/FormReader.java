package za.org.foodredistribution.service;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import za.org.foodredistribution.model.Format;

/** Reads and checks values from a submitted HTML form, collecting one error message per field. */
public final class FormReader {

    private final Map<String, String> form;
    private final Map<String, String> errors = new LinkedHashMap<>();

    public FormReader(Map<String, String> form) {
        this.form = form;
    }

    public String text(String field) {
        String value = form.get(field);
        return value == null ? "" : value.trim();
    }

    public String required(String field, String label, int maxLength) {
        String value = text(field);
        if (value.isEmpty()) {
            error(field, label + " is required.");
        } else if (value.length() > maxLength) {
            error(field, label + " must be at most " + maxLength + " characters.");
        }
        return value;
    }

    public String oneOf(String field, String label, List<String> allowed) {
        String value = text(field);
        if (!allowed.contains(value)) {
            error(field, "Please select the " + label.toLowerCase() + ".");
        }
        return value;
    }

    public double number(String field, String label, double min, boolean allowEqualToMin) {
        String value = text(field);
        try {
            double number = Double.parseDouble(value);
            if (number < min || (!allowEqualToMin && number == min)) {
                error(field, label + " must be " + (allowEqualToMin ? "at least " : "more than ") + Format.number(min) + ".");
            }
            return number;
        } catch (NumberFormatException e) {
            error(field, label + " must be a number.");
            return 0;
        }
    }

    public int integer(String field, String label) {
        try {
            int number = Integer.parseInt(text(field));
            if (number < 0 || number > 100) {
                error(field, label + " must be between 0 and 100.");
            }
            return number;
        } catch (NumberFormatException e) {
            error(field, label + " must be a whole number.");
            return 0;
        }
    }

    public long id(String field, String label) {
        try {
            return Long.parseLong(text(field));
        } catch (NumberFormatException e) {
            error(field, "Please select the " + label.toLowerCase() + ".");
            return 0;
        }
    }

    public LocalDateTime dateTime(String field, String label) {
        try {
            return Format.parseInputDateTime(text(field));
        } catch (DateTimeParseException e) {
            error(field, label + " needs a valid date and time.");
            return null;
        }
    }

    public String time(String field, String label) {
        String value = text(field);
        try {
            return LocalTime.parse(value).toString();
        } catch (DateTimeParseException e) {
            error(field, label + " needs a time like 08:30.");
            return value;
        }
    }

    public boolean checkbox(String field) {
        String value = text(field);
        return value.equals("on") || value.equals("true") || value.equals("yes");
    }

    public void error(String field, String message) {
        errors.putIfAbsent(field, message);
    }

    public boolean hasError(String field) {
        return errors.containsKey(field);
    }

    /** For small forms without per-field messages: throws with the first problem as the message. */
    public void throwFirstError() {
        if (!errors.isEmpty()) {
            throw new ValidationException(errors.values().iterator().next());
        }
    }

    public void throwIfErrors() {
        if (!errors.isEmpty()) {
            throw new ValidationException(errors);
        }
    }
}
