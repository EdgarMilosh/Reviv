package lt.reviv.bloodtests.service.plan;

import lt.reviv.bloodtests.repository.plan.PlanEntryRepository;
import lt.reviv.supplements.service.SupplementUsage;
import org.springframework.stereotype.Component;

@Component
public class DatabaseSupplementUsage implements SupplementUsage {
    private final PlanEntryRepository entries;
    public DatabaseSupplementUsage(PlanEntryRepository entries) { this.entries = entries; }
    @Override public boolean isUsedInPlans(Long supplementId) {
        return entries.existsBySupplementId(supplementId);
    }
}
