package lt.reviv.bloodtests.service.plan;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lt.reviv.bloodtests.ai.plan.OpenAiPlanClient;
import lt.reviv.bloodtests.ai.plan.PlanProperties;
import lt.reviv.bloodtests.domain.BloodTestIndicator;
import lt.reviv.bloodtests.domain.plan.ConsumptionPlan;
import lt.reviv.bloodtests.domain.plan.PlanEntry;
import lt.reviv.bloodtests.domain.plan.PlanReminder;
import lt.reviv.bloodtests.domain.rec.RecommendationGeneration;
import lt.reviv.bloodtests.domain.rec.SupplementRecommendation;
import lt.reviv.bloodtests.repository.BloodTestIndicatorRepository;
import lt.reviv.bloodtests.repository.plan.ConsumptionPlanRepository;
import lt.reviv.bloodtests.repository.plan.PlanListRow;
import lt.reviv.bloodtests.repository.plan.PlanEntryRepository;
import lt.reviv.bloodtests.repository.plan.PlanReminderRepository;
import lt.reviv.bloodtests.repository.rec.RecommendationGenerationRepository;
import lt.reviv.bloodtests.repository.rec.SupplementRecommendationRepository;
import lt.reviv.bloodtests.service.analysis.IndicatorDeviationCalculator;
import lt.reviv.bloodtests.service.review.BloodTestReviewService;
import lt.reviv.supplements.domain.Supplement;
import lt.reviv.supplements.domain.SupplementInteraction;
import lt.reviv.supplements.repository.SupplementInteractionRepository;
import lt.reviv.supplements.repository.SupplementRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.LocalTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class ConsumptionPlanService {
    public record PlanEntryResult(Long id, Long recommendationId, Long supplementId, String supplementName,
                                  BigDecimal dailyDose, String doseUnit, BigDecimal catalogMaxDailyDose, PlanEntry.DayPart dayPart,
                                  PlanEntry.MealRelation mealRelation, LocalDate startDate, LocalDate endDate,
                                  Integer breakDays, String notes, LocalTime reminderTime, List<String> warnings) {}
    public record PlanResponse(Long id, Long bloodTestId, Long generationId, String status,
                               String aiModel, boolean medicallyVerified, List<PlanEntryResult> entries) {}
    public record PlanSummary(Long id, Long bloodTestId, String bloodTestFileName,
                              String laboratory, LocalDate bloodTestDate, String status,
                              OffsetDateTime createdAt, OffsetDateTime updatedAt,
                              LocalDate startDate, LocalDate endDate, List<String> supplements) {}
    public record EditRequest(@NotNull @Positive BigDecimal dailyDose,
                              @NotNull PlanEntry.DayPart dayPart,
                              @NotNull PlanEntry.MealRelation mealRelation,
                              @NotNull LocalDate startDate,
                              @NotNull LocalDate endDate,
                              Integer breakDays) {}

    private final BloodTestReviewService review;
    private final RecommendationGenerationRepository generations;
    private final SupplementRecommendationRepository recommendations;
    private final BloodTestIndicatorRepository indicators;
    private final SupplementRepository supplements;
    private final SupplementInteractionRepository interactions;
    private final ConsumptionPlanRepository plans;
    private final PlanEntryRepository entries;
    private final PlanReminderRepository reminders;
    private final IndicatorDeviationCalculator deviations;
    private final OpenAiPlanClient ai;
    private final PlanProperties properties;
    private final JsonMapper mapper;

    public ConsumptionPlanService(BloodTestReviewService review,
                                  RecommendationGenerationRepository generations,
                                  SupplementRecommendationRepository recommendations,
                                  BloodTestIndicatorRepository indicators,
                                  SupplementRepository supplements,
                                  SupplementInteractionRepository interactions,
                                  ConsumptionPlanRepository plans,
                                  PlanEntryRepository entries,
                                  PlanReminderRepository reminders,
                                  IndicatorDeviationCalculator deviations,
                                  OpenAiPlanClient ai, PlanProperties properties, JsonMapper mapper) {
        this.review = review;
        this.generations = generations;
        this.recommendations = recommendations;
        this.indicators = indicators;
        this.supplements = supplements;
        this.interactions = interactions;
        this.plans = plans;
        this.entries = entries;
        this.reminders = reminders;
        this.deviations = deviations;
        this.ai = ai;
        this.properties = properties;
        this.mapper = mapper;
    }

    @Transactional(readOnly = true)
    public PlanResponse latest(Long bloodTestId, Jwt jwt) {
        return response(currentPlan(bloodTestId, jwt));
    }

    /** Lists the authenticated user's historical and current plans; no AI call is needed. */
    @Transactional(readOnly = true)
    public List<PlanSummary> listMine(Jwt jwt) {
        List<PlanListRow> rows = plans.findAllForUser(jwt.getSubject());
        if (rows.isEmpty()) return List.of();
        Map<Long, List<PlanEntry>> grouped = entries.findByPlanIdInOrderByPlanIdAscIdAsc(
                rows.stream().map(PlanListRow::id).toList()).stream()
                .collect(Collectors.groupingBy(PlanEntry::getPlanId));
        return rows.stream().map(row -> {
            List<PlanEntry> items = grouped.getOrDefault(row.id(), List.of());
            LocalDate start = items.stream().map(PlanEntry::getStartDate)
                    .min(LocalDate::compareTo).orElse(null);
            LocalDate end = items.stream().map(PlanEntry::getEndDate)
                    .max(LocalDate::compareTo).orElse(null);
            List<String> names = items.stream().map(PlanEntry::getSupplementNameSnapshot)
                    .distinct().toList();
            return new PlanSummary(row.id(), row.bloodTestId(), row.bloodTestFileName(),
                    row.laboratory(), row.bloodTestDate(), row.status().name(),
                    row.createdAt(), row.updatedAt(), start, end, names);
        }).toList();
    }

    @Transactional(readOnly = true)
    public PlanResponse getByPlanId(Long planId, Jwt jwt) {
        return response(ownedPlan(planId, jwt));
    }

    private ConsumptionPlan ownedPlan(Long planId, Jwt jwt) {
        return plans.findOwnedById(planId, jwt.getSubject()).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Vartojimo planas nerastas."));
    }

    @Transactional
    public PlanResponse generate(Long bloodTestId, Jwt jwt) {
        RecommendationGeneration generation = currentGeneration(bloodTestId, jwt);
        var previous = plans.findByBloodTestIdAndRecommendationGenerationIdOrderByIdDesc(bloodTestId, generation.getId());
        if (!previous.isEmpty()) return response(previous.getFirst()); // Idempotent, no repeated API costs

        List<SupplementRecommendation> suggested = recommendations
                .findByGenerationIdOrderByIdAsc(generation.getId()).stream()
                .filter(r -> r.getRecommendationStatus() == SupplementRecommendation.RecommendationStatus.SUGGESTED)
                .toList();
        if (suggested.stream().anyMatch(r -> r.getDecision() == SupplementRecommendation.Decision.PENDING)) {
            throw conflict("Pirmiausia priimkite arba atmeskite visas US-9 pasiūlytas rekomendacijas.");
        }
        List<SupplementRecommendation> accepted = suggested.stream()
                .filter(r -> r.getDecision() == SupplementRecommendation.Decision.ACCEPTED).toList();
        if (accepted.isEmpty()) throw conflict("Plano sudarymui reikia bent vienos priimtos rekomendacijos.");
        // US-9 creates recommendations per indicator. Multiple indicators can refer to
        // the same catalog supplement: schedule it ONCE, never add its doses together.
        if (accepted.size() > 100) {
            throw conflict("Viename generavime per daug patvirtintų rekomendacijų.");
        }
        if (accepted.stream().anyMatch(r -> r.getSupplementId() == null)) {
            throw conflict("Patvirtintoje rekomendacijoje trūksta papildo ID.");
        }
        Map<Long, List<SupplementRecommendation>> bySupplement = accepted.stream()
                .collect(Collectors.groupingBy(SupplementRecommendation::getSupplementId,
                        LinkedHashMap::new, Collectors.toList()));
        if (bySupplement.size() > 20) {
            throw conflict("Viename plane leidžiama daugiausia 20 skirtingų papildų.");
        }

        Map<Long, BloodTestIndicator> indicatorById = indicators.findByBloodTestIdOrderByIdAsc(bloodTestId)
                .stream().collect(Collectors.toMap(BloodTestIndicator::getId, Function.identity()));
        Map<Long, Supplement> supplementById = supplements.findAllById(bySupplement.keySet())
                .stream().collect(Collectors.toMap(Supplement::getId, Function.identity()));
        List<OpenAiPlanClient.AcceptedInput> aiInputs = new ArrayList<>();
        Set<Long> uniqueSupplements = bySupplement.keySet();
        for (Map.Entry<Long, List<SupplementRecommendation>> grouped : bySupplement.entrySet()) {
            Supplement supplement = supplementById.get(grouped.getKey());
            if (supplement == null) {
                throw conflict("Patvirtintos rekomendacijos papildas nerastas kataloge.");
            }
            SupplementRecommendation primary = grouped.getValue().getFirst();
            validateDose(primary.getProposedDailyDose(), primary.getDoseUnit(), supplement);
            if (primary.getDurationDays() == null || primary.getDurationDays() < 1
                    || primary.getDurationDays() > 365) {
                throw conflict("Papildo „" + supplement.getName() + "“ vartojimo trukmė nenustatyta. Reikalingas specialisto įvertinimas.");
            }
            List<OpenAiPlanClient.IndicatorInput> relatedIndicators = new ArrayList<>();
            List<String> allWarnings = new ArrayList<>();
            for (SupplementRecommendation rec : grouped.getValue()) {
                // If the AI recommendation engine suggested different amounts for the same
                // supplement, do not guess which dose or course is appropriate.
                if (rec.getProposedDailyDose() == null
                        || rec.getProposedDailyDose().compareTo(primary.getProposedDailyDose()) != 0
                        || rec.getDoseUnit() == null
                        || !rec.getDoseUnit().equalsIgnoreCase(primary.getDoseUnit())
                        || !Objects.equals(rec.getDurationDays(), primary.getDurationDays())) {
                    throw conflict("Papildas „" + supplement.getName()
                            + "“ rekomenduotas su skirtingomis dozėmis arba trukmėmis. Reikia peržiūrėti rekomendacijas.");
                }
                BloodTestIndicator indicator = indicatorById.get(rec.getIndicatorId());
                if (indicator == null) {
                    throw conflict("Patvirtintos rekomendacijos kraujo rodiklis nerastas.");
                }
                var deviation = deviations.calculate(indicator.getRawValue(), indicator.getReferenceInterval());
                if (deviation.status() != IndicatorDeviationCalculator.Status.BELOW) {
                    throw conflict("Rodiklio „" + indicator.getName() + "“ nukrypimo duomenų nepavyko patikrinti.");
                }
                relatedIndicators.add(new OpenAiPlanClient.IndicatorInput(indicator.getId(), indicator.getName(),
                        indicator.getRawValue(), indicator.getUnit(), indicator.getReferenceInterval(),
                        "BELOW_RANGE", deviation.percentage()));
                allWarnings.addAll(parseWarnings(rec.getRiskWarningsJson()));
            }
            aiInputs.add(new OpenAiPlanClient.AcceptedInput(
                    supplement.getId(), supplement.getName(), supplement.getActiveSubstance(),
                    primary.getProposedDailyDose(), supplement.getDoseUnit(), primary.getDurationDays(),
                    relatedIndicators, allWarnings.stream().distinct().toList()));
        }

        List<SupplementInteraction> relevant = interactions.findAll().stream()
                .filter(i -> uniqueSupplements.contains(i.getSupplementAId()) &&
                             uniqueSupplements.contains(i.getSupplementBId())).toList();
        List<OpenAiPlanClient.InteractionInput> interactionInputs = relevant.stream()
                .map(i -> new OpenAiPlanClient.InteractionInput(i.getSupplementAId(),
                        i.getSupplementBId(), i.getType(), i.getRecommendation())).toList();

        List<OpenAiPlanClient.Suggestion> proposals = ai.suggest(aiInputs, interactionInputs);
        Map<Long, OpenAiPlanClient.Suggestion> suggestions = proposals.stream()
                .collect(Collectors.toMap(OpenAiPlanClient.Suggestion::supplementId, Function.identity()));
        Map<Long, PlanEntry.DayPart> times = spreadInteractions(proposals, relevant);
        ConsumptionPlan plan = plans.saveAndFlush(new ConsumptionPlan(bloodTestId, generation.getId(), properties.model()));
        LocalDate start = LocalDate.now();
        for (Map.Entry<Long, List<SupplementRecommendation>> grouped : bySupplement.entrySet()) {
            SupplementRecommendation acceptedItem = grouped.getValue().getFirst();
            OpenAiPlanClient.Suggestion proposal = suggestions.get(grouped.getKey());
            if (proposal == null) throw new IllegalStateException("DI nepateikė visų plano elementų.");
            String notes = proposal.notes() == null ? "" : proposal.notes().trim();
            if (grouped.getValue().size() > 1) {
                String context = "Papildas susietas su " + grouped.getValue().size()
                        + " rodikliais; paros dozė nedauginama.";
                notes = (notes.isBlank() ? context : notes + " " + context);
            }
            if (notes.length() > 800) notes = notes.substring(0, 800);
            PlanEntry entry = new PlanEntry(plan.getId(), acceptedItem.getId(), grouped.getKey(),
                    supplementById.get(grouped.getKey()).getName(), acceptedItem.getProposedDailyDose(),
                    acceptedItem.getDoseUnit(), times.get(grouped.getKey()), proposal.mealRelation(),
                    start, start.plusDays(acceptedItem.getDurationDays() - 1L), proposal.breakDays(), notes);
            entries.save(entry);
        }
        return response(plan);
    }

    @Transactional
    public PlanResponse update(Long bloodTestId, Long entryId, EditRequest request, Jwt jwt) {
        return updatePlan(currentPlan(bloodTestId, jwt), entryId, request);
    }

    @Transactional
    public PlanResponse updateByPlanId(Long planId, Long entryId, EditRequest request, Jwt jwt) {
        return updatePlan(ownedPlan(planId, jwt), entryId, request);
    }

    private PlanResponse updatePlan(ConsumptionPlan plan, Long entryId, EditRequest request) {
        PlanEntry entry = entries.findByIdAndPlanId(entryId, plan.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Plano įrašas nerastas."));
        Supplement supplement = supplements.findById(entry.getSupplementId())
                .orElseThrow(() -> conflict("Papildas nerastas kataloge."));
        validateDose(request.dailyDose(), entry.getDoseUnit(), supplement);
        if (request.startDate() == null || request.endDate() == null ||
                request.endDate().isBefore(request.startDate()) ||
                request.endDate().isAfter(request.startDate().plusDays(364)) ||
                request.startDate().isBefore(LocalDate.now().minusDays(1)) ||
                request.startDate().isAfter(LocalDate.now().plusDays(365))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Neleistinos ciklo datos (1–365 dienos).");
        }
        if (request.breakDays() != null && (request.breakDays() < 0 || request.breakDays() > 365)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Pertrauka turi būti 0–365 dienų.");
        }
        for (PlanEntry other : entries.findByPlanIdOrderByIdAsc(plan.getId())) {
            if (other.getId().equals(entry.getId()) || other.getDayPart() != request.dayPart()) continue;
            boolean datesOverlap = !request.startDate().isAfter(other.getEndDate())
                    && !request.endDate().isBefore(other.getStartDate());
            if (datesOverlap && interact(entry.getSupplementId(), other.getSupplementId())) {
                throw conflict("Sąveikaujančių papildų negalima planuoti tuo pačiu paros metu.");
            }
        }
        entry.update(request.dailyDose(), request.dayPart(), request.mealRelation(),
                request.startDate(), request.endDate(), request.breakDays(), entry.getNotes());
        entries.save(entry);
        plan.touch();
        plans.save(plan);
        if (plan.getStatus() == ConsumptionPlan.Status.ACTIVE) {
            syncReminder(entry);
        }
        return response(plan);
    }

    @Transactional
    public PlanResponse activate(Long bloodTestId, Jwt jwt) {
        return activatePlan(currentPlan(bloodTestId, jwt));
    }

    @Transactional
    public PlanResponse activateByPlanId(Long planId, Jwt jwt) {
        return activatePlan(ownedPlan(planId, jwt));
    }

    private PlanResponse activatePlan(ConsumptionPlan plan) {
        List<PlanEntry> items = entries.findByPlanIdOrderByIdAsc(plan.getId());
        if (items.isEmpty()) throw conflict("Tuščio plano aktyvuoti negalima.");
        for (int i = 0; i < items.size(); i++) {
            PlanEntry a = items.get(i);
            Supplement supplement = supplements.findById(a.getSupplementId()).orElseThrow();
            validateDose(a.getDailyDose(), a.getDoseUnit(), supplement);
            for (int j = i + 1; j < items.size(); j++) {
                PlanEntry b = items.get(j);
                if (a.getDayPart() == b.getDayPart()
                        && !a.getStartDate().isAfter(b.getEndDate())
                        && !a.getEndDate().isBefore(b.getStartDate())
                        && interact(a.getSupplementId(), b.getSupplementId())) {
                    throw conflict("Plane yra tuo pačiu metu numatytų sąveikaujančių papildų.");
                }
            }
            syncReminder(a);
        }
        plan.activate();
        plans.save(plan);
        return response(plan);
    }

    private RecommendationGeneration currentGeneration(Long bloodTestId, Jwt jwt) {
        if (!"CONFIRMED".equals(review.getDetails(bloodTestId, jwt).status())) {
            throw conflict("Tyrimas dar nepatvirtintas.");
        }
        return generations.findByBloodTestIdOrderByCreatedAtDescIdDesc(bloodTestId).stream()
                .findFirst().orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Pirmiausia sugeneruokite US-9 rekomendacijas."));
    }

    private ConsumptionPlan currentPlan(Long bloodTestId, Jwt jwt) {
        RecommendationGeneration generation = currentGeneration(bloodTestId, jwt);
        return plans.findByBloodTestIdAndRecommendationGenerationIdOrderByIdDesc(bloodTestId, generation.getId())
                .stream().findFirst().orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Šioms rekomendacijoms vartojimo planas dar nesudarytas."));
    }

    private PlanResponse response(ConsumptionPlan plan) {
        // Keep the warnings from ALL accepted indicators, even though the plan has
        // only one entry for their shared supplement.
        Map<Long, List<String>> warningsBySupplement = new HashMap<>();
        for (SupplementRecommendation rec : recommendations.findByGenerationIdOrderByIdAsc(
                plan.getRecommendationGenerationId())) {
            if (rec.getDecision() == SupplementRecommendation.Decision.ACCEPTED && rec.getSupplementId() != null) {
                warningsBySupplement.computeIfAbsent(rec.getSupplementId(), id -> new ArrayList<>())
                        .addAll(parseWarnings(rec.getRiskWarningsJson()));
            }
        }
        List<PlanEntryResult> results = entries.findByPlanIdOrderByIdAsc(plan.getId()).stream()
                .map(entry -> new PlanEntryResult(entry.getId(), entry.getRecommendationId(),
                        entry.getSupplementId(), entry.getSupplementNameSnapshot(), entry.getDailyDose(),
                        entry.getDoseUnit(), supplements.findById(entry.getSupplementId())
                                .map(Supplement::getMaxDailyDose).orElse(null),
                        entry.getDayPart(), entry.getMealRelation(), entry.getStartDate(),
                        entry.getEndDate(), entry.getBreakDays(), entry.getNotes(),
                        plan.getStatus() == ConsumptionPlan.Status.ACTIVE ?
                                reminders.findByPlanEntryId(entry.getId())
                                        .map(PlanReminder::getReminderTime).orElse(null) : null,
                        warningsFor(entry, warningsBySupplement.getOrDefault(entry.getSupplementId(), List.of()))))
                .toList();
        return new PlanResponse(plan.getId(), plan.getBloodTestId(),
                plan.getRecommendationGenerationId(), plan.getStatus().name(),
                plan.getAiModel(), false, results);
    }

    private List<String> warningsFor(PlanEntry entry, List<String> relatedWarnings) {
        List<String> list = new ArrayList<>(relatedWarnings);
        Set<Long> plannedSupplementIds = entries.findByPlanIdOrderByIdAsc(entry.getPlanId()).stream()
                .map(PlanEntry::getSupplementId).collect(Collectors.toSet());
        for (SupplementInteraction i : interactions.findBySupplementAIdOrSupplementBId(
                entry.getSupplementId(), entry.getSupplementId())) {
            if (plannedSupplementIds.contains(i.otherSupplementId(entry.getSupplementId()))) {
                list.add("Sąveika kataloge: " + i.getRecommendation());
            }
        }
        return list.stream().distinct().toList();
    }

    private List<String> parseWarnings(String json) {
        if (json == null || json.isBlank()) return List.of();
        try {
            List<String> values = mapper.readValue(json, new TypeReference<List<String>>() {});
            return values == null ? List.of() : values;
        } catch (JacksonException ex) {
            throw new IllegalStateException("Netinkamas saugotų įspėjimų JSON.", ex);
        }
    }

    private void validateDose(BigDecimal dose, String unit, Supplement supplement) {
        if (dose == null || dose.signum() <= 0 || dose.stripTrailingZeros().scale() > 4 || unit == null ||
                !unit.equalsIgnoreCase(supplement.getDoseUnit()) ||
                dose.compareTo(supplement.getMaxDailyDose()) > 0) {
            throw conflict("Papildo „" + supplement.getName() + "“ dozė nėra nustatyta, nesutampa vienetai arba viršyta katalogo paros riba.");
        }
    }

    private boolean interact(Long first, Long second) {
        long a = Math.min(first, second), b = Math.max(first, second);
        return interactions.findBySupplementAIdAndSupplementBId(a, b).isPresent();
    }

    private Map<Long, PlanEntry.DayPart> spreadInteractions(List<OpenAiPlanClient.Suggestion> proposals,
                                                             List<SupplementInteraction> relevant) {
        Map<Long, PlanEntry.DayPart> assigned = new HashMap<>();
        for (OpenAiPlanClient.Suggestion proposal : proposals) {
            List<PlanEntry.DayPart> options = new ArrayList<>();
            options.add(proposal.dayPart());
            for (PlanEntry.DayPart part : PlanEntry.DayPart.values()) {
                if (!options.contains(part)) options.add(part);
            }
            PlanEntry.DayPart selected = options.stream().filter(option ->
                    relevant.stream().filter(interaction ->
                            interaction.getSupplementAId().equals(proposal.supplementId()) ||
                            interaction.getSupplementBId().equals(proposal.supplementId()))
                            .noneMatch(interaction -> option.equals(assigned.get(
                                    interaction.otherSupplementId(proposal.supplementId())))))
                    .findFirst().orElse(null);
            if (selected == null) {
                throw conflict("Papildų sąveikų nepavyksta saugiai išdėstyti į tris paros dalis. Reikalinga specialisto peržiūra.");
            }
            assigned.put(proposal.supplementId(), selected);
        }
        return assigned;
    }

    private void syncReminder(PlanEntry entry) {
        LocalTime time = switch (entry.getDayPart()) {
            case MORNING -> LocalTime.of(8, 0);
            case NOON -> LocalTime.of(13, 0);
            case EVENING -> LocalTime.of(19, 0);
        };
        var existing = reminders.findByPlanEntryId(entry.getId());
        if (existing.isPresent()) {
            PlanReminder reminder = existing.get();
            reminder.reschedule(time);
            reminders.save(reminder);
        } else {
            reminders.save(new PlanReminder(entry.getId(), time));
        }
    }

    private ResponseStatusException conflict(String message) {
        return new ResponseStatusException(HttpStatus.CONFLICT, message);
    }
}
