package lt.reviv.bloodtests.repository.plan;

import lt.reviv.bloodtests.domain.plan.ConsumptionPlan;
import java.time.LocalDate;
import java.time.OffsetDateTime;

/** Lightweight plan + blood-test metadata for the authenticated user's plan list. */
public record PlanListRow(Long id, Long bloodTestId, ConsumptionPlan.Status status,
                          OffsetDateTime createdAt, OffsetDateTime updatedAt,
                          String bloodTestFileName, String laboratory, LocalDate bloodTestDate) {}
