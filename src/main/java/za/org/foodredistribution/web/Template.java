package za.org.foodredistribution.web;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import za.org.foodredistribution.model.Format;

/**
 * A very small HTML template engine (a subset of the "Mustache" syntax), so that all HTML lives in
 * .html files under src/main/resources/templates.
 *
 * <pre>
 *   {{name}}            value, HTML-escaped          {{{name}}}  value, not escaped (trusted HTML)
 *   {{donation.status}} property of an object        {{.}}       the current item in a loop
 *   {{#items}}...{{/items}}   loop over a list, or show the block when the value is true / present
 *   {{^items}}...{{/items}}   show the block when the value is false, empty or missing
 *   {{> donations/badge}}     include another template
 *   {{! comment }}
 * </pre>
 *
 * Values can be Maps, Java records (their accessor methods) or any object with public no-argument methods.
 */
public final class Template {

    private static final Object MISSING = new Object();

    /** When true, a name that cannot be found anywhere throws an error (useful while developing). */
    private final boolean strict;
    private final Map<String, Method> methods = new ConcurrentHashMap<>();

    public Template(boolean strict) {
        this.strict = strict;
    }

    public String render(String name, Object model) {
        StringBuilder out = new StringBuilder();
        Deque<Object> stack = new ArrayDeque<>();
        stack.push(model);
        renderNodes(load(name), stack, out, name);
        return out.toString();
    }

    // ------------------------------------------------------------------ parsing

    private sealed interface Node permits Text, Var, Section, Partial {
    }

    private record Text(String text) implements Node {
    }

    private record Var(String name, boolean escape) implements Node {
    }

    private record Section(String name, boolean inverted, List<Node> children) implements Node {
    }

    private record Partial(String name) implements Node {
    }

    /** Templates are read on every request, so edits to the .html files show up without a restart. */
    private List<Node> load(String name) {
        return parse(read(name), name);
    }

    private static String read(String name) {
        String path = "templates/" + name + ".html";
        try (InputStream in = Template.class.getClassLoader().getResourceAsStream(path)) {
            if (in == null) {
                throw new IllegalStateException("Template not found: " + path);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Could not read template " + path, e);
        }
    }

    private static List<Node> parse(String source, String templateName) {
        Deque<List<Node>> levels = new ArrayDeque<>();
        Deque<String[]> open = new ArrayDeque<>(); // {name, inverted}
        levels.push(new ArrayList<>());
        int pos = 0;
        while (pos < source.length()) {
            int start = source.indexOf("{{", pos);
            if (start < 0) {
                levels.peek().add(new Text(source.substring(pos)));
                break;
            }
            if (start > pos) {
                levels.peek().add(new Text(source.substring(pos, start)));
            }
            boolean triple = source.startsWith("{{{", start);
            String closeMark = triple ? "}}}" : "}}";
            int end = source.indexOf(closeMark, start);
            if (end < 0) {
                throw new IllegalStateException("Unclosed tag in template " + templateName + " at position " + start);
            }
            String tag = source.substring(start + (triple ? 3 : 2), end).trim();
            pos = end + closeMark.length();
            if (triple) {
                levels.peek().add(new Var(tag, false));
                continue;
            }
            char type = tag.isEmpty() ? ' ' : tag.charAt(0);
            String name = tag.length() > 1 ? tag.substring(1).trim() : "";
            switch (type) {
                case '!' -> {
                    // comment
                }
                case '#', '^' -> {
                    open.push(new String[] {name, String.valueOf(type == '^')});
                    levels.push(new ArrayList<>());
                }
                case '/' -> {
                    if (open.isEmpty() || !open.peek()[0].equals(name)) {
                        throw new IllegalStateException("Unexpected {{/" + name + "}} in template " + templateName);
                    }
                    String[] section = open.pop();
                    List<Node> children = levels.pop();
                    levels.peek().add(new Section(section[0], Boolean.parseBoolean(section[1]), children));
                }
                case '>' -> levels.peek().add(new Partial(name));
                default -> levels.peek().add(new Var(tag, true));
            }
        }
        if (!open.isEmpty()) {
            throw new IllegalStateException("Missing {{/" + open.peek()[0] + "}} in template " + templateName);
        }
        return levels.pop();
    }

    // ------------------------------------------------------------------ rendering

    private void renderNodes(List<Node> nodes, Deque<Object> stack, StringBuilder out, String templateName) {
        for (Node node : nodes) {
            if (node instanceof Text text) {
                out.append(text.text());
            } else if (node instanceof Var var) {
                Object value = lookup(var.name(), stack, templateName);
                String string = toText(value);
                out.append(var.escape() ? escape(string) : string);
            } else if (node instanceof Partial partial) {
                renderNodes(load(partial.name()), stack, out, partial.name());
            } else if (node instanceof Section section) {
                Object value = lookup(section.name(), stack, templateName);
                if (section.inverted()) {
                    if (!truthy(value)) {
                        renderNodes(section.children(), stack, out, templateName);
                    }
                } else if (value instanceof Collection<?> items) {
                    for (Object item : items) {
                        stack.push(item == null ? MISSING : item);
                        renderNodes(section.children(), stack, out, templateName);
                        stack.pop();
                    }
                } else if (value instanceof Boolean) {
                    if ((Boolean) value) {
                        renderNodes(section.children(), stack, out, templateName);
                    }
                } else if (truthy(value)) {
                    stack.push(value);
                    renderNodes(section.children(), stack, out, templateName);
                    stack.pop();
                }
            }
        }
    }

    private Object lookup(String name, Deque<Object> stack, String templateName) {
        if (name.equals(".")) {
            return stack.peek();
        }
        String[] parts = name.split("\\.");
        Object value = MISSING;
        Iterator<Object> frames = stack.iterator(); // top of the stack first
        while (frames.hasNext()) {
            value = property(frames.next(), parts[0]);
            if (value != MISSING) {
                break;
            }
        }
        if (value == MISSING) {
            if (strict) {
                throw new IllegalStateException("Template " + templateName + ": unknown name '" + parts[0] + "'");
            }
            return null;
        }
        for (int i = 1; i < parts.length && value != null; i++) {
            Object next = property(value, parts[i]);
            if (next == MISSING) {
                if (strict && !(value instanceof Map)) {
                    throw new IllegalStateException("Template " + templateName + ": '" + parts[i]
                        + "' not found on " + value.getClass().getSimpleName());
                }
                return null;
            }
            value = next;
        }
        return value;
    }

    /** Reads a key from a Map, or calls a public no-argument method (record accessor). */
    private Object property(Object target, String name) {
        if (target == null || target == MISSING) {
            return MISSING;
        }
        if (target instanceof Map<?, ?> map) {
            return map.containsKey(name) ? map.get(name) : MISSING;
        }
        if (target instanceof Collection<?> collection) {
            return switch (name) {
                case "size" -> collection.size();
                case "empty" -> collection.isEmpty();
                default -> MISSING;
            };
        }
        Class<?> type = target.getClass();
        if (type.getName().startsWith("java.")) {
            return MISSING;
        }
        String key = type.getName() + "#" + name;
        Method method = methods.get(key);
        if (method == null) {
            try {
                method = type.getMethod(name);
                method.setAccessible(true);
                methods.put(key, method);
            } catch (NoSuchMethodException e) {
                return MISSING;
            }
        }
        try {
            return method.invoke(target);
        } catch (IllegalAccessException | InvocationTargetException e) {
            Throwable cause = e instanceof InvocationTargetException ite ? ite.getCause() : e;
            throw new IllegalStateException("Could not read '" + name + "' from " + type.getSimpleName() + ": " + cause, cause);
        }
    }

    private static boolean truthy(Object value) {
        if (value == null || value == MISSING) {
            return false;
        }
        if (value instanceof Boolean b) {
            return b;
        }
        if (value instanceof Collection<?> c) {
            return !c.isEmpty();
        }
        if (value instanceof String s) {
            return !s.isEmpty();
        }
        return true;
    }

    private static String toText(Object value) {
        if (value == null || value == MISSING) {
            return "";
        }
        if (value instanceof Double || value instanceof Float) {
            return Format.number(((Number) value).doubleValue());
        }
        return value.toString();
    }

    public static String escape(String text) {
        StringBuilder out = new StringBuilder(text.length());
        for (char c : text.toCharArray()) {
            switch (c) {
                case '&' -> out.append("&amp;");
                case '<' -> out.append("&lt;");
                case '>' -> out.append("&gt;");
                case '"' -> out.append("&quot;");
                case '\'' -> out.append("&#39;");
                default -> out.append(c);
            }
        }
        return out.toString();
    }
}
