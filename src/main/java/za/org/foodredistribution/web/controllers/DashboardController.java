package za.org.foodredistribution.web.controllers;

import za.org.foodredistribution.model.DonationStatus;
import za.org.foodredistribution.service.Repositories;
import za.org.foodredistribution.service.ReportService;
import za.org.foodredistribution.web.Model;
import za.org.foodredistribution.web.Router;
import za.org.foodredistribution.web.View;

public final class DashboardController {

    private final Repositories repos;
    private final ReportService reports;
    private final View view;

    public DashboardController(Repositories repos, ReportService reports, View view) {
        this.repos = repos;
        this.reports = reports;
        this.view = view;
    }

    public void register(Router router) {
        router.get("/", request -> view.page(request, "Dashboard", "dashboard", "dashboard", Model.of(
            "summary", reports.summary(),
            "expiring", reports.expiringSoon().stream().limit(5).toList(),
            "awaitingDecision", repos.donations().findAll(DonationStatus.RECIPIENT_RECOMMENDED, null).stream().limit(5).toList(),
            "activity", repos.audit().findRecent(8))));
    }
}
