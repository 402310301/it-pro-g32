package za.org.foodredistribution.web.controllers;

import za.org.foodredistribution.auth.Role;
import za.org.foodredistribution.service.ReportService;
import za.org.foodredistribution.web.Model;
import za.org.foodredistribution.web.Router;
import za.org.foodredistribution.web.View;

public final class ReportController {

    private final ReportService reports;
    private final View view;

    public ReportController(ReportService reports, View view) {
        this.reports = reports;
        this.view = view;
    }

    public void register(Router router) {
        router.get("/reports", request -> {
            request.require(Role.ADMIN, Role.COORDINATOR);
            return view.page(request, "Impact report", "reports", "reports/impact", Model.of(
                "impact", reports.impact(),
                "comparison", reports.comparison()));
        });
    }
}
