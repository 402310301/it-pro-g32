package za.org.foodredistribution.web;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/** What the server sends back: an HTML page, a static file, or a redirect. */
public record Response(int status, String contentType, byte[] body, String location, String setCookie) {

    public Response(int status, String contentType, byte[] body, String location) {
        this(status, contentType, body, location, null);
    }

    /** The same response, plus a Set-Cookie header (used for the sign-in session). */
    public Response withCookie(String cookie) {
        return new Response(status, contentType, body, location, cookie);
    }

    public static Response html(String html) {
        return html(200, html);
    }

    public static Response html(int status, String html) {
        return new Response(status, "text/html; charset=utf-8", html.getBytes(StandardCharsets.UTF_8), null);
    }

    /** Redirect after a form post (POST-redirect-GET), optionally with a message shown on the next page. */
    public static Response redirect(String path) {
        return new Response(303, null, new byte[0], path);
    }

    public static Response redirect(String path, String messageKey, String message) {
        String separator = path.contains("?") ? "&" : "?";
        return redirect(path + separator + messageKey + "=" + URLEncoder.encode(message, StandardCharsets.UTF_8));
    }

    /** Redirect with a green success message. */
    public static Response success(String path, String message) {
        return redirect(path, "msg", message);
    }

    /** Redirect with a red error message. */
    public static Response error(String path, String message) {
        return redirect(path, "err", message);
    }
}
