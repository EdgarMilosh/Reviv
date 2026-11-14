package lt.reviv.supplements.service;

import org.springframework.stereotype.Component;

/** Laikina realizacija: kol nėra vartojimo planų (US-11), joks papildas neturi plano įrašų. */
@Component
public class NoPlansSupplementUsage implements SupplementUsage {

    @Override
    public boolean isUsedInPlans(Long supplementId) {
        return false;
    }
}