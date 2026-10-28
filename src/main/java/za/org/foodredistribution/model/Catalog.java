package za.org.foodredistribution.model;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Fixed lists used by the forms and by the matching engine. */
public final class Catalog {

    private Catalog() {
    }

    public static final List<String> CATEGORIES = List.of(
        "Prepared meals", "Bakery", "Fruits and vegetables", "Dairy",
        "Meat and poultry", "Packaged food", "Beverages");

    public static final List<String> STORAGE_TYPES = List.of("Ambient", "Cool", "Refrigerated", "Frozen");

    public static final List<String> UNITS = List.of("kg", "meals", "litres", "items", "boxes");

    public static final List<String> URGENCY_LEVELS = List.of("Critical", "High", "Medium", "Low");

    public static final List<String> DONOR_TYPES = List.of(
        "Hotel", "Restaurant", "Bakery", "Retailer", "Caterer", "Farm", "Manufacturer");

    public static final List<String> RECIPIENT_TYPES = List.of(
        "Community kitchen", "Food bank", "Shelter", "Non-profit organisation", "School", "Care home");

    /** Areas of Cape Town with approximate coordinates, used for distance calculations. */
    public static final Map<String, double[]> AREAS = new LinkedHashMap<>();

    static {
        AREAS.put("Athlone", new double[] {-33.963, 18.505});
        AREAS.put("Bellville", new double[] {-33.900, 18.629});
        AREAS.put("Cape Town CBD", new double[] {-33.925, 18.424});
        AREAS.put("Claremont", new double[] {-33.981, 18.465});
        AREAS.put("Durbanville", new double[] {-33.832, 18.648});
        AREAS.put("Green Point", new double[] {-33.910, 18.411});
        AREAS.put("Gugulethu", new double[] {-33.976, 18.567});
        AREAS.put("Khayelitsha", new double[] {-34.039, 18.677});
        AREAS.put("Langa", new double[] {-33.945, 18.530});
        AREAS.put("Mitchells Plain", new double[] {-34.050, 18.620});
        AREAS.put("Observatory", new double[] {-33.936, 18.470});
        AREAS.put("Salt River", new double[] {-33.931, 18.458});
        AREAS.put("Sea Point", new double[] {-33.918, 18.386});
        AREAS.put("V&A Waterfront", new double[] {-33.907, 18.421});
        AREAS.put("Woodstock", new double[] {-33.927, 18.445});
    }

    public static double[] coordinatesOf(String area) {
        double[] coordinates = AREAS.get(area);
        if (coordinates == null) {
            throw new IllegalArgumentException("Unknown area: " + area);
        }
        return coordinates;
    }
}
