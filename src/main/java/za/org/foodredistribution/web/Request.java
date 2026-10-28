package za.org.foodredistribution.web;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import za.org.foodredistribution.auth.ForbiddenException;
import za.org.foodredistribution.auth.User;
import za.org.foodredistribution.service.NotFoundException;

/** An incoming HTTP request: path, query-string values, form values and {placeholders} from the route. */
public final class Request {

    private final String method;
    private final String path;
    private final Map<String, String> query;
    private final Map<String, String> form;
    private final Map<String, String> pathParams;
    private final String referer;
    private final Map<String, String> cookies;
    private User user;

    public Request(String method, String path, String rawQuery, String body, Map<String, String> pathParams,
                   String referer, String cookieHeader) {
        this.method = method;
        this.path = path;
        this.query = parse(rawQuery);
        this.form = parse(body);
        this.pathParams = pathParams;
        this.referer = referer;
        this.cookies = parseCookies(cookieHeader);
    }

    public String cookie(String name) {
        return cookies.get(name);
    }

    /** The signed-in user (never null on protected pages). */
    public User user() {
        return user;
    }

    void setUser(User user) {
        this.user = user;
    }

    /** Stops the request with "403 Forbidden" unless the user has one of these roles. */
    public User require(String... roles) {
        if (user == null || !user.hasRole(roles)) {
            throw new ForbiddenException("Your account (" + (user == null ? "guest" : user.roleLabel())
                + ") does not have access to this page.");
        }
        return user;
    }

    private static Map<String, String> parseCookies(String header) {
        Map<String, String> values = new LinkedHashMap<>();
        if (header == null) {
            return values;
        }
        for (String part : header.split(";")) {
            int eq = part.indexOf('=');
            if (eq > 0) {
                values.put(part.substring(0, eq).trim(), part.substring(eq + 1).trim());
            }
        }
        return values;
    }

    public String method() {
        return method;
    }

    public String path() {
        return path;
    }

    public String referer() {
        return referer;
    }

    /** A value from the query string (?status=Listed), or "" when absent. */
    public String query(String name) {
        return query.getOrDefault(name, "");
    }

    /** All submitted form fields. */
    public Map<String, String> form() {
        return form;
    }

    public String field(String name) {
        return form.getOrDefault(name, "");
    }

    /** A numeric {id} from the route, e.g. /donations/{id}. */
    public long id(String name) {
        try {
            return Long.parseLong(pathParams.get(name));
        } catch (NumberFormatException e) {
            throw new NotFoundException("Page not found.");
        }
    }

    static Map<String, String> parse(String encoded) {
        Map<String, String> values = new LinkedHashMap<>();
        if (encoded == null || encoded.isEmpty()) {
            return values;
        }
        for (String pair : encoded.split("&")) {
            if (pair.isEmpty()) {
                continue;
            }
            int eq = pair.indexOf('=');
            String key = URLDecoder.decode(eq < 0 ? pair : pair.substring(0, eq), StandardCharsets.UTF_8);
            String value = eq < 0 ? "" : URLDecoder.decode(pair.substring(eq + 1), StandardCharsets.UTF_8);
            values.put(key, value);
        }
        return values;
    }
}
