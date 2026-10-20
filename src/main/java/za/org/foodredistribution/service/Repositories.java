package za.org.foodredistribution.service;

import za.org.foodredistribution.db.Database;
import za.org.foodredistribution.repository.AssignmentRepository;
import za.org.foodredistribution.repository.AuditRepository;
import za.org.foodredistribution.repository.ConfigRepository;
import za.org.foodredistribution.repository.DonationRepository;
import za.org.foodredistribution.repository.MatchRepository;
import za.org.foodredistribution.repository.NeedRepository;
import za.org.foodredistribution.repository.NotificationRepository;
import za.org.foodredistribution.repository.OrganisationRepository;
import za.org.foodredistribution.repository.TransporterRepository;

/** All repositories in one place, created once at start-up. */
public record Repositories(
    Database db,
    OrganisationRepository organisations,
    TransporterRepository transporters,
    DonationRepository donations,
    NeedRepository needs,
    MatchRepository matches,
    AssignmentRepository assignments,
    NotificationRepository notifications,
    AuditRepository audit,
    ConfigRepository config
) {
    public static Repositories create(Database db) {
        return new Repositories(db,
            new OrganisationRepository(db),
            new TransporterRepository(db),
            new DonationRepository(db),
            new NeedRepository(db),
            new MatchRepository(db),
            new AssignmentRepository(db),
            new NotificationRepository(db),
            new AuditRepository(db),
            new ConfigRepository(db));
    }
}
