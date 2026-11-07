package za.org.foodredistribution.web;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/** Helpers for building the data passed to templates. */
public final class Model {

    private Model() {
    }

    /** Model.of("title", "Home", "count", 3) - null values are allowed. */
    public static Map<String, Object> of(Object... keyValues) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i < keyValues.length; i += 2) {
            map.put((String) keyValues[i], keyValues[i + 1]);
        }
        return map;
    }

    public record Option(String value, String label, boolean selected) {
    }

    /** Options for a select box where the value and the label are the same text. */
    public static List<Option> options(List<String> values, String selected) {
        List<Option> options = new ArrayList<>();
        for (String value : values) {
            options.add(new Option(value, value, value.equals(selected)));
        }
        return options;
    }

    /** Options for a select box built from objects, e.g. organisations (value = id, label = name). */
    public static <T> List<Option> options(List<T> items, Function<T, Object> value, Function<T, String> label, String selected) {
        List<Option> options = new ArrayList<>();
        for (T item : items) {
            String v = String.valueOf(value.apply(item));
            options.add(new Option(v, label.apply(item), v.equals(selected)));
        }
        return options;
    }
}
