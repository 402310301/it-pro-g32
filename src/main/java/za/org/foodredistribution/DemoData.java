package za.org.foodredistribution;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import za.org.foodredistribution.auth.PasswordHasher;
import za.org.foodredistribution.auth.Role;
import za.org.foodredistribution.auth.UserRepository;
import za.org.foodredistribution.model.Catalog;
import za.org.foodredistribution.model.Donation;
import za.org.foodredistribution.model.DonationStatus;
import za.org.foodredistribution.model.Match;
import za.org.foodredistribution.model.Organisation;
import za.org.foodredistribution.model.RecipientNeed;
import za.org.foodredistribution.model.TransportAssignment;
import za.org.foodredistribution.service.DonationService;
import za.org.foodredistribution.service.MatchingEngine;
import za.org.foodredistribution.service.Repositories;
import za.org.foodredistribution.service.TransportService;

/**
 * Fills an empty database with a realistic Cape Town scenario: donors, recipients, drivers,
 * six months of history for the reports, and a few live donations at different stages.
 * Dates are relative to "now", so the demo always looks current.
 */
public final class DemoData {

    private final Repositories repos;
    private final DonationService donations;
    private final TransportService transport;
    private final MatchingEngine engine;
    private final LocalDateTime now = LocalDateTime.now().withSecond(0).withNano(0);

    public DemoData(Repositories repos, DonationService donations, TransportService transport, MatchingEngine engine) {
        this.repos = repos;
        this.donations = donations;
        this.transport = transport;
        this.engine = engine;
    }

    /** Loads the demo data only when the database has no organisations yet. Returns true if data was added. */
    public boolean seedIfEmpty() {
        if (repos.organisations().count() > 0) {
            return false;
        }
        repos.config().load(); // stores the default matching weights
        LocalDateTime setup = now.minusMonths(6);

        // Donors
        long hotel = org("Table Bay Harvest Hotel", Organisation.DONOR, "Hotel", "V&A Waterfront", "1 Quay Street", "Lindiwe Khumalo", setup);
        long bakery = org("Cape Town Central Bakery", Organisation.DONOR, "Bakery", "Cape Town CBD", "48 Long Street", "Pieter van Wyk", setup);
        long grocer = org("Green Point Grocer", Organisation.DONOR, "Retailer", "Green Point", "12 Main Road", "Naledi Dlamini", setup);
        long caterer = org("Mzansi Events Catering", Organisation.DONOR, "Caterer", "Observatory", "7 Lower Main Road", "Kagiso Molefe", setup);
        long bistro = org("Sea Point Bistro", Organisation.DONOR, "Restaurant", "Sea Point", "210 Beach Road", "Sarah Adams", setup);

        // Recipients
        long kitchen = org("Ubuntu Community Kitchen", Organisation.RECIPIENT, "Community kitchen", "Salt River", "19 Albert Road", "Zanele Ndlovu", setup);
        long foodBank = org("Khayelitsha Food Bank", Organisation.RECIPIENT, "Food bank", "Khayelitsha", "3 Lansdowne Road", "Mandla Sithole", setup);
        long shelter = org("Harbour Haven Shelter", Organisation.RECIPIENT, "Shelter", "Woodstock", "88 Victoria Road", "Grace Petersen", setup);
        long youth = org("Langa Youth Support", Organisation.RECIPIENT, "Non-profit organisation", "Langa", "5 Washington Street", "Bongani Mthembu", setup);
        long careNetwork = org("Mitchells Plain Care Network", Organisation.RECIPIENT, "Non-profit organisation", "Mitchells Plain", "22 AZ Berman Drive", "Fatima Jacobs", setup);
        long seniors = repos.organisations().insert("Gugulethu Seniors Club", Organisation.RECIPIENT, "Care home",
            "Gugulethu", "14 NY1 Street", -33.976, 18.567, "Thembi Mokoena", Organisation.PENDING, now.minusDays(1));

        // An early-registered soup run with no fridge: "first come, first served" would send it chilled food
        long soupRun = org("Salt River Soup Run", Organisation.RECIPIENT, "Community kitchen", "Salt River", "2 Voortrekker Road", "Yusuf Hendricks", setup.minusDays(1));

        // Volunteer drivers
        long thando = repos.transporters().insert("Thando Mbeki", "082 555 0101", "Ford Transit Connect", 250, true, setup);
        long ayesha = repos.transporters().insert("Ayesha Daniels", "083 555 0202", "Toyota Hilux", 500, false, setup);
        repos.transporters().insert("Sipho Nkosi", "084 555 0303", "Nissan NV200", 300, true, setup);

        // Recipient needs
        need(soupRun, "Prepared meals", "Hot meals served the same evening", 40, 40, 40, "Ambient", "16:00", "20:00", "Medium");
        long kitchenMeals = need(kitchen, "Prepared meals", "Evening meals for families in Salt River", 120, 150, 180, "Refrigerated", "09:00", "17:30", "Critical");
        long bankBread = need(foodBank, "Bakery", "Bread and rolls for weekly parcels", 90, 60, 300, "Ambient", "08:00", "18:00", "High");
        long shelterMeals = need(shelter, "Prepared meals", "Meals for overnight residents", 60, 70, 80, "Refrigerated", "11:00", "20:00", "High");
        long youthProduce = need(youth, "Fruits and vegetables", "Fresh produce for youth meal programmes", 75, 60, 65, "Cool", "10:00", "16:00", "Medium");
        long careBread = need(careNetwork, "Bakery", "Bread for community distribution", 80, 55, 140, "Ambient", "07:00", "19:00", "High");
        long bankPantry = need(foodBank, "Packaged food", "Rice, beans and tinned food for parcels", 200, 150, 400, "Ambient", "08:00", "18:00", "Medium");
        need(seniors, "Dairy", "Milk and yoghurt for the seniors' breakfast club", 40, 40, 60, "Refrigerated", "08:00", "15:00", "High");

        // Six months of completed donations, for the impact report
        Object[][] history = {
            {5, bakery, bankBread, "Bakery", "Day-old loaves and rolls", 60.0, "kg", "Ambient"},
            {5, hotel, kitchenMeals, "Prepared meals", "Buffet surplus: curry and rice", 70.0, "meals", "Refrigerated"},
            {4, grocer, youthProduce, "Fruits and vegetables", "Apples, carrots and spinach", 40.0, "kg", "Cool"},
            {4, caterer, bankPantry, "Packaged food", "Unopened pasta and tinned tomatoes", 90.0, "kg", "Ambient"},
            {3, bakery, careBread, "Bakery", "Brown bread and buns", 55.0, "kg", "Ambient"},
            {3, hotel, shelterMeals, "Prepared meals", "Conference lunch portions", 50.0, "meals", "Refrigerated"},
            {3, bistro, kitchenMeals, "Prepared meals", "Vegetable lasagne portions", 30.0, "meals", "Refrigerated"},
            {2, caterer, bankPantry, "Packaged food", "Rice, lentils and beans", 120.0, "kg", "Ambient"},
            {2, grocer, youthProduce, "Fruits and vegetables", "Seasonal citrus and potatoes", 35.0, "kg", "Cool"},
            {2, bakery, bankBread, "Bakery", "Mixed rolls", 60.0, "kg", "Ambient"},
            {1, hotel, kitchenMeals, "Prepared meals", "Chicken stew and pap", 90.0, "meals", "Refrigerated"},
            {1, bakery, careBread, "Bakery", "White and brown loaves", 50.0, "kg", "Ambient"},
            {1, caterer, bankPantry, "Packaged food", "Breakfast cereal and long-life milk", 70.0, "kg", "Ambient"},
            {1, grocer, youthProduce, "Fruits and vegetables", "Bananas and butternut", 45.0, "kg", "Cool"},
        };
        for (int i = 0; i < history.length; i++) {
            Object[] h = history[i];
            LocalDateTime listedAt = now.minusMonths((Integer) h[0]).withDayOfMonth(3 + (i % 4) * 6).withHour(9).withMinute(15);
            completedDonation((Long) h[1], (Long) h[2], (String) h[3], (String) h[4], (Double) h[5], (String) h[6],
                (String) h[7], listedAt, 1 + (i % 3), null);
        }
        // An older donation that nobody collected in time
        LocalDateTime lost = now.minusMonths(2).withDayOfMonth(20).withHour(16).withMinute(0);
        long lostId = donation(bistro, "Prepared meals", "Seafood pasta portions", 25, "meals", "Refrigerated",
            lost.plusHours(6), lost.plusHours(3), true, lost);
        repos.audit().insert("Sea Point Bistro", "Donation listed", "Donation", lostId, null, DonationStatus.LISTED, lost);
        repos.donations().updateStatus(lostId, DonationStatus.EXPIRED, lost.plusHours(6));
        repos.audit().insert(DonationService.SYSTEM, "Donation expired", "Donation", lostId, DonationStatus.LISTED, DonationStatus.EXPIRED, lost.plusHours(6));

        // Yesterday: delivered by a volunteer driver
        completedDonation(caterer, bankPantry, "Packaged food", "Unopened rice, beans and canned goods", 72, "kg",
            "Ambient", now.minusDays(1).withHour(8).withMinute(30), 2, ayesha);

        // Live donations
        liveDonation(hotel, "Prepared meals", "Freshly prepared chicken and vegetable meals", 85, "meals",
            "Refrigerated", 9, 5, true, 1);
        liveDonation(bakery, "Bakery", "Mixed loaves and rolls baked this morning", 100, "kg", "Ambient", 27, 7, true, 2);
        long produce = liveDonation(grocer, "Fruits and vegetables", "Seasonal vegetables and citrus", 48, "kg", "Cool",
            56, 8, true, 5);
        liveDonation(grocer, "Dairy", "Yoghurt and milk close to sell-by date", 30, "litres", "Refrigerated", 48, 20, false, 1);
        long expiring = donation(bistro, "Prepared meals", "Vegetarian pasta portions", 35, "meals", "Refrigerated",
            now.minusHours(2), now.minusHours(5), true, now.minusHours(9));
        repos.audit().insert("Sea Point Bistro", "Donation listed", "Donation", expiring, null, DonationStatus.LISTED, now.minusHours(9));

        // The produce donation has been accepted and a driver is on the way
        Match langa = repos.matches().findByDonation(produce).stream()
            .filter(m -> m.needId() == youthProduce).findFirst().orElse(null);
        if (langa != null) {
            donations.acceptMatch(langa.id(), null);
            transport.assign(Map.of("donationId", String.valueOf(produce), "transporterId", String.valueOf(thando),
                "pickupWindow", "Today " + now.plusHours(2).format(DateTimeFormatter.ofPattern("HH:00")) + " - "
                    + now.plusHours(5).format(DateTimeFormatter.ofPattern("HH:00"))));
            long assignmentId = repos.assignments().findByDonation(produce).get(0).id();
            transport.updateStatus(assignmentId, TransportAssignment.ACCEPTED);
        }
        // Mark the expired one, so it shows in notifications
        donations.expireOverdue();
        repos.notifications().insert(null, "Welcome", "Demo data loaded. Try accepting a match, assigning a driver, "
            + "or approving the pending Gugulethu Seniors Club in Admin.", "admin", false, now);
        return true;
    }

    /**
     * Demo sign-ins (also listed on the login page while demo.seed=true).
     * Created when the users table is empty. Change or disable these before real use!
     */
    public void seedUsers(UserRepository users) {
        if (users.count() > 0) {
            return;
        }
        LocalDateTime at = now;
        users.insert("admin", "Site Administrator", PasswordHasher.hash("admin123"), Role.ADMIN, null, at);
        users.insert("coordinator", "Nomsa Dube", PasswordHasher.hash("coord123"), Role.COORDINATOR, null, at);
        users.insert("hotel", "Lindiwe Khumalo", PasswordHasher.hash("donor123"), Role.DONOR, orgId("Table Bay Harvest Hotel"), at);
        users.insert("bakery", "Pieter van Wyk", PasswordHasher.hash("donor123"), Role.DONOR, orgId("Cape Town Central Bakery"), at);
        users.insert("kitchen", "Zanele Ndlovu", PasswordHasher.hash("recipient123"), Role.RECIPIENT, orgId("Ubuntu Community Kitchen"), at);
        users.insert("foodbank", "Mandla Sithole", PasswordHasher.hash("recipient123"), Role.RECIPIENT, orgId("Khayelitsha Food Bank"), at);
    }

    private Long orgId(String name) {
        return repos.organisations().findAll().stream()
            .filter(o -> o.name().equals(name))
            .map(Organisation::id)
            .findFirst()
            .orElse(null);
    }

    // ------------------------------------------------------------------ helpers

    private long org(String name, String role, String type, String area, String address, String contact, LocalDateTime at) {
        double[] c = Catalog.coordinatesOf(area);
        return repos.organisations().insert(name, role, type, area, address, c[0], c[1], contact, Organisation.APPROVED, at);
    }

    private long need(long organisationId, String category, String description, double needed, double max,
                      double capacity, String storage, String opens, String closes, String urgency) {
        return repos.needs().insert(organisationId, category, description, needed, max, capacity, storage, opens,
            closes, urgency, now.minusMonths(6));
    }

    private long donation(long donorId, String category, String description, double quantity, String unit,
                          String storage, LocalDateTime expiry, LocalDateTime deadline, boolean transportRequired,
                          LocalDateTime listedAt) {
        Organisation donor = repos.organisations().findById(donorId);
        String prefix = "DON-" + listedAt.format(DateTimeFormatter.ofPattern("yyMMdd")) + "-";
        String reference = prefix + String.format("%03d", repos.donations().countByReferencePrefix(prefix) + 1);
        return repos.donations().insert(reference, donorId, category, description, quantity, unit, storage, expiry,
            deadline, donor.area(), donor.address(), donor.latitude(), donor.longitude(), transportRequired,
            DonationStatus.LISTED, listedAt);
    }

    /** A live donation listed some hours ago; the matching engine runs on it straight away. */
    private long liveDonation(long donorId, String category, String description, double quantity, String unit,
                              String storage, int expiresInHours, int collectWithinHours, boolean transportRequired,
                              int listedHoursAgo) {
        LocalDateTime listedAt = now.minusHours(listedHoursAgo);
        long id = donation(donorId, category, description, quantity, unit, storage, now.plusHours(expiresInHours),
            now.plusHours(collectWithinHours), transportRequired, listedAt);
        repos.audit().insert(repos.organisations().findById(donorId).name(), "Donation listed", "Donation", id, null,
            DonationStatus.LISTED, listedAt);
        donations.runMatching(donations.get(id));
        return id;
    }

    /** A finished donation with its accepted match and audit history, back-dated to listedAt. */
    private void completedDonation(long donorId, long needId, String category, String description, double quantity,
                                   String unit, String storage, LocalDateTime listedAt, int hoursToAccept,
                                   Long driverId) {
        boolean transportRequired = driverId != null;
        long id = donation(donorId, category, description, quantity, unit, storage, listedAt.plusDays(2),
            listedAt.plusHours(8), transportRequired, listedAt);
        Donation donation = donations.get(id);
        RecipientNeed need = repos.needs().findById(needId).asOpen();
        MatchingEngine.Candidate c = engine.assess(donation, need, repos.transporters().findActive(),
            repos.config().load(), listedAt);
        long matchId = repos.matches().insert(id, needId, 1, c.score(), c.distanceKm(), c.quantityCanAccept(),
            c.factors(), c.explanation(), listedAt.plusMinutes(5));
        repos.matches().updateStatus(matchId, Match.ACCEPTED, null);

        LocalDateTime acceptedAt = listedAt.plusHours(hoursToAccept);
        LocalDateTime collectedAt = acceptedAt.plusHours(2);
        LocalDateTime deliveredAt = collectedAt.plusHours(1);
        repos.donations().assignRecipient(id, need.organisationId(), c.quantityCanAccept(), DonationStatus.COMPLETED, deliveredAt);

        String donor = donation.donorName();
        String accepted = transportRequired ? DonationStatus.TRANSPORT_REQUIRED : DonationStatus.ACCEPTED;
        repos.audit().insert(donor, "Donation listed", "Donation", id, null, DonationStatus.LISTED, listedAt);
        repos.audit().insert(DonationService.MATCHING_ENGINE, "Matches generated", "Donation", id, DonationStatus.LISTED,
            DonationStatus.RECIPIENT_RECOMMENDED, listedAt.plusMinutes(5));
        repos.audit().insert(need.organisationName(), "Match accepted", "Donation", id,
            DonationStatus.RECIPIENT_RECOMMENDED, accepted, acceptedAt);
        String before = accepted;
        if (transportRequired) {
            long trip = repos.assignments().insert(id, driverId, "Pickup " + collectedAt.format(DateTimeFormatter.ofPattern("HH:mm")),
                TransportAssignment.DELIVERED, acceptedAt.plusMinutes(30));
            repos.db().update("UPDATE transport_assignments SET updated_at = ? WHERE id = ?", deliveredAt, trip);
            repos.audit().insert(DonationService.COORDINATOR, "Transport assigned", "Donation", id, accepted,
                DonationStatus.TRANSPORT_ASSIGNED, acceptedAt.plusMinutes(30));
            before = DonationStatus.TRANSPORT_ASSIGNED;
        }
        repos.audit().insert(need.organisationName(), "Pickup confirmed", "Donation", id, before, DonationStatus.COLLECTED, collectedAt);
        repos.audit().insert(need.organisationName(), "Delivery confirmed", "Donation", id, DonationStatus.COLLECTED,
            DonationStatus.COMPLETED, deliveredAt);
        if (transportRequired) {
            repos.notifications().insert(id, "Delivery confirmed", need.organisationName() + " received "
                + Math.round(c.quantityCanAccept()) + " " + unit + " from " + donor + ".", "delivery", true, deliveredAt);
        }
    }
}
