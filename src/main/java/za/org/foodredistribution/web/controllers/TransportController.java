package za.org.foodredistribution.web.controllers;

import za.org.foodredistribution.auth.Role;
import za.org.foodredistribution.model.Transporter;
import za.org.foodredistribution.model.TransportAssignment;
import za.org.foodredistribution.service.Repositories;
import za.org.foodredistribution.service.TransportService;
import za.org.foodredistribution.web.Model;
import za.org.foodredistribution.web.Response;
import za.org.foodredistribution.web.Router;
import za.org.foodredistribution.web.View;

public final class TransportController {

    private final Repositories repos;
    private final TransportService transport;
    private final View view;

    public TransportController(Repositories repos, TransportService transport, View view) {
        this.repos = repos;
        this.transport = transport;
        this.view = view;
    }

    public void register(Router router) {
        router.get("/transport", request -> {
            request.require(Role.ADMIN, Role.COORDINATOR);
            var trips = transport.assignments();
            return view.page(request, "Transport", "transport", "transport/list", Model.of(
                "waiting", transport.awaitingDriver(),
                "activeTrips", trips.stream().filter(TransportAssignment::active).toList(),
                "pastTrips", trips.stream().filter(t -> !t.active()).toList(),
                "driverOptions", Model.options(repos.transporters().findActive(), Transporter::id, Transporter::summary,
                    "")));
        });
        router.post("/transport/assign", request -> {
            request.require(Role.ADMIN, Role.COORDINATOR);
            transport.assign(request.form());
            return Response.success("/transport", "Driver assigned.");
        });
        router.post("/transport/{id}/status", request -> {
            request.require(Role.ADMIN, Role.COORDINATOR);
            transport.updateStatus(request.id("id"), request.field("status"));
            return Response.success("/transport", "Trip updated.");
        });
    }
}
