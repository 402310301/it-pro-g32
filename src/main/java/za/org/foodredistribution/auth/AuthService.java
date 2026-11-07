package za.org.foodredistribution.auth;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import za.org.foodredistribution.model.Organisation;
import za.org.foodredistribution.repository.AuditRepository;
import za.org.foodredistribution.repository.OrganisationRepository;
import za.org.foodredistribution.service.FormReader;
import za.org.foodredistribution.service.NotFoundException;
import za.org.foodredistribution.service.ValidationException;

/**
 * Signing in and out, sessions, and user accounts.
 * Sessions are kept in memory, so everyone signs in again after the app restarts.
 */
public class AuthService {

    public static final String COOKIE = "FRSESSION";
    private static final long SESSION_HOURS = 8;
    private static final SecureRandom RANDOM = new SecureRandom();

    private record Session(long userId, LocalDateTime expiresAt) {
    }

    private final Map<String, Session> sessions = new ConcurrentHashMap<>();
    private final UserRepository users;
    private final OrganisationRepository organisations;
    private final AuditRepository audit;

    public AuthService(UserRepository users, OrganisationRepository organisations, AuditRepository audit) {
        this.users = users;
        this.organisations = organisations;
        this.audit = audit;
    }

    public UserRepository users() {
        return users;
    }

    /** Checks the username and password. Returns a new session token, or null when they are wrong. */
    public String login(String username, String password) {
        User user = username == null || username.isBlank() ? null : users.findByUsername(username.trim());
        if (user == null || !user.active() || !PasswordHasher.matches(password, users.passwordHash(user.id()))) {
            return null;
        }
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        LocalDateTime now = LocalDateTime.now();
        sessions.put(token, new Session(user.id(), now.plusHours(SESSION_HOURS)));
        users.recordLogin(user.id(), now);
        audit.insert(user.fullName(), "Signed in", "User", user.id(), null, null, now);
        return token;
    }

    public void logout(String token) {
        if (token != null) {
            sessions.remove(token);
        }
    }

    /** The user for a session cookie, or null when the session is missing, expired or the account is disabled. */
    public User userFor(String token) {
        if (token == null) {
            return null;
        }
        Session session = sessions.get(token);
        LocalDateTime now = LocalDateTime.now();
        if (session == null || session.expiresAt().isBefore(now)) {
            sessions.remove(token);
            return null;
        }
        User user = users.findById(session.userId());
        if (user == null || !user.active()) {
            sessions.remove(token);
            return null;
        }
        sessions.put(token, new Session(session.userId(), now.plusHours(SESSION_HOURS))); // sliding expiry
        return user;
    }

    /** Makes sure somebody can always sign in: creates admin / admin123 when there are no users at all. */
    public boolean ensureAdminExists() {
        if (users.count() > 0) {
            return false;
        }
        users.insert("admin", "Site Administrator", PasswordHasher.hash("admin123"), Role.ADMIN, null, LocalDateTime.now());
        return true;
    }

    // ------------------------------------------------------------------ account management

    public long createUser(Map<String, String> form) {
        FormReader f = new FormReader(form);
        String username = f.required("username", "Username", 60).toLowerCase();
        String fullName = f.required("fullName", "Full name", 120);
        String role = f.oneOf("role", "Role", Role.ALL);
        String password = f.text("password");
        if (!f.hasError("username") && !username.matches("[a-z0-9._-]{3,60}")) {
            f.error("username", "Use 3-60 letters, numbers, dots, dashes or underscores.");
        }
        if (!f.hasError("username") && users.findByUsername(username) != null) {
            f.error("username", "This username is already taken.");
        }
        checkPassword(f, "password", password);
        Long organisationId = null;
        if (Role.DONOR.equals(role) || Role.RECIPIENT.equals(role)) {
            long id = f.id("organisationId", "Organisation");
            Organisation organisation = id > 0 ? organisations.findById(id) : null;
            String needed = Role.DONOR.equals(role) ? Organisation.DONOR : Organisation.RECIPIENT;
            if (organisation == null || !needed.equals(organisation.role())) {
                f.error("organisationId", "Choose a " + Role.label(role).toLowerCase() + " organisation for this user.");
            } else {
                organisationId = id;
            }
        }
        f.throwIfErrors();
        long id = users.insert(username, fullName, PasswordHasher.hash(password), role, organisationId, LocalDateTime.now());
        audit.insert(Actor.nameOr("Administrator"), "User created", "User", id, null, Role.label(role), LocalDateTime.now());
        return id;
    }

    public void changePassword(User user, Map<String, String> form) {
        FormReader f = new FormReader(form);
        String current = f.text("currentPassword");
        String next = f.text("newPassword");
        if (!PasswordHasher.matches(current, users.passwordHash(user.id()))) {
            f.error("currentPassword", "Your current password is not correct.");
        }
        checkPassword(f, "newPassword", next);
        if (!f.hasError("newPassword") && !next.equals(f.text("confirmPassword"))) {
            f.error("confirmPassword", "The two new passwords do not match.");
        }
        f.throwIfErrors();
        users.updatePassword(user.id(), PasswordHasher.hash(next));
        audit.insert(user.fullName(), "Password changed", "User", user.id(), null, null, LocalDateTime.now());
    }

    public void setActive(User admin, long userId, boolean active) {
        User target = users.findById(userId);
        if (target == null) {
            throw new NotFoundException("User #" + userId + " was not found.");
        }
        if (target.id() == admin.id() && !active) {
            throw new ValidationException("You cannot disable your own account.");
        }
        users.setActive(userId, active);
        if (!active) {
            sessions.values().removeIf(s -> s.userId() == userId);
        }
        audit.insert(admin.fullName(), active ? "User enabled" : "User disabled", "User", userId, null, null, LocalDateTime.now());
    }

    public List<User> allUsers() {
        return users.findAll();
    }

    private static void checkPassword(FormReader f, String field, String password) {
        if (password.length() < 6) {
            f.error(field, "Passwords need at least 6 characters.");
        }
    }
}
