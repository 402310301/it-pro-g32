package za.org.foodredistribution.repository;

import za.org.foodredistribution.db.Database;
import za.org.foodredistribution.db.Row;
import za.org.foodredistribution.model.MatchingWeights;

/** Stores the single row of matching weights. */
public class ConfigRepository {

    private final Database db;

    public ConfigRepository(Database db) {
        this.db = db;
    }

    public MatchingWeights load() {
        Row r = db.queryOne("SELECT * FROM matching_configuration WHERE id = 1");
        if (r == null) {
            save(MatchingWeights.DEFAULT);
            return MatchingWeights.DEFAULT;
        }
        return new MatchingWeights(r.getInt("expiry_urgency"), r.getInt("recipient_demand"),
            r.getInt("food_suitability"), r.getInt("storage_capacity"), r.getInt("quantity_compatibility"),
            r.getInt("distance"), r.getInt("transport_availability"), r.getInt("collection_compatibility"));
    }

    public void save(MatchingWeights w) {
        Object[] values = {w.expiryUrgency(), w.recipientDemand(), w.foodSuitability(), w.storageCapacity(),
            w.quantityCompatibility(), w.distance(), w.transportAvailability(), w.collectionCompatibility()};
        boolean exists = db.queryOne("SELECT id FROM matching_configuration WHERE id = 1") != null;
        if (exists) {
            db.update("""
                UPDATE matching_configuration
                SET expiry_urgency = ?, recipient_demand = ?, food_suitability = ?, storage_capacity = ?,
                    quantity_compatibility = ?, distance = ?, transport_availability = ?, collection_compatibility = ?
                WHERE id = 1""", values);
        } else {
            db.update("""
                INSERT INTO matching_configuration
                  (id, expiry_urgency, recipient_demand, food_suitability, storage_capacity,
                   quantity_compatibility, distance, transport_availability, collection_compatibility)
                VALUES (1, ?, ?, ?, ?, ?, ?, ?, ?)""", values);
        }
    }
}
