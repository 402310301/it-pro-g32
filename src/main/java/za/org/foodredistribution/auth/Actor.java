package za.org.foodredistribution.auth;

/**
 * Remembers who is signed in for the request being handled, so the audit trail can record
 * the real person's name instead of a generic "Coordinator".
 */
public final class Actor {

    private static final ThreadLocal<User> CURRENT = new ThreadLocal<>();

    private Actor() {
    }

    public static void set(User user) {
        CURRENT.set(user);
    }

    public static void clear() {
        CURRENT.remove();
    }

    public static User current() {
        return CURRENT.get();
    }

    /** The signed-in person's name, or the fallback for background jobs and start-up. */
    public static String nameOr(String fallback) {
        User user = CURRENT.get();
        return user == null ? fallback : user.fullName();
    }
}
