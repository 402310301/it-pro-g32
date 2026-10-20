package za.org.foodredistribution.service;

import java.util.Map;

/** Thrown when a submitted form has mistakes. Holds one message per form field. */
public class ValidationException extends RuntimeException {

    private final Map<String, String> errors;

    public ValidationException(Map<String, String> errors) {
        super("Please correct the highlighted fields.");
        this.errors = errors;
    }

    public ValidationException(String message) {
        super(message);
        this.errors = Map.of();
    }

    public Map<String, String> errors() {
        return errors;
    }
}
