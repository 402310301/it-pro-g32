package za.org.foodredistribution.web;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import za.org.foodredistribution.auth.Actor;
import za.org.foodredistribution.auth.AuthService;
import za.org.foodredistribution.auth.ForbiddenException;
import za.org.foodredistribution.auth.User;
import za.org.foodredistribution.service.NotFoundException;
import za.org.foodredistribution.service.ValidationException;

/**
 * Sends each request to the right handler, e.g. GET /donations/{id}.
 * Also serves the CSS and images under /static and turns errors into friendly pages.
 */
public final class Router implements HttpHandler {

    @FunctionalInterface
    public interface Handler {
        Response handle(Request request) throws Exception;
    }

    private record Route(String method, Pattern pattern, List<String> names, Handler handler, boolean open) {
    }

    private final List<Route> routes = new ArrayList<>();
    private final View view;
    private final AuthService auth;

    public Router(View view, AuthService auth) {
        this.view = view;
        this.auth = auth;
    }

    /** A page that needs a signed-in user. */
    public void get(String path, Handler handler) {
        add("GET", path, handler, false);
    }

    public void post(String path, Handler handler) {
        add("POST", path, handler, false);
    }

    /** A page anyone may open without signing in (the login page). */
    public void publicGet(String path, Handler handler) {
        add("GET", path, handler, true);
    }

    public void publicPost(String path, Handler handler) {
        add("POST", path, handler, true);
    }

    private void add(String method, String path, Handler handler, boolean open) {
        List<String> names = new ArrayList<>();
        Matcher m = Pattern.compile("\\{(\\w+)}").matcher(path);
        StringBuilder regex = new StringBuilder();
        int last = 0;
        while (m.find()) {
            regex.append(Pattern.quote(path.substring(last, m.start()))).append("([^/]+)");
            names.add(m.group(1));
            last = m.end();
        }
        regex.append(Pattern.quote(path.substring(last)));
        routes.add(new Route(method, Pattern.compile(regex.toString()), names, handler, open));
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        Response response;
        Request request = null;
        try {
            URI uri = exchange.getRequestURI();
            String path = uri.getPath();
            String method = exchange.getRequestMethod().toUpperCase();
            if (method.equals("GET") && path.startsWith("/static/")) {
                response = staticFile(path.substring("/static/".length()));
            } else {
                String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                response = null;
                for (Route route : routes) {
                    Matcher matcher = route.pattern().matcher(path);
                    if (route.method().equals(method) && matcher.matches()) {
                        Map<String, String> params = new LinkedHashMap<>();
                        for (int i = 0; i < route.names().size(); i++) {
                            params.put(route.names().get(i), matcher.group(i + 1));
                        }
                        request = new Request(method, path, uri.getRawQuery(), body, params,
                            exchange.getRequestHeaders().getFirst("Referer"),
                            exchange.getRequestHeaders().getFirst("Cookie"));
                        User user = auth.userFor(request.cookie(AuthService.COOKIE));
                        request.setUser(user);
                        if (user == null && !route.open()) {
                            // Not signed in: go to the login page, then come back here afterwards.
                            String next = method.equals("GET") ? path + (uri.getRawQuery() == null ? "" : "?" + uri.getRawQuery()) : "/";
                            response = Response.redirect("/login?next=" + java.net.URLEncoder.encode(next, StandardCharsets.UTF_8));
                        } else {
                            Actor.set(user);
                            response = route.handler().handle(request);
                        }
                        break;
                    }
                }
                if (response == null) {
                    response = errorPage(null, 404, "Page not found", "There is no page at " + path + ".");
                }
            }
        } catch (ForbiddenException e) {
            response = errorPage(request, 403, "Not allowed", e.getMessage());
        } catch (NotFoundException e) {
            response = errorPage(request, 404, "Not found", e.getMessage());
        } catch (ValidationException e) {
            // A form problem that the handler did not deal with: go back and show the message.
            response = Response.error(backTo(request), e.getMessage());
        } catch (Exception e) {
            e.printStackTrace();
            response = errorPage(request, 500, "Something went wrong", e.getMessage() == null ? e.toString() : e.getMessage());
        } finally {
            Actor.clear();
        }
        send(exchange, response);
    }

    private Response errorPage(Request request, int status, String title, String message) {
        try {
            return Response.html(status, view.render(request, title, "", "error",
                Model.of("heading", title, "message", message, "status", status)));
        } catch (RuntimeException e) {
            // The layout itself failed (for example the database is down): show plain HTML.
            return Response.html(status, "<!doctype html><title>" + Template.escape(title) + "</title><h1>"
                + Template.escape(title) + "</h1><p>" + Template.escape(message) + "</p><p>"
                + Template.escape(String.valueOf(e.getMessage())) + "</p><p><a href=\"/\">Back to dashboard</a></p>");
        }
    }

    /** The page the form came from, without old ?msg= / ?err= messages. */
    private static String backTo(Request request) {
        if (request == null || request.referer() == null) {
            return "/";
        }
        try {
            URI referer = URI.create(request.referer());
            String query = referer.getRawQuery();
            StringBuilder kept = new StringBuilder();
            if (query != null) {
                for (String part : query.split("&")) {
                    if (!part.startsWith("msg=") && !part.startsWith("err=") && !part.isEmpty()) {
                        kept.append(kept.length() == 0 ? "?" : "&").append(part);
                    }
                }
            }
            return (referer.getRawPath() == null || referer.getRawPath().isEmpty() ? "/" : referer.getRawPath()) + kept;
        } catch (IllegalArgumentException e) {
            return "/";
        }
    }

    private static Response staticFile(String name) throws IOException {
        if (name.contains("..")) {
            return new Response(404, "text/plain", "Not found".getBytes(StandardCharsets.UTF_8), null);
        }
        try (InputStream in = Router.class.getClassLoader().getResourceAsStream("static/" + name)) {
            if (in == null) {
                return new Response(404, "text/plain", "Not found".getBytes(StandardCharsets.UTF_8), null);
            }
            return new Response(200, contentType(name), in.readAllBytes(), null);
        }
    }

    private static String contentType(String name) {
        if (name.endsWith(".css")) {
            return "text/css; charset=utf-8";
        }
        if (name.endsWith(".svg")) {
            return "image/svg+xml";
        }
        if (name.endsWith(".png")) {
            return "image/png";
        }
        if (name.endsWith(".ico")) {
            return "image/x-icon";
        }
        if (name.endsWith(".jpg") || name.endsWith(".jpeg")) {
            return "image/jpeg";
        }
        if (name.endsWith(".webp")) {
            return "image/webp";
        }
        if (name.endsWith(".gif")) {
            return "image/gif";
        }
        if (name.endsWith(".txt")) {
            return "text/plain; charset=utf-8";
        }
        return "application/octet-stream";
    }

    private static void send(HttpExchange exchange, Response response) throws IOException {
        if (response.location() != null) {
            exchange.getResponseHeaders().set("Location", response.location());
        }
        if (response.setCookie() != null) {
            exchange.getResponseHeaders().add("Set-Cookie", response.setCookie());
        }
        if (response.contentType() != null) {
            exchange.getResponseHeaders().set("Content-Type", response.contentType());
        }
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        byte[] body = response.body();
        exchange.sendResponseHeaders(response.status(), body.length == 0 ? -1 : body.length);
        if (body.length > 0) {
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(body);
            }
        }
        exchange.close();
    }
}
