package lt.reviv.supplements.dto;

import lt.reviv.supplements.domain.SupplementForm;
import lt.reviv.supplements.domain.SupplementStatus;

import java.math.BigDecimal;
import java.util.List;

public record SupplementResponse(
        Long id,
        String name,
        String activeSubstance,
        SupplementForm form,
        BigDecimal doseAmount,
        String doseUnit,
        BigDecimal maxDailyDose,
        String missedDoseRule,
        SupplementStatus status,
        List<DistributorResponse> distributors,
        List<InteractionResponse> interactions
) {
}