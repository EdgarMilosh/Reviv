package lt.reviv.doses.service;

import lt.reviv.bloodtests.domain.plan.ConsumptionPlan;
import lt.reviv.bloodtests.domain.plan.PlanEntry;
import lt.reviv.bloodtests.domain.plan.PlanReminder;
import lt.reviv.bloodtests.repository.plan.ConsumptionPlanRepository;
import lt.reviv.bloodtests.repository.plan.PlanEntryRepository;
import lt.reviv.bloodtests.repository.plan.PlanReminderRepository;
import lt.reviv.doses.config.AdherenceProperties;
import lt.reviv.doses.domain.DoseLog;
import lt.reviv.doses.dto.DaySchedule;
import lt.reviv.doses.dto.DoseState;
import lt.reviv.doses.dto.EntryHistory;
import lt.reviv.doses.dto.GuidanceView;
import lt.reviv.doses.dto.HistorySummary;
import lt.reviv.doses.dto.MarkResponse;
import lt.reviv.doses.dto.ScheduledDose;
import lt.reviv.doses.repository.DoseLogRepository;
import lt.reviv.supplements.domain.Supplement;
import lt.reviv.supplements.repository.SupplementRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/** DP8 - plan management component: daily schedule, dose marks, guidance and history (US-13). */
@Service
public class DoseLogService {

    private static final Map<PlanEntry.DayPart, LocalTime> DEFAULT_TIMES = Map.of(
            PlanEntry.DayPart.MORNING, LocalTime.of(8, 0),
            PlanEntry.DayPart.NOON, LocalTime.of(13, 0),
            PlanEntry.DayPart.EVENING, LocalTime.of(19, 0));
    private static final long MAX_PERIOD_DAYS = 366;

    private final ConsumptionPlanRepository plans;
    private final PlanEntryRepository entries;
    private final PlanReminderRepository reminders;
    private final DoseLogRepository logs;
    private final SupplementRepository supplements;
    private final AdherenceProperties properties;
    private final Clock clock;

    public DoseLogService(ConsumptionPlanRepository plans,
                          PlanEntryRepository entries,
                          PlanReminderRepository reminders,
                          DoseLogRepository logs,
                          SupplementRepository supplements,
                          AdherenceProperties properties,
                          Clock clock) {
        this.plans = plans;
        this.entries = entries;
        this.reminders = reminders;
        this.logs = logs;
        this.supplements = supplements;
        this.properties = properties;
        this.clock = clock;
    }

    /** The doses planned for a day (today by default) with their marks. */
    @Transactional(readOnly = true)
    public DaySchedule schedule(Long planId, LocalDate date, Jwt jwt) {
        ConsumptionPlan plan = ownedPlan(planId, jwt);
        LocalDate day = date == null ? today() : date;
        if (plan.getStatus() != ConsumptionPlan.Status.ACTIVE) {
            return new DaySchedule(day, planId, plan.getStatus().name(), List.of());
        }
        List<PlanEntry> onDay = entries.findByPlanIdOrderByIdAsc(planId).stream()
                .filter(entry -> covers(entry, day))
                .toList();
        Map<Long, DoseLog> marks = marksFor(onDay, day, day);
        List<ScheduledDose> doses = onDay.stream()
                .map(entry -> toDose(entry, day, marks.get(entry.getId())))
                .sorted(Comparator.comparing(ScheduledDose::time).thenComparing(ScheduledDose::supplementName))
                .toList();
        return new DaySchedule(day, planId, plan.getStatus().name(), doses);
    }

    /** Marks a dose as taken or skipped (a repeated mark replaces the previous one). Only today can be marked. */
    @Transactional
    public MarkResponse mark(Long planId, Long entryId, LocalDate date, DoseLog.Status status, Jwt jwt) {
        ConsumptionPlan plan = ownedPlan(planId, jwt);
        PlanEntry entry = entry(planId, entryId);
        if (plan.getStatus() != ConsumptionPlan.Status.ACTIVE) {
            throw error(HttpStatus.CONFLICT, "Dozes galima žymėti tik aktyviame vartojimo plane.");
        }
        if (!date.equals(today())) {
            throw error(HttpStatus.BAD_REQUEST, "Dozę galima pažymėti tik šiandien.");
        }
        if (!covers(entry, date)) {
            throw error(HttpStatus.BAD_REQUEST, "Šiai dienai šis papildas plane nenumatytas.");
        }
        OffsetDateTime now = OffsetDateTime.now(clock);
        DoseLog mark = logs.findByPlanEntryIdAndDoseDate(entryId, date)
                .map(existing -> {
                    existing.change(status, now);
                    return existing;
                })
                .orElseGet(() -> new DoseLog(entryId, date, status, now));
        logs.save(mark);

        GuidanceView guidance = null;
        if (status == DoseLog.Status.SKIPPED) {
            MissedDoseGuidance.Advice advice = MissedDoseGuidance.advise(LocalDateTime.now(clock), timeOf(entry));
            String rule = supplements.findById(entry.getSupplementId())
                    .map(Supplement::getMissedDoseRule)
                    .orElse(null);
            guidance = new GuidanceView(advice.action().name(), advice.message(), rule);
        }
        return new MarkResponse(toDose(entry, date, mark), guidance);
    }

    /** Cancels a mark; only possible on the same day (NI US-13, criterion 5). */
    @Transactional
    public void cancel(Long planId, Long entryId, LocalDate date, Jwt jwt) {
        ownedPlan(planId, jwt);
        entry(planId, entryId);
        if (!date.equals(today())) {
            throw error(HttpStatus.CONFLICT, "Žymėjimą galima atšaukti tik tą pačią dieną.");
        }
        if (logs.deleteByPlanEntryIdAndDoseDate(entryId, date) == 0) {
            throw error(HttpStatus.NOT_FOUND, "Šios dozės žymėjimo nėra.");
        }
    }

    /** Counts of taken, skipped and unmarked doses with the adherence percentage for a period. */
    @Transactional(readOnly = true)
    public HistorySummary history(Long planId, LocalDate from, LocalDate to, Jwt jwt) {
        ConsumptionPlan plan = ownedPlan(planId, jwt);
        LocalDate today = today();
        List<PlanEntry> all = entries.findByPlanIdOrderByIdAsc(planId);
        LocalDate periodFrom = from != null ? from
                : all.stream().map(PlanEntry::getStartDate).min(Comparator.naturalOrder()).orElse(today);
        LocalDate periodTo = to != null ? to : today;
        if (periodFrom.isAfter(periodTo)) {
            throw error(HttpStatus.BAD_REQUEST, "Laikotarpio pradžia negali būti vėlesnė už pabaigą.");
        }
        if (ChronoUnit.DAYS.between(periodFrom, periodTo) > MAX_PERIOD_DAYS) {
            throw error(HttpStatus.BAD_REQUEST, "Laikotarpis negali būti ilgesnis nei 366 dienos.");
        }
        if (plan.getStatus() != ConsumptionPlan.Status.ACTIVE || all.isEmpty()) {
            return new HistorySummary(planId, periodFrom, periodTo, 0, 0, 0, 0, null, false, false, null, List.of());
        }

        List<Long> ids = all.stream().map(PlanEntry::getId).toList();
        List<DoseLog> periodLogs = logs.findByPlanEntryIdInAndDoseDateBetween(ids, periodFrom, periodTo);
        List<EntryHistory> perEntry = new ArrayList<>();
        long planned = 0;
        long taken = 0;
        long skipped = 0;
        for (PlanEntry entry : all) {
            List<DoseLog> own = periodLogs.stream()
                    .filter(log -> log.getPlanEntryId().equals(entry.getId())
                            && covers(entry, log.getDoseDate()) && !log.getDoseDate().isAfter(today))
                    .toList();
            long entryTaken = own.stream().filter(log -> log.getStatus() == DoseLog.Status.TAKEN).count();
            long entrySkipped = own.size() - entryTaken;
            long entryPlanned = AdherenceCalculator.plannedDoses(
                    entry.getStartDate(), entry.getEndDate(), periodFrom, periodTo, today.minusDays(1));
            boolean todayMarked = own.stream().anyMatch(log -> log.getDoseDate().equals(today));
            if (todayMarked) {
                entryPlanned++;
            }
            long entryUnmarked = Math.max(0, entryPlanned - entryTaken - entrySkipped);
            perEntry.add(new EntryHistory(entry.getId(), entry.getSupplementNameSnapshot(), entryPlanned,
                    entryTaken, entrySkipped, entryUnmarked, AdherenceCalculator.percent(entryTaken, entryPlanned)));
            planned += entryPlanned;
            taken += entryTaken;
            skipped += entrySkipped;
        }

        boolean cycleEnded = all.stream().allMatch(entry -> entry.getEndDate().isBefore(today));
        boolean notFollowed = false;
        String notice = null;
        if (cycleEnded) {
            notFollowed = cycleNotFollowed(all, ids, today);
            if (notFollowed) {
                notice = "Ciklo metu praleista daugiau nei " + properties.missedThresholdPercent()
                        + " % dozių, todėl plano nebuvo laikomasi ir rezultatai gali būti nepasiekti.";
            }
        }
        return new HistorySummary(planId, periodFrom, periodTo, planned, taken, skipped,
                Math.max(0, planned - taken - skipped), AdherenceCalculator.percent(taken, planned),
                cycleEnded, notFollowed, notice, perEntry);
    }

    /** Over the whole (finished) cycle: were more than the configured share of the planned doses not taken? */
    private boolean cycleNotFollowed(List<PlanEntry> all, List<Long> ids, LocalDate today) {
        LocalDate first = all.stream().map(PlanEntry::getStartDate).min(Comparator.naturalOrder()).orElse(today);
        LocalDate last = all.stream().map(PlanEntry::getEndDate).max(Comparator.naturalOrder()).orElse(today);
        List<DoseLog> cycleLogs = logs.findByPlanEntryIdInAndDoseDateBetween(ids, first, last);
        long planned = 0;
        long taken = 0;
        for (PlanEntry entry : all) {
            planned += AdherenceCalculator.plannedDoses(entry.getStartDate(), entry.getEndDate(),
                    entry.getStartDate(), entry.getEndDate(), today.minusDays(1));
            taken += cycleLogs.stream()
                    .filter(log -> log.getPlanEntryId().equals(entry.getId())
                            && log.getStatus() == DoseLog.Status.TAKEN && covers(entry, log.getDoseDate()))
                    .count();
        }
        return AdherenceCalculator.notFollowed(planned, taken, properties.missedThresholdPercent());
    }

    private Map<Long, DoseLog> marksFor(List<PlanEntry> onDay, LocalDate from, LocalDate to) {
        if (onDay.isEmpty()) {
            return Map.of();
        }
        List<Long> ids = onDay.stream().map(PlanEntry::getId).toList();
        return logs.findByPlanEntryIdInAndDoseDateBetween(ids, from, to).stream()
                .collect(Collectors.toMap(DoseLog::getPlanEntryId, Function.identity(), (a, b) -> a));
    }

    private ScheduledDose toDose(PlanEntry entry, LocalDate day, DoseLog mark) {
        DoseState state = mark == null ? DoseState.PENDING
                : mark.getStatus() == DoseLog.Status.TAKEN ? DoseState.TAKEN : DoseState.SKIPPED;
        return new ScheduledDose(entry.getId(), entry.getSupplementNameSnapshot(), entry.getDailyDose(),
                entry.getDoseUnit(), entry.getDayPart(), entry.getMealRelation(), timeOf(entry), state,
                mark == null ? null : mark.getMarkedAt(), mark != null && day.equals(today()));
    }

    private LocalTime timeOf(PlanEntry entry) {
        return reminders.findByPlanEntryId(entry.getId())
                .map(PlanReminder::getReminderTime)
                .orElse(DEFAULT_TIMES.get(entry.getDayPart()));
    }

    private static boolean covers(PlanEntry entry, LocalDate day) {
        return !day.isBefore(entry.getStartDate()) && !day.isAfter(entry.getEndDate());
    }

    private LocalDate today() {
        return LocalDate.now(clock);
    }

    private ConsumptionPlan ownedPlan(Long planId, Jwt jwt) {
        return plans.findOwnedById(planId, jwt.getSubject())
                .orElseThrow(() -> error(HttpStatus.NOT_FOUND, "Vartojimo planas nerastas."));
    }

    private PlanEntry entry(Long planId, Long entryId) {
        return entries.findByIdAndPlanId(entryId, planId)
                .orElseThrow(() -> error(HttpStatus.NOT_FOUND, "Plano įrašas nerastas."));
    }

    private static ResponseStatusException error(HttpStatus status, String message) {
        return new ResponseStatusException(status, message);
    }
}