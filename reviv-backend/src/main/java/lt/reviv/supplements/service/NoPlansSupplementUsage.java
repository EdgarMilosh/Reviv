package lt.reviv.supplements.service;

@Deprecated
public class NoPlansSupplementUsage implements SupplementUsage {
    @Override
    public boolean isUsedInPlans(Long supplementId) {
        return false;
    }
}
