package za.org.foodredistribution.auth;

/** The signed-in user is not allowed to do this. */
public class ForbiddenException extends RuntimeException {
    public ForbiddenException(String message) {
        super(message);
    }
}
