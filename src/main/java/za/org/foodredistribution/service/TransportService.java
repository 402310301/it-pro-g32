package za.org.foodredistribution.service;

import za.org.foodredistribution.auth.Actor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import za.org.foodredistribution.model.Donation;
import za.org.foodredistribution.model.DonationStatus;
import za.org.foodredistribution.model.Transporter;
import za.org.foodredistribution.model.TransportAssignment;

/** Volunteer transport: assigning drivers and tracking pickup and delivery. */
public class TransportService {

    private final Repositories repos;
    private final DonationService donations;
    private final EventLog events;

    public TransportService(Repositories repos, DonationService donations, EventLog events) {
        this.repos = repos;
        this.donations = donations;
        this.events = events;
    }

    public List<TransportAssignment> assignments() {
        return repos.assignments().findAll();
    }

    /** Donations accepted by a recipient that still need a driver. */
    public List<Donation> awaitingDriver() {
        return repos.donations().findAll(DonationStatus.TRANSPORT_REQUIRED, null);
    }

    public void assign(Map<String, String> form) {
        FormReader f = new FormReader(form);
        long donationId = f.id("donationId", "Donation");
        long transporterId = f.id("transporterId", "Driver");
        String window = f.required("pickupWindow", "Pickup window", 80);
        f.throwFirstError();

        Donation donation = donations.get(donationId);
        Transporter transporter = repos.transporters().findById(transporterId);
        if (!DonationStatus.TRANSPORT_REQUIRED.equals(donation.status())) {
            throw new ValidationException(donation.reference() + " is not waiting for a driver.");
        }
        if (transporter == null || !transporter.active()) {
            throw new ValidationException("Choose an active driver.");
        }
        if (donation.unit().equals("kg") && transporter.capacityKg() < donation.quantityAllocated()) {
            throw new ValidationException(transporter.name() + "'s vehicle carries " + transporter.capacityLabel()
                + ", but " + donation.allocatedLabel() + " must be moved.");
        }
        boolean needsCold = donation.storageRequirement().equals("Refrigerated") || donation.storageRequirement().equals("Frozen");
        if (needsCold && !transporter.refrigerated()) {
            throw new ValidationException(donation.reference() + " must stay cold; choose a driver with a refrigerated vehicle.");
        }

        repos.db().inTransaction(() -> {
            LocalDateTime now = LocalDateTime.now();
            long id = repos.assignments().insert(donationId, transporterId, window, TransportAssignment.OFFERED, now);
            donations.changeStatus(donation, DonationStatus.TRANSPORT_ASSIGNED, Actor.nameOr(DonationService.COORDINATOR), "Transport assigned");
            events.audit(Actor.nameOr(DonationService.COORDINATOR), "Driver offered trip", "Transport", id, null, TransportAssignment.label(TransportAssignment.OFFERED));
            events.notify(donationId, "transport", "Driver assigned",
                transporter.name() + " will collect " + donation.reference() + " (" + window + ").");
        });
    }

    public void updateStatus(long assignmentId, String newStatus) {
        TransportAssignment assignment = repos.assignments().findById(assignmentId);
        if (assignment == null) {
            throw new NotFoundException("Transport assignment #" + assignmentId + " was not found.");
        }
        boolean allowed = assignment.nextSteps().stream().anyMatch(step -> step.value().equals(newStatus));
        if (!allowed) {
            throw new ValidationException("A trip that is '" + assignment.statusLabel() + "' cannot change to '"
                + TransportAssignment.label(newStatus) + "'.");
        }
        repos.db().inTransaction(() -> {
            repos.assignments().updateStatus(assignmentId, newStatus, LocalDateTime.now());
            events.audit(assignment.transporterName(), "Trip " + TransportAssignment.label(newStatus).toLowerCase(),
                "Transport", assignmentId, assignment.statusLabel(), TransportAssignment.label(newStatus));
            Donation donation = donations.get(assignment.donationId());
            switch (newStatus) {
                case TransportAssignment.PICKED_UP ->
                    donations.changeStatus(donation, DonationStatus.COLLECTED, assignment.transporterName(), "Pickup confirmed");
                case TransportAssignment.DELIVERED -> {
                    donations.changeStatus(donation, DonationStatus.COMPLETED, assignment.transporterName(), "Delivery confirmed");
                    events.notify(donation.id(), "delivery", "Delivery confirmed",
                        donation.recipientName() + " received " + donation.allocatedLabel() + " from "
                            + donation.donorName() + ".");
                }
                case TransportAssignment.CANCELLED -> {
                    donations.changeStatus(donation, DonationStatus.TRANSPORT_REQUIRED, Actor.nameOr(DonationService.COORDINATOR), "Trip cancelled");
                    events.notify(donation.id(), "transport", "Driver needed again",
                        assignment.transporterName() + " can no longer collect " + donation.reference()
                            + ". Please assign another driver.");
                }
                default -> {
                    // ACCEPTED: the donation stays "Transport Assigned"
                }
            }
        });
    }
}
