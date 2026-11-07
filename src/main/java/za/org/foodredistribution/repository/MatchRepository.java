package za.org.foodredistribution.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import za.org.foodredistribution.db.Database;
import za.org.foodredistribution.db.Row;
import za.org.foodredistribution.model.FactorScores;
import za.org.foodredistribution.model.Match;

public class MatchRepository {

    private static final String SELECT = """
        SELECT m.*, n.recipient_organisation_id, n.urgency, n.storage_type,
               n.storage_capacity AS need_storage_capacity, n.opens_at, n.closes_at,
               o.name AS recipient_name, o.organisation_type, o.area
        FROM matches m
        JOIN recipient_needs n ON n.id = m.recipient_need_id
        JOIN organisations o ON o.id = n.recipient_organisation_id
        """;

    private final Database db;

    public MatchRepository(Database db) {
        this.db = db;
    }

    public List<Match> findByDonation(long donationId) {
        return db.query(SELECT + " WHERE m.donation_id = ? ORDER BY m.rank_position, m.id", donationId)
            .stream().map(this::map).toList();
    }

    public List<Match> findByStatus(String status) {
        return db.query(SELECT + " WHERE m.status = ?", status).stream().map(this::map).toList();
    }

    public Match findById(long id) {
        Row row = db.queryOne(SELECT + " WHERE m.id = ?", id);
        return row == null ? null : map(row);
    }

    /** Needs that already have a decided match (accepted/declined) for this donation. */
    public Set<Long> decidedNeedIds(long donationId) {
        return db.query("SELECT recipient_need_id FROM matches WHERE donation_id = ? AND status IN (?, ?)",
                donationId, Match.ACCEPTED, Match.DECLINED)
            .stream().map(r -> r.getLong("recipient_need_id")).collect(Collectors.toSet());
    }

    /** Removes undecided recommendations so they can be recalculated. */
    public void deleteUndecided(long donationId) {
        db.update("DELETE FROM matches WHERE donation_id = ? AND status IN (?, ?)",
            donationId, Match.RECOMMENDED, Match.NOT_SELECTED);
    }

    public long insert(long donationId, long needId, int rank, double score, double distanceKm,
                       double quantityCanAccept, FactorScores f, String explanation, LocalDateTime createdAt) {
        return db.insert("""
            INSERT INTO matches
              (donation_id, recipient_need_id, rank_position, score, distance_km, quantity_can_accept,
               expiry_urgency, recipient_demand, food_suitability, storage_capacity, quantity_compatibility,
               distance_score, transport_availability, collection_compatibility, explanation, status, created_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""",
            donationId, needId, rank, score, distanceKm, quantityCanAccept,
            f.expiryUrgency(), f.recipientDemand(), f.foodSuitability(), f.storageCapacity(),
            f.quantityCompatibility(), f.distance(), f.transportAvailability(), f.collectionCompatibility(),
            explanation, Match.RECOMMENDED, createdAt);
    }

    public void updateStatus(long id, String status, String declineReason) {
        db.update("UPDATE matches SET status = ?, decline_reason = ? WHERE id = ?", status, declineReason, id);
    }

    /** After one recipient accepts, the other recommendations for the donation are closed. */
    public void closeOtherRecommendations(long donationId, long acceptedMatchId) {
        db.update("UPDATE matches SET status = ? WHERE donation_id = ? AND id <> ? AND status = ?",
            Match.NOT_SELECTED, donationId, acceptedMatchId, Match.RECOMMENDED);
    }

    public void closeAllRecommendations(long donationId) {
        db.update("UPDATE matches SET status = ? WHERE donation_id = ? AND status = ?",
            Match.NOT_SELECTED, donationId, Match.RECOMMENDED);
    }

    private Match map(Row r) {
        FactorScores factors = new FactorScores(
            r.getDouble("expiry_urgency"), r.getDouble("recipient_demand"), r.getDouble("food_suitability"),
            r.getDouble("storage_capacity"), r.getDouble("quantity_compatibility"), r.getDouble("distance_score"),
            r.getDouble("transport_availability"), r.getDouble("collection_compatibility"));
        return new Match(
            r.getLong("id"), r.getLong("donation_id"), r.getLong("recipient_need_id"),
            r.getLong("recipient_organisation_id"), r.getString("recipient_name"), r.getString("organisation_type"),
            r.getString("area"), r.getString("urgency"), r.getString("storage_type"),
            r.getDouble("need_storage_capacity"), r.getString("opens_at") + " - " + r.getString("closes_at"),
            r.getInt("rank_position"), r.getDouble("score"), r.getDouble("distance_km"),
            r.getDouble("quantity_can_accept"), factors, r.getString("explanation"), r.getString("status"),
            r.getString("decline_reason"));
    }
}
