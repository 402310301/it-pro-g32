package za.org.foodredistribution.model;

/** A volunteer driver and vehicle. */
public record Transporter(
    long id,
    String name,
    String phone,
    String vehicle,
    double capacityKg,
    boolean refrigerated,
    boolean active
) {
    public String capacityLabel() {
        return Format.number(capacityKg) + " kg";
    }

    public String summary() {
        return name + " - " + vehicle + " (" + capacityLabel() + (refrigerated ? ", refrigerated" : "") + ")";
    }
}
