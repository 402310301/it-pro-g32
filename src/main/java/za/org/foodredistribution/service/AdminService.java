package za.org.foodredistribution.service;

import za.org.foodredistribution.auth.Actor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import za.org.foodredistribution.model.Catalog;
import za.org.foodredistribution.model.MatchingWeights;
import za.org.foodredistribution.model.Organisation;

/** Platform administration: organisations, drivers, recipient needs and matching weights. */
public class AdminService {

    private final Repositories repos;
    private final DonationService donations;
    private final EventLog events;

    public AdminService(Repositories repos, DonationService donations, EventLog events) {
        this.repos = repos;
        this.donations = donations;
        this.events = events;
    }

    public MatchingWeights weights() {
        return repos.config().load();
    }

    public void updateWeights(Map<String, String> form) {
        FormReader f = new FormReader(form);
        MatchingWeights weights = new MatchingWeights(
            f.integer("expiryUrgency", "Expiry urgency"),
            f.integer("recipientDemand", "Recipient demand"),
            f.integer("foodSuitability", "Food suitability"),
            f.integer("storageCapacity", "Storage capacity"),
            f.integer("quantityCompatibility", "Quantity fit"),
            f.integer("distance", "Distance"),
            f.integer("transportAvailability", "Transport"),
            f.integer("collectionCompatibility", "Collection window"));
        f.throwIfErrors();
        try {
            weights.validate();
        } catch (IllegalArgumentException e) {
            throw new ValidationException(e.getMessage());
        }
        repos.db().inTransaction(() -> {
            repos.config().save(weights);
            events.audit(Actor.nameOr("Administrator"), "Matching weights updated", "Configuration", 1, null, null);
            donations.rematchOpenDonations(null);
        });
    }

    public long addOrganisation(Map<String, String> form) {
        FormReader f = new FormReader(form);
        String name = f.required("name", "Name", 200);
        String role = f.oneOf("role", "Role", List.of(Organisation.DONOR, Organisation.RECIPIENT));
        List<String> types = Organisation.DONOR.equals(role) ? Catalog.DONOR_TYPES : Catalog.RECIPIENT_TYPES;
        String type = f.oneOf("organisationType", "Organisation type", types);
        String area = f.oneOf("area", "Area", List.copyOf(Catalog.AREAS.keySet()));
        String address = f.required("address", "Address", 255);
        String contact = f.text("contactName");
        if (!f.hasError("name") && repos.organisations().existsByName(name)) {
            f.error("name", "An organisation with this name already exists.");
        }
        f.throwIfErrors();
        double[] coordinates = Catalog.coordinatesOf(area);
        return repos.db().inTransaction(() -> {
            long id = repos.organisations().insert(name, role, type, area, address, coordinates[0], coordinates[1],
                contact.isEmpty() ? null : contact, Organisation.PENDING, LocalDateTime.now());
            events.audit(Actor.nameOr("Administrator"), "Organisation registered", "Organisation", id, null, Organisation.PENDING);
            events.notify(null, "admin", "Approval needed", name + " registered as a "
                + (Organisation.DONOR.equals(role) ? "donor" : "recipient") + " and is waiting for approval.");
            return id;
        });
    }

    public void approveOrganisation(long id) {
        Organisation organisation = repos.organisations().findById(id);
        if (organisation == null) {
            throw new NotFoundException("Organisation #" + id + " was not found.");
        }
        if (organisation.approved()) {
            return;
        }
        repos.db().inTransaction(() -> {
            repos.organisations().approve(id);
            events.audit(Actor.nameOr("Administrator"), "Organisation approved", "Organisation", id, Organisation.PENDING, Organisation.APPROVED);
            if (Organisation.RECIPIENT.equals(organisation.role())) {
                donations.rematchOpenDonations(null);
            }
        });
    }

    public long addTransporter(Map<String, String> form) {
        FormReader f = new FormReader(form);
        String name = f.required("name", "Driver name", 120);
        String phone = f.text("phone");
        String vehicle = f.required("vehicle", "Vehicle", 120);
        double capacity = f.number("capacityKg", "Capacity", 0, false);
        boolean refrigerated = f.checkbox("refrigerated");
        f.throwIfErrors();
        return repos.db().inTransaction(() -> {
            long id = repos.transporters().insert(name, phone.isEmpty() ? null : phone, vehicle, capacity, refrigerated,
                LocalDateTime.now());
            events.audit(Actor.nameOr("Administrator"), "Driver added", "Transporter", id, null, null);
            return id;
        });
    }

    public void setTransporterActive(long id, boolean active) {
        if (repos.transporters().findById(id) == null) {
            throw new NotFoundException("Driver #" + id + " was not found.");
        }
        repos.transporters().setActive(id, active);
        events.audit(Actor.nameOr("Administrator"), active ? "Driver activated" : "Driver deactivated", "Transporter", id, null, null);
    }

    // ------------------------------------------------------------------ recipient needs

    public long addNeed(Map<String, String> form) {
        FormReader f = new FormReader(form);
        long organisationId = f.id("organisationId", "Recipient organisation");
        String category = f.oneOf("category", "Category", Catalog.CATEGORIES);
        String description = f.required("description", "Description", 255);
        double needed = f.number("quantityNeeded", "Quantity needed", 0, false);
        double max = f.number("maxPerDonation", "Maximum per donation", 0, false);
        double capacity = f.number("storageCapacity", "Storage capacity", 0, true);
        String storageType = f.oneOf("storageType", "Storage type", Catalog.STORAGE_TYPES);
        String opens = f.time("opensAt", "Opening time");
        String closes = f.time("closesAt", "Closing time");
        String urgency = f.oneOf("urgency", "Urgency", Catalog.URGENCY_LEVELS);
        if (!f.hasError("opensAt") && !f.hasError("closesAt") && opens.compareTo(closes) >= 0) {
            f.error("closesAt", "Closing time must be after opening time.");
        }
        Organisation organisation = organisationId > 0 ? repos.organisations().findById(organisationId) : null;
        if (!f.hasError("organisationId") && (organisation == null || !Organisation.RECIPIENT.equals(organisation.role()))) {
            f.error("organisationId", "Choose a recipient organisation.");
        }
        f.throwIfErrors();
        return repos.db().inTransaction(() -> {
            long id = repos.needs().insert(organisationId, category, description, needed, max, capacity, storageType,
                opens, closes, urgency, LocalDateTime.now());
            events.audit(organisation.name(), "Need registered", "Need", id, null, null);
            donations.rematchOpenDonations(category);
            return id;
        });
    }

    public void setNeedActive(long id, boolean active) {
        var need = repos.needs().findById(id);
        if (need == null) {
            throw new NotFoundException("Need #" + id + " was not found.");
        }
        repos.db().inTransaction(() -> {
            repos.needs().setActive(id, active);
            events.audit(need.organisationName(), active ? "Need reopened" : "Need closed", "Need", id, null, null);
            donations.rematchOpenDonations(need.category());
        });
    }
}
