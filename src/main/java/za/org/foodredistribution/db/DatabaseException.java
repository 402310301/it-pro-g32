package za.org.foodredistribution.db;

/** Unchecked wrapper around SQLException so the rest of the code stays tidy. */
public class DatabaseException extends RuntimeException {
    public DatabaseException(String message, Throwable cause) {
        super(message, cause);
    }
}
